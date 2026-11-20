## Ownership and security

Only the Java AI service and this contract are in scope. The learning service owns per-user chat history, persisted experiment results and evaluation. AI does not persist experiments or sign results. Evidence comes from the read-only Python Knowledge Bridge at `PYTHON_KNOWLEDGE_URL`, fixed to `spaceId=1`; it never modifies the graph or Python source.

AI business endpoints require an existing bearer JWT. Only internal `ERROR` and `ASYNC` servlet dispatches bypass authentication; clients cannot access business endpoints unauthenticated. `JWT_SECRET` is a required YAML placeholder; the start script must provide the same secret across services. Startup requires at least 32 nonblank UTF-8 bytes.

## Configuration

| Variable | Default or limit | Purpose |
|---|---|---|
| `JWT_SECRET` | Required | Shared service secret; at least 32 UTF-8 bytes. |
| `OPENAI_API_KEY` | Empty means unconfigured | Missing/placeholders make no upstream call. |
| `OPENAI_BASE_URL` | `https://api.openai.com/v1` | HTTP(S) compatible API base. Preserve proxy paths like `/proxy/v1`; append `/v1` only for the host root. Do not include credentials, query/fragment, or `/chat/completions`. |
| `OPENAI_MODEL` | `gpt-4o-mini` | Fixed model across all comparison arms. |
| `OPENAI_TEMPERATURE` | `0.2`, valid `0..2` | Fixed temperature across all comparison arms. |
| `OPENAI_MAX_TOKENS` | `1024`, valid `1..131072` | Fixed generation token limit. |
| `AI_MODEL_HTTP_TIMEOUT` | `30s`, maximum `120s` | Bounded provider requests and streaming; provider retries disabled. |
| `PYTHON_KNOWLEDGE_URL` | `http://127.0.0.1:8999` | Shared variable name across services. Connect/read timeouts: `3s`/`15s`. |
| `AI_STREAM_TIMEOUT` | `120s`, maximum `10m` | Absolute SSE deadline. |
| `AI_COMPARE_TIMEOUT` | `150s`, maximum `180s` | Absolute comparison deadline. |
| Python response limit | `2 MiB` and `200` triples | Oversized untruncated ablation evidence is rejected, never silently truncated. |

The current FastAPI/Uvicorn Bridge is plain HTTP/1.1. The Java JDK HttpClient is explicitly pinned to HTTP/1.1 for this connection because its default h2c upgrade is not supported by this Uvicorn deployment and can result in an empty-body 422 response.

## Endpoints

- `POST /api/ai/ask`: `{ "question": "...", "mode": "DIRECT|TRIPLES|RANKED" }`. Omitted mode defaults to `RANKED`.
- `POST /api/ai/ask/stream`: same body; also accepts optional `historyMessages` (maximum 10 nonblank strings, 2,000 characters each) and `historyEntities` (maximum 20 nonblank strings, 200 characters each). Existing `status`, `knowledge`, `delta`, `done` and `error` SSE names remain. Invalid requests and missing model configuration return JSON `400`/`503` before asynchronous streaming.
- `POST /api/ai/compare`: `{ "question": "..." }` returns the JSON list `DIRECT`, `TRIPLES`, `RANKED`. `includeAblations` is nullable; omitted and explicit `false` both return three items, and only `Boolean.TRUE` enables ablations.
- `POST /api/ai/compare` with `{ "question": "...", "includeAblations": true }` returns the primary three followed by `ABLATION_SORTED_SCORED`, `ABLATION_WITHOUT_SCORE`, `ABLATION_WITHOUT_SORT`, `ABLATION_WITHOUT_SORT_AND_SCORE`. No new ablation endpoint is required. Experiment-only enum values on `/ask` or `/ask/stream` return `400 EXPERIMENT_ONLY_MODE`.
- `GET /api/ai/dependencies`: reports model `configured`, safe configuration status, model, temperature, `maxTokens` and default `topK`. With an absent model key, Python health is deliberately not called: `pythonKnowledgeStatus` is `NOT_CHECKED` and `pythonKnowledgeService` is JSON `null`, not `false`. With configured credentials, bounded `/health` reports `UP` or `DOWN` independently.

Comparison retrieves one Python snapshot for all evidence modes, fixes immutable model/temperature/token options, and runs arms concurrently under one absolute deadline. Queue and worker capacity are bounded; unfinished results receive `COMPARISON_TIMEOUT`, and completed arms are retained. Missing model configuration returns immediate HTTP `503`, `configured=false`, with no Python or provider calls. Error messages do not reveal upstream bodies, exception text or credentials.

## Fairness and ablations

