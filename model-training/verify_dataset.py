"""Verify JSONL checksums, splits, evidence labels and complete held-out answer leakage."""
import argparse
import hashlib
import json
import math
from pathlib import Path
from build_dataset import norm, validate_dataset


def read(path):
    return [json.loads(line) for line in path.read_text(encoding="utf8").splitlines() if line.strip()]


def verify(root):
    manifest = json.loads((root / "manifest.json").read_text(encoding="utf8"))
    for name, checksum in manifest["sha256"].items():
        if hashlib.sha256((root / name).read_bytes()).hexdigest() != checksum:
            raise ValueError("Checksum mismatch: " + name)
    splits = {s: read(root / f"{s}-facts.jsonl") for s in ("train", "validation", "test")}
    cases = {s: read(root / f"{s}-cases.jsonl") for s in splits}
    report = validate_dataset(splits, cases)
    all_messages = {}
    for split in splits:
        by_id = {c["id"]: c for c in cases[split]}
        records = read(root / f"{split}.jsonl")
        if len(records) != len(by_id) * 4:
            raise ValueError("Expected four training task representations per question")
        seen = set()
        for row in records:
            key = (row["caseId"], row["task"])
            if key in seen:
                raise ValueError("Repeated task/question row")
            seen.add(key)
            case = by_id[row["caseId"]]
            messages = row["messages"]
            roles = [m["role"] for m in messages]
            if roles not in (["user", "assistant"], ["system", "user", "assistant"]):
                raise ValueError("Invalid message roles")
            if any(not isinstance(m["content"], str) or not m["content"].strip() for m in messages):
                raise ValueError("Empty message")
            if row["task"] == "EVIDENCE_SELECTION":
                label = json.loads(messages[-1]["content"])
                if label.get("selectedIds") != case["goldEvidenceIds"] or label.get("status") != "SUFFICIENT":
                    raise ValueError("Invalid evidence selection label")
            elif row["task"] == "GROUNDED_ANSWER":
                label = json.loads(messages[-1]["content"])
                if label.get("usedIds") != case["goldEvidenceIds"] or label.get("answer") != case["referenceAnswer"]:
                    raise ValueError("Invalid grounded generation label")
            elif row["task"] in {"DIRECT", "TRIPLES"}:
                if messages[-1]["content"] != case["referenceAnswer"]:
                    raise ValueError("Invalid direct/triples reference label")
            else:
                raise ValueError("Unknown task")
            for candidate in case["candidates"]:
                score = candidate.get("score")
                if not isinstance(score, (int, float)) or not math.isfinite(score) or not -1.00001 <= score <= 1.00001:
                    raise ValueError("Invalid cosine score")
        all_messages[split] = norm("\n".join(m["content"] for r in records for m in r["messages"]))
    long_answer_collisions = []
    for split, facts in splits.items():
        for f in facts:
            obj = norm(f["object"])
            if len(obj) >= 8:
                for other, text in all_messages.items():
                    if split != other and obj in text:
                        long_answer_collisions.append({"factId": f["id"], "from": split, "foundIn": other})
    if long_answer_collisions:
        raise ValueError("Held-out long answer appears in other-split messages: " + str(long_answer_collisions[:5]))
    report["long_answer_substring_overlap_ge8"] = 0
    report["all_jsonl_hashes_verified"] = True
    report["training_schema_and_evidence_labels_verified"] = True
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("dataset", type=Path)
    args = parser.parse_args()
    print(json.dumps(verify(args.dataset), ensure_ascii=False, indent=2))
