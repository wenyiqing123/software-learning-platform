# Local Qwen model and Nebula dataset implementation plan

**Goal:** Download the requested Ollama `qwen3.5:0.8b` model locally and export the existing software-engineering graph as reproducible supervised fine-tuning and evaluation data. Do not train or change the platform's active DeepSeek provider.

**Architecture:** Reuse `D:/model` for Ollama. A new self-contained `model-training` folder holds a read-only Nebula exporter, a deterministic dataset builder, tests and documentation. No dependency or source file inside Mecha-QA is modified. Snapshot JSONL is immutable input to dataset generation.

**Constraints:** space ID 1 must resolve to the existing `air_ticket` space; directed `entity` / `relation` facts only; no schema or graph writes; no third-party LLM calls for labels; no implicit replacement of earlier datasets. All generated labels are graph-derived templates, not teacher-reviewed ground truth. No model training is authorized in this task.

## Tasks

- [x] Verify the exact tag/storage. Kept the installed 0.14.2 intact and downloaded SHA-256-verified official portable Ollama 0.40.16 to a separate directory because the requested model requires a newer runtime. Used loopback 11435, context 2048, parallelism 1 and existing system HTTPS proxy. Exact model downloaded; all nine content blobs verified; one inference completed; GPU weights unloaded.
- [x] Started Docker Desktop and the original metad/storaged/graphd containers without recreation. Graphd needed a second start after meta/storage became healthy. Logical ID 1 remains air_ticket; physical ID 2196; 1088 directed physical edges exported read-only.
- [x] Added and ran failing/regression tests, then passed ten tests covering duplicate ranks, split isolation, long object substring grouping, multi-object completeness, natural question templates, score provenance, read-only query guard and validation of evidence labels.
- [x] Implemented immutable raw snapshot plus deterministic grouped 80/10/10 split; 544 unique facts split 434/55/55. Duplicate source ranks are preserved; nine train facts with a nine-object relation are excluded from QA rather than labeled incompletely.
- [x] Built four chat tasks, 2496/264/288 records. Candidate order varies within split; candidate scores are actual CPU cosine scores from the existing local ranker, never fabricated from gold labels. Graph-generated references are marked for human review.
- [x] Final dataset-v1 passed schema, label, checksum and long-answer leakage checks. Independent reproducibility-v1 regeneration has identical hashes for every JSONL. Chinese README and human-review CSV delivered.
- [x] Confirmed model tag/Q8_0/1.32GB, GPU unload and unchanged DeepSeek config. Evidence and model metadata saved in model-info.json and verification-report.json. No training executed.

## Outputs

`model-training/{README.md,export_nebula.py,build_dataset.py,tests/,data/<timestamp>/,logs/}` plus the downloaded model in `D:/model`. Training files use a `messages` array; test cases carry a `question`, `referenceAnswer`, `expectedEntities`, explicit evidence, and split metadata. Test files are evaluation only.
