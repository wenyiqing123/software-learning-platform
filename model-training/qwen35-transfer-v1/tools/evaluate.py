"""Evaluate the two-stage RANKED pipeline and Java-parity answer metrics on held-out test cases."""
import argparse
import json
import re
from pathlib import Path

import torch
from peft import PeftModel
from transformers import AutoModelForMultimodalLM, AutoTokenizer

from fetch_model import MODEL_DIR
from metrics import evaluate_answer, mean_metrics, normalize_for_java, requires_multiple_evidence

PACKAGE = Path(__file__).resolve().parents[1]
EVALUATION = PACKAGE / "evaluation"
NUMBER = re.compile(r"[0-9]+(?:\.[0-9]+)?")


def parse_json(raw):
    if not raw or len(raw) > 65536:
        return None
    value = raw.strip()
    if value.startswith("```json") and value.endswith("```"):
        value = value[7:-3].strip()
    try:
        result = json.loads(value)
        return result if isinstance(result, dict) else None
    except (ValueError, TypeError):
        return None


def validate_generated_answer(raw, evidence, selected_ids):
    result = parse_json(raw)
    if not result or not isinstance(result.get("answer"), str) or not result["answer"].strip():
        return None, "invalid-json-or-empty-answer"
    used = result.get("usedIds")
    if not isinstance(used, list) or set(used) != set(selected_ids) or len(used) != len(set(used)):
        return None, "used-evidence-ids-mismatch"
    answer = result["answer"].strip()
    if len(evidence) == 1 and normalize_for_java(answer) == normalize_for_java(evidence[0]["object"]):
        answer = evidence[0]["subject"] + evidence[0]["predicate"] + "：" + answer
    normalized = normalize_for_java(answer)
    for fact in evidence:
        if any(not normalize_for_java(fact[key]) or normalize_for_java(fact[key]) not in normalized
               for key in ("subject", "predicate", "object")):
            return None, "answer-does-not-cover-selected-triples"
    fact_text = " ".join(" ".join(fact[key] for key in ("subject", "predicate", "object")) for fact in evidence)
    if ("例如" in answer or "比如" in answer) and "例如" not in fact_text and "比如" not in fact_text:
        return None, "answer-adds-unsupported-example"
    source_numbers = set(NUMBER.findall(fact_text))
    if any(number not in source_numbers for number in NUMBER.findall(answer)):
        return None, "answer-adds-unsupported-number"
    return {"answer": answer, "usedIds": used}, None


def generate(model, tokenizer, messages, device, max_new_tokens):
    encoded = tokenizer.apply_chat_template(messages, tokenize=True, add_generation_prompt=True,
                                            enable_thinking=False, return_tensors="pt",
                                            return_dict=True)
    encoded = {key: value.to(device) for key, value in encoded.items() if isinstance(value, torch.Tensor)}
    prompt_length = encoded["input_ids"].shape[1]
    with torch.inference_mode():
        with torch.autocast(device_type="cuda", dtype=next(model.parameters()).dtype, enabled=device.type == "cuda"):
            sequences = model.generate(**encoded, max_new_tokens=max_new_tokens, do_sample=False,
                                       use_cache=True, pad_token_id=tokenizer.pad_token_id,
                                       eos_token_id=tokenizer.eos_token_id)
    return tokenizer.decode(sequences[0, prompt_length:], skip_special_tokens=True).strip()


