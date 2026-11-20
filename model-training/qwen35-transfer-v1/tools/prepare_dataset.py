"""Build a leakage-checked, RANKED-only transfer dataset from the frozen Nebula snapshot."""
import argparse
import csv
import hashlib
import json
import sys
from collections.abc import Mapping
from pathlib import Path

HERE = Path(__file__).resolve().parent
PACKAGE = HERE.parent

TASKS = {"EVIDENCE_SELECTION", "GROUNDED_ANSWER"}
SPLITS = ("train", "validation", "test")


def read_jsonl(path):
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def write_jsonl(path, rows):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as stream:
        for row in rows:
            stream.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")


def filter_ranked_records(rows):
    return [row for row in rows if row.get("task") in TASKS]


def build_ranked_splits(splits):
    owners = {}
    counts = {}
    for split in SPLITS:
        records = splits[split]
        grouped = {}
        for row in records:
            task, case_id = row.get("task"), row.get("caseId")
            if task not in TASKS or not case_id:
                raise ValueError(f"Invalid RANKED record in {split}")
            grouped.setdefault(case_id, set()).add(task)
            previous = owners.setdefault(case_id, split)
            if previous != split:
                raise ValueError(f"cross-split case overlap: {case_id}")
        if any(tasks != TASKS for tasks in grouped.values()):
            raise ValueError(f"Each case needs exactly one selection and one grounded answer in {split}")
        counts[split] = {"cases": len(grouped), "records": len(records)}
    if not all(counts[name]["cases"] for name in SPLITS):
        raise ValueError("Every train/validation/test split must be non-empty")
    return {"splits": counts, "cross_split_case_overlap": 0}


