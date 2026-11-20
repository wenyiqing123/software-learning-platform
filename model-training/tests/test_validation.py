import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from build_dataset import prepare_facts, split_facts, make_cases, make_messages, write_jsonl
from verify_dataset import verify


class ValidationTests(unittest.TestCase):
    def test_tampering_with_an_evidence_label_is_rejected(self):
        rows = [{"subject": f"主体{i}", "predicate": "定义", "object": f"内容{i}"} for i in range(12)]
        facts, _ = prepare_facts(rows)
        splits = split_facts(facts, seed=42)
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp)
            for split, fs in splits.items():
                cases = make_cases(fs, seed=42, max_candidates=4)
                for c in cases:
                    for e in c["candidates"]:
                        e["score"] = .4
                messages = [r for c in cases for r in make_messages(c, "select", "answer")]
                write_jsonl(path / f"{split}-facts.jsonl", fs)
                write_jsonl(path / f"{split}-cases.jsonl", cases)
                write_jsonl(path / f"{split}.jsonl", messages)
            (path / "manifest.json").write_text(json.dumps({"sha256": {}}), encoding="utf8")
            self.assertEqual(verify(path)["cross_split_fact_overlap"], 0)
            file = path / "train.jsonl"
            messages = [json.loads(s) for s in file.read_text(encoding="utf8").splitlines()]
            selection = next(m for m in messages if m["task"] == "EVIDENCE_SELECTION")
            selection["messages"][-1]["content"] = '{"status":"SUFFICIENT","selectedIds":["T999"]}'
            write_jsonl(file, messages)
            with self.assertRaisesRegex(ValueError, "selection label"):
                verify(path)


if __name__ == "__main__":
    unittest.main()
