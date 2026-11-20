"""Apply complete, named human review to train/validation and keep all labels in sync."""
import argparse
import csv
from datetime import date
import hashlib
import json
import unicodedata
from pathlib import Path

from prepare_dataset import PACKAGE, read_jsonl, write_jsonl


def apply_review_to_case(original, review):
    case_id = original["id"]
    if review.get("reviewStatus", "").strip().upper() != "APPROVED":
        raise ValueError(f"Case is not approved: {case_id}")
    if not review.get("reviewerName", "").strip() or not review.get("reviewedAt", "").strip():
        raise ValueError(f"Reviewer name and date are required: {case_id}")
    try:
        date.fromisoformat(review["reviewedAt"].strip())
    except ValueError as error:
        raise ValueError(f"Reviewer date must use YYYY-MM-DD: {case_id}") from error
    case = json.loads(json.dumps(original, ensure_ascii=False))
    evidence = {item["id"]: item for item in case["candidates"]}
    encoded_ids = review.get("correctedGoldEvidenceJson", "").strip()
    selected = json.loads(encoded_ids) if encoded_ids else case["goldEvidenceIds"]
    if (not isinstance(selected, list) or not selected or not all(isinstance(item, str) for item in selected)
            or len(selected) != len(set(selected)) or not set(selected) <= set(evidence)):
        raise ValueError(f"Invalid reviewed evidence IDs: {case_id}")
    answer = review.get("correctedReferenceAnswer", "").strip() or case["referenceAnswer"]
    normalize = lambda text: "".join(char for char in text.lower()
                                       if unicodedata.category(char).startswith("L")
                                       or unicodedata.category(char) == "Nd")
    if not answer or any(any(normalize(item[key]) not in normalize(answer)
                             for key in ("subject", "predicate", "object"))
                         for item in (evidence[key] for key in selected)):
        raise ValueError(f"Reviewed answer must cover each selected subject, relation, and object: {case_id}")
    case["goldEvidenceIds"] = selected
    case["expectedEntities"] = [evidence[key]["object"] for key in selected]
    case["referenceAnswer"] = answer
    case["taskType"] = "SINGLE_FACT" if len(selected) == 1 else "MULTI_FACT"
    case["labelSource"] = "human-reviewed"
    case["reviewerName"] = review["reviewerName"].strip()
    case["reviewedAt"] = review["reviewedAt"].strip()
    case["reviewNotes"] = review.get("reviewNotes", "").strip()
    return case


def apply_review_to_splits(cases, reviews, rows):
    if set(cases) != set(reviews):
        missing = sorted(set(cases) - set(reviews))
        extra = sorted(set(reviews) - set(cases))
        raise ValueError(f"Review rows must cover exactly the split cases; missing={missing[:3]}, extra={extra[:3]}")
    reviewed_cases = {}
    for case_id, original in cases.items():
        review = reviews[case_id]
        case = apply_review_to_case(original, review)
        reviewed_cases[case_id] = case

    output_rows = []
    seen = set()
    for original in rows:
        case_id, task = original["caseId"], original["task"]
        if case_id not in reviewed_cases or task not in {"EVIDENCE_SELECTION", "GROUNDED_ANSWER"}:
            raise ValueError(f"Unexpected training row: {case_id}/{task}")
        key = (case_id, task)
        if key in seen:
            raise ValueError(f"Duplicate training task: {key}")
        seen.add(key)
        case = reviewed_cases[case_id]
        row = json.loads(json.dumps(original, ensure_ascii=False))
        if task == "EVIDENCE_SELECTION":
            label = json.loads(row["messages"][-1]["content"])
            label.update({"status": "SUFFICIENT", "taskType": case["taskType"], "selectedIds": case["goldEvidenceIds"]})
            row["messages"][-1]["content"] = json.dumps(label, ensure_ascii=False, separators=(",", ":"))
        else:
            system = row["messages"][0]["content"]
            prefix, context = system.rsplit("\n", 1)
            context = json.loads(context)
            evidence = {item["id"]: item for item in case["candidates"]}
            context.update({"taskType": case["taskType"],
                            "selectedEvidence": [evidence[key] for key in case["goldEvidenceIds"]]})
            row["messages"][0]["content"] = prefix + "\n" + json.dumps(context, ensure_ascii=False, separators=(",", ":"))
            row["messages"][-1]["content"] = json.dumps(
                {"answer": case["referenceAnswer"], "usedIds": case["goldEvidenceIds"]},
                ensure_ascii=False, separators=(",", ":"))
        row["labelSource"] = "human-reviewed"
        output_rows.append(row)
    expected = {(case_id, task) for case_id in cases for task in ("EVIDENCE_SELECTION", "GROUNDED_ANSWER")}
    if seen != expected:
        raise ValueError("Reviewed train/validation tasks are not a complete pair per case")
    return reviewed_cases, output_rows


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--review-csv", type=Path, default=PACKAGE / "review/train-validation-review.csv")
    parser.add_argument("--output", type=Path, default=PACKAGE / "dataset-reviewed")
    args = parser.parse_args()
    if args.output.exists():
        raise SystemExit(f"Refusing to overwrite review output: {args.output}")
    with args.review_csv.open(encoding="utf-8-sig", newline="") as stream:
        rows = list(csv.DictReader(stream))
    if any(row.get("split") not in {"train", "validation"} for row in rows):
        raise SystemExit("Review CSV must contain only train and validation cases; test cases are evaluation-only.")
    reviews = {}
    for row in rows:
        case_id = row.get("caseId", "")
        if not case_id or case_id in reviews:
            raise SystemExit("Review CSV has an empty or duplicate caseId")
        reviews[case_id] = row
    output_splits, case_counts = {}, {}
    reviewed_case_splits = {}
    for split in ("train", "validation"):
        case_rows = read_jsonl(PACKAGE / "provenance" / f"{split}-cases.jsonl")
        cases = {row["id"]: row for row in case_rows}
        split_reviews = {key: value for key, value in reviews.items() if value.get("split") == split}
        source_rows = read_jsonl(PACKAGE / "dataset" / f"{split}.jsonl")
        reviewed_cases, reviewed_rows = apply_review_to_splits(cases, split_reviews, source_rows)
        reviewed_case_splits[split] = list(reviewed_cases.values())
        output_splits[split] = reviewed_rows
        case_counts[split] = {"cases": len(reviewed_cases), "records": len(reviewed_rows)}
    args.output.mkdir(parents=True)
    for split, output_rows in output_splits.items():
        write_jsonl(args.output / f"{split}.jsonl", output_rows)
        write_jsonl(args.output / f"{split}-cases.jsonl", reviewed_case_splits[split])
    digest = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
    report = {"schemaVersion": 1, "javaStrategyVersion": "ranked-grounded-v3", "teacherReviewed": True,
              "reviewSourceSha256": digest(args.review_csv), "sourceDatasetManifestSha256": digest(PACKAGE / "dataset/manifest.json"),
              "reviewedCases": case_counts, "testDataLoaded": False,
              "filesSha256": {path.name: digest(path) for path in sorted(args.output.glob("*.jsonl"))}}
    (args.output / "manifest.json").write_text(json.dumps(report, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"output": str(args.output), "reviewedCases": case_counts, "testDataLoaded": False},
                     ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
