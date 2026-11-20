"""Verify the transfer package's dataset and pinned base-model files."""
import hashlib
import json
from pathlib import Path

PACKAGE = Path(__file__).resolve().parents[1]
PINNED_REPO = "Qwen/Qwen3.5-0.8B"
PINNED_REVISION = "2fc06364715b967f1860aea9cf38778875588b17"
PINNED_MODEL_SIZE = 1746942600
PINNED_MODEL_SHA256 = "04b1c301231dd422b8860db31311ab2721511346a32cb1e079c4c4e5f1fe4696"
PINNED_TOKENIZER_SHA256 = "5f9e4d4901a92b997e463c1f46055088b6cca5ca61a6522d1b9f64c4bb81cb42"


def digest(path):
    hasher = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            hasher.update(chunk)
    return hasher.hexdigest()


def verify():
    manifest_path = PACKAGE / "dataset/manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    checked = 0
    for name, expected in manifest["filesSha256"].items():
        path = PACKAGE / name
        if not path.is_file() or digest(path) != expected:
            raise ValueError(f"Dataset checksum mismatch: {name}")
        checked += 1
    model_path = PACKAGE / "provenance/base-model-verification.json"
    if not model_path.is_file():
        raise ValueError("Base model verification manifest is missing")
    model = json.loads(model_path.read_text(encoding="utf-8"))
    if model.get("repoId") != PINNED_REPO or model.get("revision") != PINNED_REVISION:
        raise ValueError("Base-model repository/revision does not match the pinned official model")
    if model["files"].get("model.safetensors-00001-of-00001.safetensors") != {
            "bytes": PINNED_MODEL_SIZE, "sha256": PINNED_MODEL_SHA256}:
        raise ValueError("Base-model Safetensors entry does not match the pinned SHA-256")
    if model["files"].get("tokenizer.json", {}).get("sha256") != PINNED_TOKENIZER_SHA256:
        raise ValueError("Base-model tokenizer does not match the pinned SHA-256")
    for name, expected in model["files"].items():
        path = PACKAGE / "base-model" / name
        if not path.is_file() or path.stat().st_size != expected["bytes"] or digest(path) != expected["sha256"]:
            raise ValueError(f"Base-model checksum mismatch: {name}")
        checked += 1
    split_ids = {}
    for split in ("train", "validation"):
        rows = [json.loads(line) for line in (PACKAGE / "dataset" / f"{split}.jsonl").read_text(encoding="utf-8").splitlines() if line]
        tasks = {}
        for row in rows:
            if row.get("task") not in {"EVIDENCE_SELECTION", "GROUNDED_ANSWER"}:
                raise ValueError(f"Unexpected task in {split}")
            tasks.setdefault(row["caseId"], set()).add(row["task"])
        if not tasks or any(value != {"EVIDENCE_SELECTION", "GROUNDED_ANSWER"} for value in tasks.values()):
            raise ValueError(f"Missing or unpaired RANKED tasks in {split}")
        split_ids[split] = set(tasks)
    test_rows = [json.loads(line) for line in (PACKAGE / "evaluation/test-cases.jsonl").read_text(encoding="utf-8").splitlines() if line]
    test_ids = {row["id"] for row in test_rows}
    if test_ids & split_ids["train"] or test_ids & split_ids["validation"]:
        raise ValueError("Held-out test case leaked into train/validation")
    for task in ("test-selection", "test-grounded-answer"):
        task_rows = [json.loads(line) for line in (PACKAGE / f"evaluation/{task}.jsonl").read_text(encoding="utf-8").splitlines() if line]
        expected_task = "EVIDENCE_SELECTION" if task == "test-selection" else "GROUNDED_ANSWER"
        if {row["caseId"] for row in task_rows} != test_ids or any(row["task"] != expected_task for row in task_rows):
            raise ValueError(f"Held-out task file is incomplete or contains wrong task: {task}")
    if manifest.get("teacherReviewed") is not False or manifest.get("testDataLoaded") is not False:
        raise ValueError("Original snapshot must remain clearly marked as unreviewed")
    if manifest["sourceDatasetVerification"].get("long_answer_substring_overlap_ge8") != 0:
        raise ValueError("Source dataset leakage audit did not pass")
    token_report_path = PACKAGE / "provenance/tokenization-smoke.json"
    token_report = json.loads(token_report_path.read_text(encoding="utf-8"))
    if token_report.get("revision") != PINNED_REVISION or any(
            item.get("overLimit") != 0 for item in token_report.get("splits", {}).values()):
        raise ValueError("Actual tokenizer smoke test is missing or reports overlength samples")
    if any(token_report["splits"][split].get("maxTokens", 0) > 1024 for split in ("train", "validation")):
        raise ValueError("16 GB profile length does not cover every packaged training/validation example")
    checked += 1
    return {"verifiedFiles": checked, "teacherReviewed": False, "pinnedRevision": PINNED_REVISION,
            "testFiles": ["evaluation/test-cases.jsonl", "evaluation/test-selection.jsonl",
                          "evaluation/test-grounded-answer.jsonl"]}


if __name__ == "__main__":
    print(json.dumps(verify(), ensure_ascii=False, indent=2))
