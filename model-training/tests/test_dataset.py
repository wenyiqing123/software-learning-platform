import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from build_dataset import prepare_facts, split_facts, make_cases, make_messages, validate_dataset


def raw(subject, predicate, obj, rank=0):
    return {"subject": subject, "predicate": predicate, "object": obj,
            "src": subject, "dst": obj, "edge_rank": rank}


class DatasetTests(unittest.TestCase):
    def setUp(self):
        self.rows = [raw(f"知识点{i}", "定义", f"定义内容{i}") for i in range(30)]

    def test_duplicate_edge_ranks_do_not_create_duplicate_training_facts(self):
        facts, excluded = prepare_facts([raw("加工", "规则", "采用动宾短语"),
                                        raw("加工", "规则", "采用动宾短语", 1),
                                        raw(" ", "定义", "空主体")])
        self.assertEqual(len(facts), 1)
        self.assertEqual(len(facts[0]["sources"]), 2)
        self.assertEqual(excluded["duplicate_edges"], 1)
        self.assertEqual(excluded["invalid_rows"], 1)

    def test_subject_and_shared_answer_groups_never_cross_splits(self):
        rows = self.rows + [raw("知识点0", "作用", "其他用途"),
                           raw("别名知识点", "定义", "定义内容0")]
        facts, _ = prepare_facts(rows)
        splits = split_facts(facts, seed=42)
        self.assertEqual(set(splits), {"train", "validation", "test"})
        self.assertTrue(all(splits.values()))
        subject_owner, answer_owner = {}, {}
        for split, records in splits.items():
            for f in records:
                for owners, key in ((subject_owner, f["subject"]), (answer_owner, f["object"])):
                    self.assertIn(owners.setdefault(key, split), [split])
        self.assertEqual(splits, split_facts(list(reversed(facts)), seed=42))

    def test_same_subject_relation_multiple_objects_are_answered_together(self):
        facts, _ = prepare_facts([raw("可行性研究", "包含", "技术可行性"),
                                  raw("可行性研究", "包含", "经济可行性"),
                                  raw("需求分析", "输出", "规格说明书")])
        cases = make_cases(facts, seed=7, max_candidates=8)
        case = next(c for c in cases if c["subject"] == "可行性研究")
        self.assertEqual(len(case["goldEvidenceIds"]), 2)
        self.assertIn("技术可行性", case["referenceAnswer"])
        self.assertIn("经济可行性", case["referenceAnswer"])
        self.assertEqual(case["taskType"], "MULTI_FACT")

    def test_candidate_contexts_are_local_to_each_split_and_ids_are_valid(self):
        facts, _ = prepare_facts(self.rows)
        splits = split_facts(facts, seed=42)
        all_cases = {s: make_cases(fs, seed=42, max_candidates=5) for s, fs in splits.items()}
        for s, cases in all_cases.items():
            own = {f["id"] for f in splits[s]}
            for c in cases:
                self.assertTrue({e["factId"] for e in c["candidates"]} <= own)
                self.assertTrue(set(c["goldEvidenceIds"]) <= {e["id"] for e in c["candidates"]})
        self.assertEqual(validate_dataset(splits, all_cases)["cross_split_fact_overlap"], 0)

    def test_generation_labels_use_only_evidence_and_preserve_punctuation(self):
        text = "必须采用动词+宾语的动宾短语形式，如校验权限、生成报表"
        facts, _ = prepare_facts([raw("加工的命名", "规则", text)])
        case = make_cases(facts, seed=7, max_candidates=5)[0]
        self.assertEqual(case["expectedEntities"], [text])
        self.assertEqual(case["referenceAnswer"], "加工的命名规则" + text + "。")
        case["candidates"][0]["score"] = 0.25
        records = make_messages(case, selection_system="selection", answer_system="grounded")
        self.assertEqual({r["task"] for r in records}, {"DIRECT", "TRIPLES", "EVIDENCE_SELECTION", "GROUNDED_ANSWER"})
        for r in records:
            self.assertEqual([m["role"] for m in r["messages"]][-1], "assistant")
        selection = next(r for r in records if r["task"] == "EVIDENCE_SELECTION")
        self.assertEqual(json.loads(selection["messages"][-1]["content"])["selectedIds"], case["goldEvidenceIds"])
        direct = next(r for r in records if r["task"] == "DIRECT")
        self.assertEqual(direct["messages"][0]["content"], case["question"])

    def test_semantic_scores_are_required_and_never_filled_from_gold_labels(self):
        facts, _ = prepare_facts(self.rows[:4])
        case = make_cases(facts, seed=7, max_candidates=4)[0]
        self.assertTrue(all("score" not in e for e in case["candidates"]))
        with self.assertRaisesRegex(ValueError, "score"):
            make_messages(case, selection_system="selection", answer_system="grounded")

    def test_verbal_relations_become_questions_without_an_extra_nominal_copula(self):
        facts, _ = prepare_facts([raw("动态图", "包含", "状态图"),
                                  raw("需求", "分为", "功能需求"),
                                  raw("DFD", "使用工具", "绘图工具")])
        cases = make_cases(facts, seed=9, max_candidates=5)
        questions = {c["question"] for c in cases}
        self.assertIn("动态图包含哪些内容？", questions)
        self.assertIn("需求分为哪些类别？", questions)
        self.assertIn("DFD使用哪些工具？", questions)
        divided = next(c for c in cases if c["subject"] == "需求")
        self.assertEqual(divided["referenceAnswer"], "需求分为功能需求。")

    def test_long_heldout_answer_cannot_be_a_training_distractor_substring(self):
        text = "系统分析和设计的完整过程"
        facts, _ = prepare_facts(self.rows + [raw("研究", "定义", text),
                                            raw("研究别名", "引用", "这里指的是" + text + "的精简版")])
        splits = split_facts(facts, seed=42)
        owners = {f["subject"]:s for s, fs in splits.items() for f in fs}
        self.assertEqual(owners["研究"], owners["研究别名"])


if __name__ == "__main__":
    unittest.main()
