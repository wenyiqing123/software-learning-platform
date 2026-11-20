# Qwen3.5 External Fine-Tuning Bundle Implementation Plan

**Goal:** Prepare a verifiable portable package for Qwen3.5-0.8B LoRA fine-tuning on an external NVIDIA machine, without changing Mecha-QA or the platform runtime.

**Architecture:** Keep all download, dataset projection, training, evaluation, and packaging tools under `model-training/qwen35-transfer-v1/`. Pin the Hugging Face base-model revision and expected file hashes; derive only the two tasks used by Java `ranked-grounded-v3`; use assistant-only causal loss, train/validation separation, and a held-out test evaluation command.

**Tech Stack:** Python 3.11, PyTorch CUDA 12.8, Transformers, PEFT LoRA, Hugging Face Hub, JSONL, SHA-256.

**Spec:** User request in this conversation; preserve the Nebula snapshot, existing Mecha-QA, Java DeepSeek configuration, and all existing environments.

## Global Constraints

- Do not modify `Mecha-QA`, existing Anaconda/LLM environments, Java application behavior, Nebula data, or Ollama GGUF weights.
- Pin the base model to `Qwen/Qwen3.5-0.8B` revision `2fc06364715b967f1860aea9cf38778875588b17` and validate the 1,746,942,600-byte Safetensors shard against SHA-256 `04b1c301231dd422b8860db31311ab2721511346a32cb1e079c4c4e5f1fe4696`.
- Train only `EVIDENCE_SELECTION` and `GROUNDED_ANSWER` examples; use the graph snapshot's existing split; never feed test records to training or validation.
- Use exact Java prompt/evidence behavior and reproduce `ExperimentMetrics` character-level ROUGE-L, effective-order BLEU-4, and expected-entity recall.
- Clearly label graph-template references as unreviewed; never claim fine-tuning or improved experimental metrics happened locally.
- Make all scripts create fresh output paths or fail instead of overwriting prior artifacts.

## File Map

- `model-training/qwen35-transfer-v1/tools/prepare_dataset.py`: verify and project existing task JSONL; emit train/validation/test evaluation files and a human-review CSV.
- `model-training/qwen35-transfer-v1/tools/metrics.py`: port Java evaluation metrics for reproducible post-transfer evaluation.
- `model-training/qwen35-transfer-v1/tools/train.py`: Qwen3.5 text-only, assistant-only LoRA SFT with validation loss checkpointing.
- `model-training/qwen35-transfer-v1/tools/evaluate.py`: measure selection quality and grounded answer metrics on test data, with optional adapter.
- `model-training/qwen35-transfer-v1/tools/fetch_model.py`: download revision-pinned HF assets and verify SHA-256/size.
- `model-training/qwen35-transfer-v1/tests/test_bundle.py`: dataset isolation, task pairing, loss-mask, and metric regressions.
- `model-training/qwen35-transfer-v1/{requirements.txt,README.md}` and `tools/install_python_environment.py`: isolated dependency install and Chinese transfer guide.
- `model-training/qwen35-transfer-v1/base-model/`: official Transformers Safetensors snapshot; separate from Ollama GGUF.
- `model-training/qwen35-transfer-v1/dataset/` and `evaluation/`: provenance and split files; test remains evaluation-only.
- `model-training/qwen35-transfer-v1/review/`: explicit teacher-review checklists for graph-derived labels.

## Tasks

1. [x] Add failing tests for RANKED-only export, paired-task completeness, cross-split isolation, assistant-only labels, and metric parity.
2. [x] Implement dataset projection, prompt/version/provenance validation, label-review export, and SHA-256 manifest generation.
3. [x] Implement Java-parity metric scoring and isolated evaluator; test Chinese punctuation, empty outputs, entity recall, and multi-evidence checks.
4. [x] Implement revision-pinned model download and hash checks; preserve license, tokenizer, chat template, and model metadata.
5. [x] Implement assistant-only LoRA SFT, train/validation safeguards, validation-loss checkpoint selection, tokenizer length checks, and a 16 GB speed profile with automatic largest-fitting micro-batch probing.
6. [x] Add Linux/Windows isolated Python CUDA environment installer, package verification, ZIP64 packer, and transfer instructions. External dependency installation requires network access.
7. [x] Run data/unit/leakage/checksum/tokenizer/ZIP verification; graph-template labels remain explicitly unreviewed and no training was run.
