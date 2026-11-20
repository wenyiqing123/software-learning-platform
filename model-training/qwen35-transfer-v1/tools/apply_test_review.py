"""Apply a separate blinded teacher review to held-out gold labels, never to SFT rows."""
import argparse
import csv
import hashlib
import json
from pathlib import Path

from apply_review import apply_review_to_case
from prepare_dataset import PACKAGE, read_jsonl, write_jsonl


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--review-csv", type=Path, default=PACKAGE / "review/test-review.csv")
    parser.add_argument("--output", type=Path, default=PACKAGE / "evaluation-reviewed")
    args = parser.parse_args()
    if args.output.exists():
        raise SystemExit(f"Refusing to overwrite test review output: {args.output}")
    with args.review_csv.open(encoding="utf-8-sig", newline="") as stream:
        rows = list(csv.DictReader(stream))
    if any(row.get("split") != "test" for row in rows):
        raise SystemExit("The held-out gold review CSV must contain only test cases.")
    reviews = {row.get("caseId", ""): row for row in rows}
    cases = read_jsonl(PACKAGE / "evaluation/test-cases.jsonl")
    if len(reviews) != len(rows) or set(reviews) != {case["id"] for case in cases}:
        raise SystemExit("Test review CSV must contain exactly one row for every held-out case.")
    reviewed = [apply_review_to_case(case, reviews[case["id"]]) for case in cases]
    args.output.mkdir(parents=True)
    write_jsonl(args.output / "test-cases.jsonl", reviewed)
    digest = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
    manifest = {"teacherReviewed": True, "split": "test", "testDataLoadedForTraining": False,
                "reviewCsvSha256": digest(args.review_csv),
                "sourceTestCasesSha256": digest(PACKAGE / "evaluation/test-cases.jsonl"),
                "testCasesSha256": digest(args.output / "test-cases.jsonl"), "caseCount": len(reviewed)}
    (args.output / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"output": str(args.output), "split": "test", "caseCount": len(reviewed),
                      "trainingSplitChanged": False}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