def load_model(adapter):
    if not (MODEL_DIR / "config.json").is_file():
        raise SystemExit("Base model is missing. Run tools/fetch_model.py first.")
    tokenizer = AutoTokenizer.from_pretrained(MODEL_DIR, use_fast=True)
    if tokenizer.pad_token_id is None:
        tokenizer.pad_token = tokenizer.eos_token
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    if device.type == "cuda":
        capability = torch.cuda.get_device_capability(0)
        native_bf16 = capability >= (8, 0) and torch.cuda.is_bf16_supported()
        dtype = torch.bfloat16 if native_bf16 else torch.float16
    else:
        dtype = torch.float32
    model = AutoModelForMultimodalLM.from_pretrained(
        MODEL_DIR, dtype=dtype, attn_implementation="sdpa", low_cpu_mem_usage=True)
    if adapter:
        model = PeftModel.from_pretrained(model, adapter)
    model.to(device).eval()
    return model, tokenizer, device


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adapter", type=Path, help="Omit for the unmodified Safetensors baseline")
    parser.add_argument("--evaluation-dir", type=Path, default=EVALUATION,
                        help="Use evaluation-reviewed/ only after an independent blinded review; default is the original frozen test set")
    parser.add_argument("--output", type=Path, help="Fresh JSONL output path")
    parser.add_argument("--max-cases", type=int, help="Small diagnostic run; full evaluation is the default")
    parser.add_argument("--max-new-tokens", type=int, default=256)
    args = parser.parse_args()
    if args.adapter and not args.adapter.is_dir():
        raise SystemExit(f"Adapter directory not found: {args.adapter}")
    output = args.output or PACKAGE / ("evaluation-baseline.jsonl" if not args.adapter else "evaluation-finetuned.jsonl")
    if output.exists():
        raise SystemExit(f"Refusing to overwrite evaluation output: {output}")
    cases_path = args.evaluation_dir / "test-cases.jsonl"
    cases = [json.loads(line) for line in cases_path.read_text(encoding="utf-8").splitlines() if line]
    if args.evaluation_dir.resolve() != EVALUATION.resolve():
        review_manifest = json.loads((args.evaluation_dir / "manifest.json").read_text(encoding="utf-8"))
        import hashlib
        if review_manifest.get("teacherReviewed") is not True or review_manifest.get("split") != "test" or review_manifest.get("testDataLoadedForTraining") is not False:
            raise SystemExit("Reviewed test evaluation manifest is missing or invalid.")
        if hashlib.sha256(cases_path.read_bytes()).hexdigest() != review_manifest.get("testCasesSha256"):
            raise SystemExit("Reviewed test case checksum mismatch.")
    selectors = {row["caseId"]: row for row in
                 (json.loads(line) for line in (EVALUATION / "test-selection.jsonl").read_text(encoding="utf-8").splitlines() if line)}
    answers = {row["caseId"]: row for row in
               (json.loads(line) for line in (EVALUATION / "test-grounded-answer.jsonl").read_text(encoding="utf-8").splitlines() if line)}
    cases = cases[:args.max_cases] if args.max_cases else cases
    if not cases:
        raise SystemExit("Held-out test cases are missing")
    model, tokenizer, device = load_model(args.adapter)
    rows, selection_correct, oracle_valid, e2e_valid = [], 0, [], []
    for index, case in enumerate(cases, 1):
        case_id = case["id"]
        selector = selectors[case_id]
        answer = answers[case_id]
        select_input = selector["messages"][:-1]
        raw_selection = generate(model, tokenizer, select_input, device, 128)
        selection = parse_json(raw_selection)
        predicted_ids = selection.get("selectedIds", []) if isinstance(selection, dict) else []
        predicted_type = selection.get("taskType") if isinstance(selection, dict) else None
        predicted_status = selection.get("status") if isinstance(selection, dict) else None
        evidence_by_id = {item["id"]: item for item in case["candidates"]}
        prediction_evidence = [evidence_by_id[item] for item in predicted_ids
                               if isinstance(item, str) and item in evidence_by_id]
        selection_is_valid = (predicted_status == "SUFFICIENT" and predicted_type in ("SINGLE_FACT", "MULTI_FACT", "COMPARISON")
                              and len(predicted_ids) == len(prediction_evidence) == len(set(predicted_ids))
                              and bool(prediction_evidence))
        if selection_is_valid and requires_multiple_evidence(case["question"], predicted_type):
            selection_is_valid = predicted_type != "SINGLE_FACT" and len(prediction_evidence) >= 2
        elif selection_is_valid and predicted_type == "SINGLE_FACT":
            selection_is_valid = len(prediction_evidence) == 1
        target_ids = case["goldEvidenceIds"]
        selection_exact = selection_is_valid and predicted_type == case["taskType"] and set(predicted_ids) == set(target_ids)
        selection_correct += bool(selection_exact)

        base_instructions, _ = answer["messages"][0]["content"].rsplit("\n", 1)
        user_message = answer["messages"][1]
        gold_evidence = [evidence_by_id[item] for item in target_ids]
        oracle_context = json.dumps({"question": case["question"], "taskType": case["taskType"],
                                     "selectedEvidence": gold_evidence}, ensure_ascii=False, separators=(",", ":"))
        raw_oracle = generate(model, tokenizer, [{"role": "system", "content": base_instructions + "\n" + oracle_context},
                                                user_message], device, args.max_new_tokens)
        oracle_result, oracle_error = validate_generated_answer(raw_oracle, gold_evidence, target_ids)
        oracle_score = evaluate_answer(oracle_result["answer"], case["referenceAnswer"], case["expectedEntities"]) if oracle_result else None
        if oracle_score is not None:
            oracle_valid.append(oracle_score)

        raw_e2e, e2e_result, e2e_error, e2e_score = "", None, "selection-invalid", None
        if selection_is_valid:
            runtime_context = json.dumps({"question": case["question"], "taskType": predicted_type,
                                          "selectedEvidence": prediction_evidence}, ensure_ascii=False, separators=(",", ":"))
            runtime_system = base_instructions + "\n" + runtime_context
            raw_e2e = generate(model, tokenizer, [{"role": "system", "content": runtime_system}, user_message], device, args.max_new_tokens)
            e2e_result, e2e_error = validate_generated_answer(raw_e2e, prediction_evidence, predicted_ids)
            if e2e_result:
                e2e_score = evaluate_answer(e2e_result["answer"], case["referenceAnswer"], case["expectedEntities"])
                e2e_valid.append(e2e_score)
        rows.append({"caseId": case_id, "question": case["question"], "referenceAnswer": case["referenceAnswer"],
                     "selection": selection, "selectionExactMatch": bool(selection_exact),
                     "oracleAnswer": oracle_result, "oracleValidationError": oracle_error, "oracleMetrics": oracle_score,
                     "endToEndAnswer": e2e_result, "endToEndValidationError": e2e_error, "endToEndMetrics": e2e_score})
        print(f"[{index}/{len(cases)}] case={case_id} selectionExact={bool(selection_exact)} "
              f"oracleValid={oracle_result is not None} endToEndValid={e2e_result is not None}", flush=True)

    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8", newline="\n") as stream:
        for row in rows:
            stream.write(json.dumps(row, ensure_ascii=False) + "\n")
    report = {"model": str(args.adapter or MODEL_DIR), "split": "test", "cases": len(rows),
              "selectionExactMatchRate": selection_correct / len(rows),
              "oracleEvidenceGenerationValidRate": len(oracle_valid) / len(rows),
              "oracleValidAnswerMetrics": mean_metrics(oracle_valid),
              "endToEndEvidenceValidRate": len(e2e_valid) / len(rows),
              "endToEndValidAnswerMetrics": mean_metrics(e2e_valid), "predictions": str(output)}
    report_path = output.with_suffix(".summary.json")
    report_path.write_text(json.dumps(report, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