def encode_conversation(tokenizer, messages, max_seq_len):
    if len(messages) < 2 or messages[-1].get("role") != "assistant":
        raise ValueError("Each training conversation must end with an assistant answer")
    options = {"tokenize": True, "enable_thinking": False}
    prompt_ids = tokenizer.apply_chat_template(messages[:-1], add_generation_prompt=True, **options)
    input_ids = tokenizer.apply_chat_template(messages, add_generation_prompt=False, **options)
    if isinstance(prompt_ids, Mapping):
        prompt_ids = prompt_ids["input_ids"]
    if isinstance(input_ids, Mapping):
        input_ids = input_ids["input_ids"]
    if hasattr(prompt_ids, "tolist"):
        prompt_ids = prompt_ids.tolist()
    if hasattr(input_ids, "tolist"):
        input_ids = input_ids.tolist()
    if input_ids and isinstance(input_ids[0], list):
        input_ids = input_ids[0]
    if prompt_ids and isinstance(prompt_ids[0], list):
        prompt_ids = prompt_ids[0]
    if input_ids[:len(prompt_ids)] != prompt_ids:
        raise ValueError("Chat template assistant prefix is not a prefix of the complete conversation")
    if len(input_ids) > max_seq_len:
        raise ValueError(f"Conversation has {len(input_ids)} tokens, exceeding max_seq_len={max_seq_len}")
    labels = [-100] * len(prompt_ids) + input_ids[len(prompt_ids):]
    if all(label == -100 for label in labels):
        raise ValueError("Conversation contains no supervised assistant tokens")
    return {"input_ids": input_ids, "labels": labels}


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def write_review_csv(path, split_cases, splits=("train", "validation")):
    path.parent.mkdir(parents=True, exist_ok=True)
    fields = ["split", "caseId", "subject", "predicate", "taskType", "question", "referenceAnswer",
              "expectedEntitiesJson", "goldEvidenceJson", "candidatesJson", "labelSource", "reviewerName", "reviewedAt", "reviewStatus",
              "correctedReferenceAnswer", "correctedGoldEvidenceJson", "reviewNotes"]
    with path.open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        for split in splits:
            for case in split_cases[split]:
                writer.writerow({
                    "split": split,
                    "caseId": case["id"],
                    "subject": case["subject"],
                    "predicate": case["predicate"],
                    "taskType": case["taskType"],
                    "question": case["question"],
                    "referenceAnswer": case["referenceAnswer"],
                    "expectedEntitiesJson": json.dumps(case["expectedEntities"], ensure_ascii=False),
                    "goldEvidenceJson": json.dumps(case["goldEvidenceIds"], ensure_ascii=False),
                    "candidatesJson": json.dumps(case["candidates"], ensure_ascii=False, separators=(",", ":")),
                    "labelSource": case["labelSource"],
                    "reviewerName": "",
                    "reviewedAt": "",
                    "reviewStatus": "PENDING",
                    "correctedReferenceAnswer": "",
                    "correctedGoldEvidenceJson": "",
                    "reviewNotes": "",
                })


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=PACKAGE.parent / "data/nebula-20261116/dataset-v1")
    parser.add_argument("--output", type=Path, default=PACKAGE / "dataset")
    parser.add_argument("--refresh-generated", action="store_true",
                        help="Refresh this script's own unreviewed projection after verifying its existing artifact hashes")
    args = parser.parse_args()
    if args.output.exists() and any(args.output.iterdir()):
        if not args.refresh_generated or args.output.resolve() != (PACKAGE / "dataset").resolve():
            parser.error("refusing to overwrite non-empty output; --refresh-generated is limited to the package's original dataset directory")
        old_manifest_path = args.output / "manifest.json"
        old_manifest = json.loads(old_manifest_path.read_text(encoding="utf-8"))
        if old_manifest.get("teacherReviewed") is not False or old_manifest.get("sourceDatasetSha256") != sha256(args.source / "manifest.json"):
            parser.error("existing dataset is not the original unreviewed projection from this source snapshot")
        for relative, expected in old_manifest.get("filesSha256", {}).items():
            if relative.split("/", 1)[0] not in {"dataset", "evaluation", "provenance", "review"}:
                continue
            path = PACKAGE / relative
            if not path.is_file() or sha256(path) != expected:
                parser.error(f"refusing to replace changed artifact: {relative}")

    sys.path.insert(0, str(PACKAGE.parent))
    from verify_dataset import verify

    validation = verify(args.source)
    source_manifest_path = args.source / "manifest.json"
    source_manifest = json.loads(source_manifest_path.read_text(encoding="utf-8"))
    ranked, cases = {}, {}
    for split in SPLITS:
        ranked[split] = filter_ranked_records(read_jsonl(args.source / f"{split}.jsonl"))
        cases[split] = read_jsonl(args.source / f"{split}-cases.jsonl")
        case_ids = {case["id"] for case in cases[split]}
        if {row["caseId"] for row in ranked[split]} != case_ids:
            raise ValueError(f"RANKED tasks and source cases disagree in {split}")

    report = build_ranked_splits(ranked)
    version = source_manifest.get("javaStrategyVersion")
    if not version:
        raise ValueError("Source manifest has no Java strategy version")

    args.output.mkdir(parents=True, exist_ok=True)
    for split in SPLITS:
        write_jsonl(args.output / f"{split}.jsonl", ranked[split])
        write_jsonl(PACKAGE / "provenance" / f"{split}-cases.jsonl", cases[split])
    write_jsonl(PACKAGE / "provenance" / "train-facts.jsonl", read_jsonl(args.source / "train-facts.jsonl"))
    write_jsonl(PACKAGE / "provenance" / "validation-facts.jsonl", read_jsonl(args.source / "validation-facts.jsonl"))
    write_jsonl(PACKAGE / "evaluation" / "test-cases.jsonl", cases["test"])
    write_review_csv(PACKAGE / "review" / "train-validation-review.csv", cases)
    write_review_csv(PACKAGE / "review" / "test-review.csv", cases, ("test",))
    source_copy = PACKAGE / "provenance" / "source-manifest.json"
    source_copy.write_text(json.dumps(source_manifest, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    write_jsonl(PACKAGE / "evaluation" / "test-selection.jsonl",
                [row for row in ranked["test"] if row["task"] == "EVIDENCE_SELECTION"])
    write_jsonl(PACKAGE / "evaluation" / "test-grounded-answer.jsonl",
                [row for row in ranked["test"] if row["task"] == "GROUNDED_ANSWER"])

    artifact_dirs = ("dataset", "evaluation", "provenance", "review")
    files = sorted(path for name in artifact_dirs for path in (PACKAGE / name).rglob("*")
                   if path.is_file() and path.name != "manifest.json" and path.suffix != ".pyc"
                   and "__pycache__" not in path.parts and path.name != "base-model-verification.json")
    manifest = {
        "schemaVersion": 1,
        "baseModel": "Qwen/Qwen3.5-0.8B",
        "javaStrategyVersion": version,
        "sourceDatasetSha256": sha256(source_manifest_path),
        "sourceDatasetVerification": validation,
        "projection": "Only the exact EVIDENCE_SELECTION and GROUNDED_ANSWER messages used by Java RANKED are included. The grounded system context contains selected graph evidence, which is the runtime contract for the second stage, not the reference answer text.",
        "labelSource": "graph-template-needs-human-review",
        "teacherReviewed": False,
        "testDataLoaded": False,
        "testPolicy": "Test files exist only under evaluation/ and are not accepted by train.py.",
        "splits": report,
        "filesSha256": {str(path.relative_to(PACKAGE)).replace("\\", "/"): sha256(path) for path in files},
    }
    (PACKAGE / "dataset" / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"output": str(PACKAGE), "splits": report, "reviewRows": sum(len(cases[s]) for s in ("train", "validation")),
                      "teacherReviewed": False, "javaStrategyVersion": version}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
