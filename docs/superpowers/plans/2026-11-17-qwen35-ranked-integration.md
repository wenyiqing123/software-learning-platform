# Qwen3.5 RANKED Integration Implementation Plan

> **For agentic workers:** Execute this plan inline using TDD and verification checkpoints. Keep the optional route disabled until all acceptance checks pass.

**Goal:** Serve the existing Qwen3.5 LoRA adapter from the T4 workspace and wire it only into interactive RANKED chat with evidence validation and explicit rollback.

**Architecture:** Add a small OpenAI-compatible Python inference service using the existing Transformers/PEFT environment. Add a separately configured Spring `AiModelClient` for optional interactive RANKED requests; keep comparison arms on the original shared model and buffer RANKED SSE until grounding validation succeeds.

**Tech Stack:** Python 3.11, PyTorch/CUDA, Transformers, PEFT, stdlib HTTP server/SSE; Spring Boot 3, Spring AI OpenAI-compatible client, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-11-17-qwen35-ranked-integration.md`

## Global Constraints

- Do not modify Mecha-QA or its existing Python Bridge.
- Qwen integration defaults off; do not replace the DeepSeek default.
- Only interactive RANKED uses the Qwen client; compare/ablation calls keep the shared default client.
- Fail closed on Qwen unavailability; disabling the feature restores existing behavior.
- Never log prompt bodies, API keys, or authorization headers.
- Do not modify graph data, training data, adapter weights, drivers, TrustedHosts, firewall, or global environment.
- The 72-case automatic test labels remain explicitly unreviewed and exploratory.

---

### Task 1: Python OpenAI-Compatible Inference Service

**Files:**
- Create: `model-training/qwen35-transfer-v1/serving/openai_server.py`
- Create: `model-training/qwen35-transfer-v1/serving/test_openai_server.py`
- Create: `model-training/qwen35-transfer-v1/serving/README.md`
- Modify: `model-training/qwen35-transfer-v1/tools/verify_package.py` only if serving files need packaged-manifest verification.

**Interfaces:** `GET /health`, `GET /v1/models`, `POST /v1/chat/completions`; model dir and adapter dir come from environment variables. JSON request/response follows OpenAI Chat Completions. Streaming follows SSE `data:` frames ending in `data: [DONE]`.

- [ ] Write failing service tests using injected fake tokenizer/model for authorization, request bounds, message formatting, JSON usage, stream frames, and health response.
- [ ] Run the focused Python test file and confirm expected failures.
- [ ] Implement configuration parsing, Bearer auth, bounded stdlib HTTP handling, lazy/model startup, serialized GPU generation, JSON completion and `TextIteratorStreamer` SSE without logging user content.
- [ ] Run focused Python tests, then package verifier and all current training-bundle tests.
- [ ] Validate remote environment imports pinned `torch`, `transformers`, `peft` and the existing model/adapter checksums before serving.

### Task 2: Optional Java Ranked Model Configuration

**Files:**
- Create: `backend/ai-service/src/main/java/edu/wfust/learning/ai/RankedModelSettings.java`
- Create: `backend/ai-service/src/main/java/edu/wfust/learning/ai/RankedModelConfiguration.java`
- Modify: `backend/ai-service/src/main/java/edu/wfust/learning/ai/GroundedRankedAnswer.java`
- Modify: `backend/ai-service/src/main/java/edu/wfust/learning/ai/AnswerService.java`
- Modify: `backend/ai-service/src/main/java/edu/wfust/learning/ai/AnswerResponse.java` only if provenance cannot identify actual model without schema-safe addition.
- Test: `backend/ai-service/src/test/java/edu/wfust/learning/ai/RankedModelSettingsTest.java`
- Test: `backend/ai-service/src/test/java/edu/wfust/learning/ai/AnswerServiceTest.java`
- Test: `backend/ai-service/src/test/java/edu/wfust/learning/ai/ModelSettingsTest.java`

**Interfaces:** `RankedModelSettings.enabled/configured/requireConfigured/model/baseUrl/options`; conditional bean named `rankedModelClient`; AnswerService receives an optional Qwen `GroundedRankedAnswer` and an explicit feature flag.

- [ ] Write tests proving off-by-default, valid/invalid URL and placeholder key behavior, and actual-model provenance.
- [ ] Run targeted tests and confirm they fail for missing optional configuration/routing.
- [ ] Add validated configuration with safe messages and no logging of credentials.
- [ ] Add Qwen-only grounded answer bean; keep the existing grounded bean on the default model for compare/ablations.
- [ ] Route only non-comparison RANKED through Qwen when both feature and client are configured; otherwise preserve the existing default-model route.
- [ ] Run targeted tests and the full `ai-service` test suite.

### Task 3: Grounded RANKED Streaming and Failure Contract

**Files:**
- Modify: `backend/ai-service/src/main/java/edu/wfust/learning/ai/AnswerStreamSession.java`
- Modify: `backend/ai-service/src/main/java/edu/wfust/learning/ai/AnswerService.java`
- Test: `backend/ai-service/src/test/java/edu/wfust/learning/ai/AnswerStreamSessionTest.java`
- Test: `backend/ai-service/src/test/java/edu/wfust/learning/ai/AnswerServiceTest.java`

- [ ] Add failing tests proving Qwen RANKED does not emit answer delta before full answer validation; rejected JSON/evidence mismatch emits error and no delta.
- [ ] Keep DIRECT/TRIPLES SSE streaming behavior unchanged and keep comparison synchronous/default-model behavior unchanged.
- [ ] Implement buffered event flow for the opt-in Qwen RANKED route: status, then validated knowledge, answer delta, done; safe failure emits error only.
- [ ] Run focused SSE/service tests and full AI-service tests.

### Task 4: Local and Remote Configuration/Operations

**Files:**
- Modify: `.env.example`
- Modify: `deploy/compose.server.yml` only to add opt-in external URL/key/enable variables to ai-service; never enable by default.
- Create: `scripts/start-qwen35-remote.ps1` (uses current-user stored Windows credentials, no password argument and no system config mutation).
- Create: `scripts/stop-qwen35-remote.ps1` (stops only the process recorded by this script after PID/start-time verification).
- Create: `scripts/qwen35-service-status.ps1`
- Modify: project README model section and serving README with exact start/stop/config/rollback commands.

- [ ] Unit-test or static-test script path safety and process ownership checks where practical.
- [ ] Prepare remote service files in `D:\SXJ\qwen35-finetune\serving`, keeping the base model and adapter read-only.
- [ ] Start hidden remote Python service on port 8998; record PID/start time/log path under the workspace.
- [ ] Verify health, `/v1/models`, missing-key 401, authenticated JSON completion, and authenticated SSE completion.
- [ ] Verify no system PATH, driver, firewall, TrustedHosts, global variables, or Mecha-QA files changed.

### Task 5: End-to-End Acceptance and Rollback

**Files:**
- Create: `model-training/results/qwen35-integration-20261117/acceptance-report.json`
- Modify: `.env.local` only after service and tests pass, adding opt-in settings without printing secrets; preserve all existing values.

- [ ] Run Maven `ai-service` tests, backend tests, Python service tests, and frontend tests/build if AI interaction contracts changed.
- [ ] Enable the opt-in route locally, verify `/api/ai/dependencies` reports the ranked model without exposing credentials, and exercise direct/triples/stream/compare routing.
- [ ] Run at least five live RANKED questions through local Java + existing Python Bridge/Nebula + remote Qwen, covering single-fact, multi-fact, no-evidence, service error, and streaming.
- [ ] Require every successful RANKED response to pass Java `GroundedRankedAnswer` selected-ID/entity-relation-object checks; collect latency and safe model provenance.
- [ ] Turn the feature off, verify existing DeepSeek RANKED route recovers, then restore the feature off unless every acceptance gate is satisfied.
- [ ] Mark integration complete only with actual command outputs and a written acceptance report; otherwise keep disabled and report the specific blocker.
