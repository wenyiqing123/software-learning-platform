"""Assistant-only Qwen3.5 LoRA SFT. Test data is not an input to this program."""
import argparse
import gc
import hashlib
import json
import math
import random
import shutil
from pathlib import Path

import torch
from peft import LoraConfig, TaskType, get_peft_model
from transformers import AutoModelForMultimodalLM, AutoTokenizer

from fetch_model import MODEL_DIR, REVISION
from prepare_dataset import encode_conversation
from training_profile import (DEFAULT_BATCH_SIZE, DEFAULT_GRADIENT_ACCUMULATION,
                              DEFAULT_EFFECTIVE_BATCH_SIZE,
                              DEFAULT_MAX_SEQ_LEN, MIN_FREE_VRAM_MIB, MIN_PROBE_RESERVE_MIB,
                              MIN_TOTAL_VRAM_MIB,
                              accumulation_for_batch, batch_candidates, supports_native_bf16,
                              probe_memory_reserve, require_complete_split, validate_vram)

PACKAGE = Path(__file__).resolve().parents[1]
DATASET = PACKAGE / "dataset"
TARGET_MODULES = ["q_proj", "k_proj", "v_proj", "o_proj", "in_proj_qkv", "in_proj_z",
                  "in_proj_b", "in_proj_a", "out_proj", "gate_proj", "up_proj", "down_proj"]


def read_jsonl(path):
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def load_rows(path, tokenizer, max_seq_len):
    rows = read_jsonl(path)
    if not rows:
        raise ValueError(f"Empty SFT split: {path}")
    encoded, too_long = [], []
    for row in rows:
        try:
            item = encode_conversation(tokenizer, row["messages"], max_seq_len)
            encoded.append({**item, "task": row["task"], "caseId": row["caseId"]})
        except ValueError as error:
            if "exceeding max_seq_len" not in str(error):
                raise
            too_long.append(row["caseId"])
    if not encoded:
        raise ValueError(f"No records fit max_seq_len={max_seq_len}: {path}")
    require_complete_split(len(rows), len(encoded), too_long)
    return encoded, {"records": len(rows), "used": len(encoded), "overLimit": len(too_long), "overLimitCaseIds": too_long}


def collate(batch, pad_token_id):
    width = max(len(row["input_ids"]) for row in batch)
    input_ids = [row["input_ids"] + [pad_token_id] * (width - len(row["input_ids"])) for row in batch]
    labels = [row["labels"] + [-100] * (width - len(row["labels"])) for row in batch]
    mask = [[1] * len(row["input_ids"]) + [0] * (width - len(row["input_ids"])) for row in batch]
    return {"input_ids": torch.tensor(input_ids, dtype=torch.long),
            "labels": torch.tensor(labels, dtype=torch.long),
            "attention_mask": torch.tensor(mask, dtype=torch.long)}


def batches(rows, size, shuffle=False):
    order = list(range(len(rows)))
    if shuffle:
        random.shuffle(order)
    for start in range(0, len(order), size):
        yield [rows[index] for index in order[start:start + size]]


def evaluate_loss(model, rows, device, dtype, pad_id, batch_size):
    model.eval()
    weighted, count = 0.0, 0
    with torch.no_grad():
        for batch in batches(rows, batch_size):
            inputs = {key: value.to(device) for key, value in collate(batch, pad_id).items()}
            with torch.autocast(device_type="cuda", dtype=dtype, enabled=device.type == "cuda"):
                loss = model(**inputs, use_cache=False).loss
            weighted += float(loss.detach().float()) * len(batch)
            count += len(batch)
    model.train()
    return weighted / count


