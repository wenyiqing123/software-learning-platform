import importlib.util
import json
import shutil
import sys
import subprocess
import tempfile
import types
import unittest
from unittest.mock import patch
from pathlib import Path

PACKAGE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PACKAGE / "tools"))

from prepare_dataset import build_ranked_splits, filter_ranked_records
from prepare_dataset import encode_conversation
from training_profile import require_complete_split, validate_vram
from apply_review import apply_review_to_case, apply_review_to_splits
from metrics import evaluate_answer, normalize_for_java, requires_multiple_evidence


class BundleTests(unittest.TestCase):
    def test_evaluator_imports_and_compiles_number_pattern(self):
        stubs = {name: types.ModuleType(name) for name in
                 ("torch", "peft", "transformers", "fetch_model", "metrics")}
        stubs["peft"].PeftModel = object
        stubs["transformers"].AutoModelForMultimodalLM = object
        stubs["transformers"].AutoTokenizer = object
        stubs["fetch_model"].MODEL_DIR = PACKAGE / "base-model"
        for name in ("evaluate_answer", "mean_metrics", "normalize_for_java", "requires_multiple_evidence"):
            setattr(stubs["metrics"], name, object())
        spec = importlib.util.spec_from_file_location("isolated_evaluate", PACKAGE / "tools" / "evaluate.py")
        module = importlib.util.module_from_spec(spec)
        with patch.dict(sys.modules, stubs):
            spec.loader.exec_module(module)
        self.assertEqual(module.NUMBER.findall("编号 12 和比例 3.5"), ["12", "3.5"])

    def test_dataset_preparer_imports_without_workspace_only_modules(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            isolated_tools = Path(temporary_directory) / "qwen35-transfer-v1" / "tools"
            isolated_tools.mkdir(parents=True)
            shutil.copy2(PACKAGE / "tools" / "prepare_dataset.py", isolated_tools)
            result = subprocess.run(
                [sys.executable, "-I", "-c",
                 "import sys; sys.path.insert(0, sys.argv[1]); import prepare_dataset",
                 str(isolated_tools)],
                capture_output=True, text=True, check=False,
            )
            self.assertEqual(result.returncode, 0, result.stderr)

    def test_only_ranked_pipeline_tasks_are_exported(self):
        rows = [{"task": task, "caseId": "case-1"} for task in
                ("DIRECT", "TRIPLES", "EVIDENCE_SELECTION", "GROUNDED_ANSWER")]
        self.assertEqual([row["task"] for row in filter_ranked_records(rows)],
                         ["EVIDENCE_SELECTION", "GROUNDED_ANSWER"])

    def test_pair_integrity_and_split_isolation_are_checked(self):
        splits = {name: [{"task": task, "caseId": name + "-1"}
                         for task in ("EVIDENCE_SELECTION", "GROUNDED_ANSWER")]
                  for name in ("train", "validation", "test")}
        self.assertEqual(build_ranked_splits(splits)["cross_split_case_overlap"], 0)
        splits["test"].append({"task": "GROUNDED_ANSWER", "caseId": "train-1"})
        with self.assertRaisesRegex(ValueError, "cross-split"):
            build_ranked_splits(splits)

    def test_char_metrics_match_java_for_exact_chinese_and_punctuation(self):
        result = evaluate_answer("加工的命名规则应采用动宾短语。", "加工的命名规则应采用动宾短语", ["动宾短语"])
        self.assertEqual(result["rougeL"], 1.0)
        self.assertEqual(result["bleu4"], 1.0)
        self.assertEqual(result["entityRecall"], 1.0)
        self.assertEqual(result["tokenizer"], "unicode-character-v1")

    def test_empty_prediction_has_zero_metrics(self):
        result = evaluate_answer("", "参考答案", ["答案"])
        self.assertEqual(result["rougeL"], 0.0)
        self.assertEqual(result["bleu4"], 0.0)
        self.assertEqual(result["entityRecall"], 0.0)

    def test_metric_tokenization_matches_java_lowercase_then_character_filter(self):
        result = evaluate_answer("İ", "i", [])
        self.assertEqual(result["rougeL"], 1.0)
        self.assertEqual(result["bleu4"], 1.0)
        self.assertEqual(normalize_for_java("İ"), "i")

    def test_multi_fact_question_cannot_pass_with_single_selected_evidence(self):
        self.assertTrue(requires_multiple_evidence("分别说明A和B？", "SINGLE_FACT"))
        self.assertTrue(requires_multiple_evidence("是什么？为什么？", "SINGLE_FACT"))
        self.assertFalse(requires_multiple_evidence("加工命名规则是什么？", "SINGLE_FACT"))

    def test_only_assistant_completion_receives_loss(self):
        class Tokenizer:
            def apply_chat_template(self, messages, tokenize, add_generation_prompt, enable_thinking):
                return [1, 2, 3] if add_generation_prompt else [1, 2, 3, 4, 5]

        encoded = encode_conversation(Tokenizer(), [
            {"role": "user", "content": "问题"},
            {"role": "assistant", "content": "答案"},
        ], max_seq_len=8)
        self.assertEqual(encoded["input_ids"], [1, 2, 3, 4, 5])
        self.assertEqual(encoded["labels"], [-100, -100, -100, 4, 5])

    def test_training_refuses_examples_that_exceed_the_token_limit(self):
        class Tokenizer:
            def apply_chat_template(self, messages, tokenize, add_generation_prompt, enable_thinking):
                return [1, 2, 3, 4]

        with self.assertRaisesRegex(ValueError, "max_seq_len"):
            encode_conversation(Tokenizer(), [
                {"role": "user", "content": "问题"},
                {"role": "assistant", "content": "答案"},
            ], max_seq_len=3)

    def test_full_dataset_mode_never_silently_skips_overlength_rows(self):
        self.assertEqual(require_complete_split(1248, 1248, []), 1248)
        with self.assertRaisesRegex(ValueError, "Full-split training"):
            require_complete_split(1248, 1247, ["case-1248"])

    def test_16gb_profile_rejects_insufficient_total_or_free_vram(self):
        self.assertEqual(validate_vram(15360, 8000), {"totalMiB": 15360, "freeMiB": 8000})
        with self.assertRaisesRegex(RuntimeError, "total VRAM"):
            validate_vram(4096, 3500)
        with self.assertRaisesRegex(RuntimeError, "free VRAM"):
            validate_vram(16384, 6143)

    def test_teacher_review_changes_selection_context_and_reference_consistently(self):
        case = {"id": "c1", "taskType": "SINGLE_FACT", "referenceAnswer": "甲关系旧答案",
                "expectedEntities": ["旧答案"], "goldEvidenceIds": ["T1"], "labelSource": "graph-template-needs-human-review",
                "candidates": [{"id": "T1", "subject": "甲", "predicate": "关系", "object": "旧答案"},
                               {"id": "T2", "subject": "乙", "predicate": "关系", "object": "新答案"}]}
        rows = [
            {"caseId": "c1", "task": "EVIDENCE_SELECTION", "messages": [
                {"role": "user", "content": "输入"},
                {"role": "assistant", "content": '{"status":"SUFFICIENT","taskType":"SINGLE_FACT","selectedIds":["T1"]}'}]},
            {"caseId": "c1", "task": "GROUNDED_ANSWER", "messages": [
                {"role": "system", "content": 'prompt\n{"question":"q","taskType":"SINGLE_FACT","selectedEvidence":[]} '},
                {"role": "user", "content": "输入"},
                {"role": "assistant", "content": '{"answer":"甲关系旧答案","usedIds":["T1"]}'}]},
        ]
        review = {"caseId": "c1", "reviewStatus": "APPROVED", "reviewerName": "reviewer",
                  "reviewedAt": "2026-11-16", "correctedReferenceAnswer": "乙关系新答案",
                  "correctedGoldEvidenceJson": '["T2"]'}
        reviewed_case, reviewed_rows = apply_review_to_splits({"c1": case}, {"c1": review}, rows)
        self.assertEqual(reviewed_case["c1"]["goldEvidenceIds"], ["T2"])
        self.assertEqual(reviewed_case["c1"]["expectedEntities"], ["新答案"])
        self.assertEqual(json.loads(reviewed_rows[0]["messages"][-1]["content"])["selectedIds"], ["T2"])
        grounded = reviewed_rows[1]["messages"]
        self.assertEqual(json.loads(grounded[-1]["content"])["usedIds"], ["T2"])
        self.assertEqual(json.loads(grounded[0]["content"].splitlines()[-1])["selectedEvidence"][0]["id"], "T2")

    def test_test_review_updates_only_frozen_evaluation_gold(self):
        case = {"id": "test-1", "taskType": "SINGLE_FACT", "referenceAnswer": "甲关系旧答案",
                "expectedEntities": ["旧答案"], "goldEvidenceIds": ["T1"],
                "candidates": [{"id": "T1", "subject": "甲", "predicate": "关系", "object": "旧答案"},
                               {"id": "T2", "subject": "乙", "predicate": "关系", "object": "新答案"}]}
        review = {"reviewStatus": "APPROVED", "reviewerName": "reviewer", "reviewedAt": "2026-11-16",
                  "correctedReferenceAnswer": "乙关系新答案", "correctedGoldEvidenceJson": '["T2"]'}
        reviewed = apply_review_to_case(case, review)
        self.assertEqual(reviewed["goldEvidenceIds"], ["T2"])
        self.assertEqual(reviewed["referenceAnswer"], "乙关系新答案")
        self.assertEqual(case["goldEvidenceIds"], ["T1"])
        with self.assertRaisesRegex(ValueError, "not approved"):
            apply_review_to_case(case, {**review, "reviewStatus": "PENDING"})


if __name__ == "__main__":
    unittest.main()
