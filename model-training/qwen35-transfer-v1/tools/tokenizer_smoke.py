"""Tokenize every packaged SFT and held-out row with the pinned Qwen chat template."""
import json
from pathlib import Path

from transformers import AutoConfig, AutoTokenizer

from fetch_model import MODEL_DIR, REVISION
from prepare_dataset import PACKAGE, encode_conversation, read_jsonl


def inspect_rows(name, path, tokenizer, max_seq_len):
    paths = path if isinstance(path, (list, tuple)) else (path,)
    rows = [row for item in paths for row in read_jsonl(item)]
    lengths = []
    over_limit = []
    for row in rows:
        try:
            encoded = encode_conversation(tokenizer, row["messages"], max_seq_len)
            lengths.append(len(encoded["input_ids"]))
        except ValueError as error:
            if "exceeding max_seq_len" not in str(error):
                raise
            over_limit.append(row.get("caseId"))
    return {"rows": len(rows), "tokenized": len(lengths), "overLimit": len(over_limit),
            "overLimitCaseIds": over_limit, "maxTokens": max(lengths) if lengths else None}


def main():
    if not (MODEL_DIR / "config.json").is_file():
        raise SystemExit("Pinned base model is not present")
    config = AutoConfig.from_pretrained(MODEL_DIR, local_files_only=True)
    tokenizer = AutoTokenizer.from_pretrained(MODEL_DIR, local_files_only=True, use_fast=True)
    max_seq_len = 1536
    report = {"model": "Qwen/Qwen3.5-0.8B", "revision": REVISION, "modelType": config.model_type,
              "tokenizerClass": type(tokenizer).__name__, "vocabSize": len(tokenizer), "maxSeqLen": max_seq_len,
              "splits": {
                  "train": inspect_rows("train", PACKAGE / "dataset/train.jsonl", tokenizer, max_seq_len),
                  "validation": inspect_rows("validation", PACKAGE / "dataset/validation.jsonl", tokenizer, max_seq_len),
                  "test": inspect_rows("test", [PACKAGE / "evaluation/test-selection.jsonl",
                                                  PACKAGE / "evaluation/test-grounded-answer.jsonl"],
                                        tokenizer, max_seq_len),
              }}
    if any(split["overLimit"] for split in report["splits"].values()):
        raise SystemExit("Some records exceed the current token limit; inspect provenance/tokenization-smoke.json")
    target = PACKAGE / "provenance/tokenization-smoke.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(report, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