def probe_micro_batch(model, rows, pad_token_id, device, dtype, requested_batch_size):
    candidates = batch_candidates(requested_batch_size, len(rows))
    longest_rows = sorted(rows, key=lambda row: len(row["input_ids"]), reverse=True)
    for size in candidates:
        inputs = loss = None
        model.zero_grad(set_to_none=True)
        torch.cuda.reset_peak_memory_stats(device)
        free_before, _ = torch.cuda.mem_get_info(device)
        baseline_mib = round(torch.cuda.memory_allocated(device) / (1024 * 1024), 1)
        oom = False
        try:
            inputs = {key: value.to(device) for key, value in
                      collate(longest_rows[:size], pad_token_id).items()}
            with torch.autocast(device_type="cuda", dtype=dtype):
                loss = model(**inputs, use_cache=False).loss
            loss.backward()
            torch.cuda.synchronize(device)
            peak_mib = round(torch.cuda.max_memory_allocated(device) / (1024 * 1024), 1)
            fits_reserve, projected_free_mib = probe_memory_reserve(
                free_before / (1024 * 1024), baseline_mib, peak_mib)
            model.zero_grad(set_to_none=True)
            del inputs, loss
            if not fits_reserve:
                print(json.dumps({"memoryProbe": "reserve-rejected", "batchSize": size,
                                  "projectedFreeMiB": projected_free_mib,
                                  "requiredReserveMiB": MIN_PROBE_RESERVE_MIB}), flush=True)
                torch.cuda.empty_cache()
                if requested_batch_size:
                    raise SystemExit(
                        f"--batch-size {size} leaves less than {MIN_PROBE_RESERVE_MIB} MiB safety headroom. "
                        "Retry without --batch-size for automatic fallback."
                    )
                continue
            print(json.dumps({"memoryProbe": "passed", "batchSize": size,
                              "peakAllocatedMiB": peak_mib,
                              "projectedFreeMiB": projected_free_mib}), flush=True)
            return size, peak_mib, projected_free_mib
        except torch.cuda.OutOfMemoryError:
            model.zero_grad(set_to_none=True)
            del inputs, loss
            print(json.dumps({"memoryProbe": "oom", "batchSize": size}), flush=True)
            if requested_batch_size:
                raise SystemExit(
                    f"--batch-size {size} does not fit this GPU. Retry without --batch-size for automatic fallback."
                )
            oom = True
        finally:
            if oom:
                gc.collect()
                torch.cuda.empty_cache()
    raise SystemExit("No micro-batch fits. Retry with --gradient-checkpointing or a shorter --max-seq-len.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset", type=Path, default=DATASET,
                        help="Train/validation directory; defaults to the unreviewed snapshot and requires an explicit acknowledgement")
    parser.add_argument("--output", type=Path, default=PACKAGE / "output")
    parser.add_argument("--epochs", type=int, default=3)
    parser.add_argument("--batch-size", type=int, default=DEFAULT_BATCH_SIZE,
                        help="Micro-batch size; 0 probes 8, 4, 2, then 1 and selects the largest that fits")
    parser.add_argument("--gradient-accumulation", type=int, default=DEFAULT_GRADIENT_ACCUMULATION,
                        help="Defaults to an effective batch near 8 for the selected micro-batch")
    parser.add_argument("--max-seq-len", type=int, default=DEFAULT_MAX_SEQ_LEN)
    parser.add_argument("--gradient-checkpointing", action="store_true",
                        help="Reduce activation memory at a speed cost if automatic batch probing cannot fit")
    parser.add_argument("--learning-rate", type=float, default=2e-4)
    parser.add_argument("--seed", type=int, default=20261116)
    parser.add_argument("--allow-unreviewed-labels", action="store_true",
                        help="Explicitly acknowledge graph-template labels were not teacher reviewed")
    args = parser.parse_args()
    if (args.epochs < 1 or args.batch_size < 0
            or (args.gradient_accumulation is not None and args.gradient_accumulation < 1)
            or args.max_seq_len < 16 or args.learning_rate <= 0):
        raise SystemExit("epochs, batch size, accumulation, sequence length and learning rate must all be positive.")
    data_dir = args.dataset.resolve()
    manifest_path = data_dir / "manifest.json"
    for name in ("train.jsonl", "validation.jsonl", "manifest.json"):
        if not (data_dir / name).is_file():
            raise SystemExit(f"Missing packaged {name}; run tools/prepare_dataset.py first.")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest.get("testDataLoaded") is not False:
        raise SystemExit("Training manifest does not certify that test data was excluded.")
    if not args.allow_unreviewed_labels and not manifest.get("teacherReviewed"):
        raise SystemExit("Training blocked: review graph labels first, or explicitly pass --allow-unreviewed-labels.")
    if manifest.get("javaStrategyVersion") != "ranked-grounded-v3":
        raise SystemExit("Training data Java strategy version does not match ranked-grounded-v3.")
    if args.output.exists():
        raise SystemExit(f"Refusing to overwrite training output: {args.output}")
    for name in ("train.jsonl", "validation.jsonl"):
        path = data_dir / name
        expected = (manifest.get("filesSha256", {}).get(name)
                    or manifest.get("filesSha256", {}).get(f"dataset/{name}"))
        actual = hashlib.sha256(path.read_bytes()).hexdigest()
        if expected != actual:
            raise SystemExit(f"Dataset checksum mismatch: {name}")
        rows = read_jsonl(path)
        if any(row.get("task") not in {"EVIDENCE_SELECTION", "GROUNDED_ANSWER"} for row in rows):
            raise SystemExit(f"Non-RANKED task found in {name}")
    if not MODEL_DIR.is_dir() or not (MODEL_DIR / "model.safetensors-00001-of-00001.safetensors").is_file():
        raise SystemExit("Missing verified base-model weights. Run tools/fetch_model.py first.")
    if not torch.cuda.is_available():
        raise SystemExit("CUDA GPU not detected. Run fine-tuning on the external NVIDIA machine.")
    free_bytes, total_bytes = torch.cuda.mem_get_info(0)
    try:
        vram = validate_vram(round(total_bytes / (1024 * 1024)), round(free_bytes / (1024 * 1024)))
    except RuntimeError as error:
        raise SystemExit(str(error)) from error

    random.seed(args.seed)
    torch.manual_seed(args.seed)
    torch.cuda.manual_seed_all(args.seed)
    device = torch.device("cuda")
    capability = torch.cuda.get_device_capability(0)
    native_bf16 = supports_native_bf16(capability, torch.cuda.is_bf16_supported())
    dtype = torch.bfloat16 if native_bf16 else torch.float16
    tokenizer = AutoTokenizer.from_pretrained(MODEL_DIR, use_fast=True)
    if tokenizer.pad_token_id is None:
        tokenizer.pad_token = tokenizer.eos_token
    train_rows, train_filter = load_rows(data_dir / "train.jsonl", tokenizer, args.max_seq_len)
    validation_rows, validation_filter = load_rows(data_dir / "validation.jsonl", tokenizer, args.max_seq_len)
    if args.batch_size > len(train_rows):
        raise SystemExit(f"--batch-size {args.batch_size} exceeds the {len(train_rows)} training rows.")
    model = AutoModelForMultimodalLM.from_pretrained(
        MODEL_DIR, dtype=dtype, attn_implementation="sdpa", low_cpu_mem_usage=True)
    model.config.use_cache = False
    if args.gradient_checkpointing:
        model.gradient_checkpointing_enable(gradient_checkpointing_kwargs={"use_reentrant": False})
        model.enable_input_require_grads()
    lora = LoraConfig(task_type=TaskType.CAUSAL_LM, r=8, lora_alpha=16, lora_dropout=0.05,
                      target_modules=TARGET_MODULES, bias="none")
    model = get_peft_model(model, lora)
    model.to(device)
    model.train()
    trainable = sum(parameter.numel() for parameter in model.parameters() if parameter.requires_grad)
    total = sum(parameter.numel() for parameter in model.parameters())
    optimizer = torch.optim.AdamW((parameter for parameter in model.parameters() if parameter.requires_grad),
                                  lr=args.learning_rate, weight_decay=0.01, fused=True)
    scaler = torch.amp.GradScaler("cuda", enabled=dtype == torch.float16)
    selected_batch_size, probe_peak_mib, probe_projected_free_mib = probe_micro_batch(
        model, train_rows, tokenizer.pad_token_id, device, dtype, args.batch_size)
    args.batch_size = selected_batch_size
    if args.gradient_accumulation is None:
        args.gradient_accumulation = accumulation_for_batch(args.batch_size)
    effective_batch_size = args.batch_size * args.gradient_accumulation
    micro_steps_per_epoch = math.ceil(len(train_rows) / args.batch_size)
    steps_per_epoch = math.ceil(micro_steps_per_epoch / args.gradient_accumulation)
    total_steps = args.epochs * steps_per_epoch
    warmup_steps = max(1, int(total_steps * 0.03))
    args.output.mkdir(parents=True)
    report = {"profile": "16gb-speed-first-lora", "minimumTotalVramMiB": MIN_TOTAL_VRAM_MIB,
              "minimumFreeVramMiB": MIN_FREE_VRAM_MIB, "gpuMemoryAtStart": vram,
              "minimumProbeReserveMiB": MIN_PROBE_RESERVE_MIB,
              "batchSizeProbePeakAllocatedMiB": probe_peak_mib,
              "batchSizeProbeProjectedFreeMiB": probe_projected_free_mib,
              "microBatchSize": args.batch_size, "gradientAccumulation": args.gradient_accumulation,
              "effectiveBatchSize": effective_batch_size,
              "gradientCheckpointing": args.gradient_checkpointing,
              "computeCapability": list(capability), "nativeBf16": native_bf16,
              "model": "Qwen/Qwen3.5-0.8B", "revision": REVISION, "strategy": "ranked-grounded-v3",
              "taskCounts": {name: sum(row["task"] == name for row in train_rows)
                             for name in ("EVIDENCE_SELECTION", "GROUNDED_ANSWER")},
              "maxSequenceLength": args.max_seq_len, "trainingRows": train_filter,
              "validationRows": validation_filter, "teacherReviewed": bool(manifest.get("teacherReviewed")),
              "unreviewedTemplateLabelAcknowledged": bool(args.allow_unreviewed_labels), "dtype": str(dtype),
              "gpu": torch.cuda.get_device_name(0), "trainableParameters": trainable, "totalParameters": total,
              "epochs": [], "bestAdapter": "best-adapter"}
    best, stale, step = float("inf"), 0, 0
    torch.cuda.reset_peak_memory_stats()
    for epoch in range(args.epochs):
        model.train()
        optimizer.zero_grad(set_to_none=True)
        losses, micro = [], 0
        final_micro = micro_steps_per_epoch
        for micro, batch in enumerate(batches(train_rows, args.batch_size, shuffle=True), start=1):
            inputs = {key: value.to(device) for key, value in collate(batch, tokenizer.pad_token_id).items()}
            with torch.autocast(device_type="cuda", dtype=dtype):
                loss = model(**inputs, use_cache=False).loss
            window_start = ((micro - 1) // args.gradient_accumulation) * args.gradient_accumulation
            window_size = min(args.gradient_accumulation, final_micro - window_start)
            scaler.scale(loss / window_size).backward()
            losses.append(float(loss.detach().float()))
            if micro % args.gradient_accumulation == 0 or micro == final_micro:
                scaler.unscale_(optimizer)
                torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
                warmup = min(1.0, (step + 1) / warmup_steps)
                decay = max(0.0, 1.0 - step / max(total_steps, 1))
                for group in optimizer.param_groups:
                    group["lr"] = args.learning_rate * warmup * decay
                scaler.step(optimizer)
                scaler.update()
                optimizer.zero_grad(set_to_none=True)
                step += 1
        val_loss = evaluate_loss(model, validation_rows, device, dtype, tokenizer.pad_token_id, args.batch_size)
        epoch_report = {"epoch": epoch + 1, "trainLossMeanByBatch": sum(losses) / len(losses),
                        "validationAssistantLoss": val_loss}
        report["epochs"].append(epoch_report)
        print(json.dumps(epoch_report), flush=True)
        if val_loss < best:
            best, stale = val_loss, 0
            target = args.output / "best-adapter"
            if target.exists():
                shutil.rmtree(target)
            model.save_pretrained(target, safe_serialization=True)
            tokenizer.save_pretrained(target)
            adapter_config = target / "adapter_config.json"
            if adapter_config.is_file():
                config = json.loads(adapter_config.read_text(encoding="utf-8"))
                config["base_model_name_or_path"] = "Qwen/Qwen3.5-0.8B"
                config["revision"] = REVISION
                adapter_config.write_text(json.dumps(config, sort_keys=True, indent=2) + "\n", encoding="utf-8")
        else:
            stale += 1
            if stale >= 2:
                break
    report["bestValidationAssistantLoss"] = best
    report["maxCudaMemoryMiB"] = round(torch.cuda.max_memory_allocated() / (1024 * 1024), 1)
    (args.output / "training-report.json").write_text(
        json.dumps(report, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"output": str(args.output), "bestValidationAssistantLoss": best,
                      "maxCudaMemoryMiB": report["maxCudaMemoryMiB"], "adapter": "best-adapter"}, ensure_ascii=False))


if __name__ == "__main__":
    main()