- `DIRECT` sends only the exact question; it never calls Python. A failed Python retrieval cannot prevent its generation.
- `TRIPLES` builds an unscored Java prompt from the shared returned evidence.
- Interactive `RANKED` sends Python's exact original prompt as literal user text, preserving whitespace, braces, scores and order. Do not interpolate or rewrite it. On opt-in comparisons, use Python's documented `topK=0` so the original prompt evidence does not silently disagree with the shared Java evidence.
- Experiment comparison `RANKED` retains that exact Python prompt as the user message and adds the backend-only `ranked-grounded-v3` instructions. It first selects direct evidence IDs from the actual candidate triples, validates the selection (including a minimum of two items for explicit multi-fact/comparison questions), then requires every selected fact's subject, predicate and object in the answer. It asks the model to realize the triple as grammatical Chinese: label relations such as 规则/类型/方式/形式 connect directly to the object without 是/为; nominal judgment relations such as 实质/定义/含义 use 是/为. It must not mechanically insert a colon where a grammatical connective is needed. A missing component fails validation without scoring. For a single fact, if the model returns only the exact object, the backend may prefix the exact evidence subject and predicate and records that normalization in the trace. `result.prompt` remains the original Python prompt; the system prompt text is not returned. Provenance records `PYTHON_ORIGINAL_PLUS_SYSTEM_PROMPT`, the system prompt version and SHA-256; `grounding` records candidates, selection, raw model outputs, validation, timing, tokens and normalization. Incomplete evidence or unsupported answers fail without generating/including a scored answer. This applies to experiment comparisons only; interactive tutor and streaming `RANKED` behavior remain unchanged. Treat records without the version as the previous strategy when analyzing history.
- The four optional ablations are a separate Java factorial experiment using `JAVA_FACTORIAL_V1`: a common sorted/scored control (`ABLATION_SORTED_SCORED`) and three variants (`ABLATION_WITHOUT_SCORE`, `ABLATION_WITHOUT_SORT`, `ABLATION_WITHOUT_SORT_AND_SCORE`). Every prompt uses the same template; vary score visibility and ascending score ordering, matching `build_original_prompt(is_sort=True, is_score=True)`. Without-sort uses the raw triple order returned by Python `topK=0`.
- All evidence-driven comparison responses start from the same retrieved snapshot. `TRIPLES` is a third prompt template and is not claimed to isolate a score-only change. Experiment `RANKED` adds an evidence-selection and grounded-generation procedure, so it is not a prompt-only factorial control against the other arms; describe it as a separate strategy, not as proof that ranking alone caused a quality change.
- If Python's untruncated response exceeds the 15-second HTTP timeout, `2 MiB` body limit or `200`-triple safety limit, reject that snapshot as `ABLATION_UNSUPPORTED`; never truncate data or claim a raw order that was not returned.

## Result shape

Keep all existing `AnswerResponse` JSON fields unchanged: `question`, `mode`, `answer`, `entities`, `triples`, `hasEvidence`, `latencyMs`, `prompt` and `error`. Additive fields:

- `provenance`: opaque server-generated `runId`, shared `comparisonId`, evidence/retrieval IDs where available, evidence status (`AVAILABLE`, `EMPTY`, `FAILED`, `NOT_REQUESTED`), prompt source, Python template, SHA-256 of the exact prompt, model/temperature/`maxTokens`, `spaceId`, effective `topK`, returned/prompt order, `scoresInPrompt` and `rawOrderAvailable`. DIRECT has no evidence IDs. These IDs are informational, not signed attestations or persistence keys.
- `timings.retrievalMs`, `timings.generationMs` and `timings.totalMs`. Existing `latencyMs` remains the total.
- Nullable `usage.promptTokens`, `usage.completionTokens` and `usage.totalTokens` when the provider supplies usage. Never estimate token counts.
- Nullable `errorCode`, such as `MODEL_NOT_CONFIGURED`, `KNOWLEDGE_UNAVAILABLE`, `MODEL_UNAVAILABLE`, `ABLATION_UNSUPPORTED` or `COMPARISON_TIMEOUT`.

History is available for an individual follow-up only; forward validated request `historyMessages` and `historyEntities` to those same Python Bridge fields. Comparison remains question-only for a controlled shared-evidence run. The separate main-service `POST /api/experiments/run` should likewise use nullable `Boolean includeAblations` and `Boolean.TRUE.equals(value)` so omitted means false. That persistence endpoint is outside the AI-service edit scope.

## Smoke expectations and verification

With a missing/placeholder `OPENAI_API_KEY`, `/api/ai/compare` returns explicit HTTP `503` and performs no Python/provider call. A persisted experiment-run request must not record an attempt when its downstream compare failed; persistence begins only after a successful AI response. With a configured provider and Bridge, `includeAblations:true` returns seven modes inside the configured `150s` default / `180s` maximum deadline.

Verified on `D:\develop\Java\temurin-21\jdk-21.0.13+9`: final Maven reactor passed with 51 learning-service tests and 47 AI-service tests; frontend Vitest passed 17 tests and the production build succeeded. A separate live local-stack run verified DeepSeek, the existing Python Bridge, NebulaGraph space 1, all three primary strategies, all four ablations, exact RANKED prompt equality, and experiment/rating persistence. Live results establish integration functionality, not answer quality or a research performance improvement. The key is only in the ignored local `.env.local`; no secret is stored in this contract.
