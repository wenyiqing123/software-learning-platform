# Wave 4 Finalization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task with regression checkpoints.

**Goal:** Complete the Wave 4 backend stability/security review, management-console polish, and final documentation without changing public REST/SSE contracts or external Mecha-QA/Python/NebulaGraph code.

**Architecture:** Keep `AnswerService` as the compatibility facade, but move mode selection into small package-private strategy components and keep `GroundedRankedAnswer` as the evidence-selection/generation engine. Harden dependency clients and event consumers with bounded failures, idempotency, and user-scoped queries. Apply the existing CSS token system to admin/reporting views and finish with automated and runtime verification.

**Tech Stack:** Java 21, Spring Boot, Reactor, RabbitMQ, Redis, MyBatis-Plus, Vue 3, Element Plus, Vitest, Maven.

**Spec:** `docs/superpowers/plans/2026-11-20-platform-optimization-roadmap.md` and the Wave 4 request in the current task.

## Global Constraints

- Preserve `/api/ai/ask`, `/api/ai/compare`, `/api/ai/compare/qwen-matrix`, and `/api/ai/ask/stream` request/response/SSE contracts.
- Do not expose the RANKED evidence/generation system prompt in API responses, frontend state, or exports.
- Do not modify `Mecha-QA`, Python Bridge source, model weights, or NebulaGraph data.
- Student quiz APIs must not return answer fields before submission; all learner data access must be scoped to the authenticated user.
- Failed dependency calls remain explicit structured failures; no fallback may fabricate answer, graph, or experiment evidence.
- Use existing frontend design tokens and Element Plus; do not introduce a second UI framework.

### Task 1: Capture backend strategy and boundary regressions

**Files:**
- Create: `backend/ai-service/src/main/java/edu/wfust/learning/ai/QaExecutionStrategy.java`
- Create: `backend/ai-service/src/main/java/edu/wfust/learning/ai/QaStrategySelector.java`
- Test: `backend/ai-service/src/test/java/edu/wfust/learning/ai/QaStrategySelectorTest.java`

- [x] Add tests proving DIRECT, TRIPLES, RANKED, and each ablation map to the intended strategy family and that unknown/null modes fail explicitly.
- [x] Run the focused test and observe the expected missing-class failure.
- [x] Add the smallest selector/strategy interfaces needed by the existing facade; do not move generation logic yet.
- [x] Run focused tests and then the existing AnswerService/PromptStrategy/GroundedRankedAnswer tests.

### Task 2: Extract mode dispatch without changing contracts

**Files:**
- Modify: `backend/ai-service/src/main/java/edu/wfust/learning/ai/AnswerService.java`
- Modify: `backend/ai-service/src/main/java/edu/wfust/learning/ai/GroundedRankedAnswer.java` only for focused helper visibility if required
- Test: existing AI service tests plus selector regression tests

- [x] Introduce a small immutable execution context containing request, snapshot, prompt, provenance, and model choice.
- [x] Route direct generation, triples/ablation generation, and ranked grounded generation through strategy adapters while retaining the facade methods and response assembly.
- [x] Keep hidden system prompt hashes server-side and keep `AnswerResponse` fields unchanged.
- [x] Run the full AI module test suite before proceeding.

### Task 3: Harden dependency and event boundaries

**Files:**
- Modify: `backend/ai-service/src/main/java/edu/wfust/learning/ai/AiModelClient.java`
- Modify: `backend/ai-service/src/main/java/edu/wfust/learning/ai/client/PythonKnowledgeClient.java`
- Modify: `backend/learning-service/src/main/java/edu/wfust/learning/platform/knowledge/PythonGraphClient.java`
- Modify: `backend/learning-service/src/main/java/edu/wfust/learning/platform/messaging/OutboxRelay.java`
- Modify: `backend/learning-service/src/main/java/edu/wfust/learning/platform/messaging/KnowledgeMasteryConsumer.java`
- Modify: `backend/learning-service/src/main/java/edu/wfust/learning/platform/recommendation/RecommendationService.java`
- Test: focused client, messaging, and recommendation tests

- [x] Add/verify bounded timeout and stable error-code mapping for empty, 4xx, 5xx, malformed, and unavailable upstream responses.
- [x] Ensure outbox failures are observable and retryable without duplicate logical delivery.
- [x] Ensure mastery events are idempotent and Redis invalidation happens after successful transaction completion.
- [x] Ensure recommendation cache misses/errors degrade to a user-scoped database result without cross-user reuse.

### Task 4: Security and IDOR regression coverage

**Files:**
- Modify: relevant learning-service controllers/services only where tests identify a real gap
- Test: existing security/integration tests and new focused regression tests

- [x] Assert pre-submit question payloads contain no standard-answer field or correct option.
- [x] Assert authenticated user A cannot read or mutate user B progress, notes, wrong-book, review, recommendation, tutor, report, experiment, or evaluation records.
- [x] Assert admin-only endpoints reject learner tokens.
- [x] Scan tracked source/config files for credential-like literals and verify only templates/placeholders remain.

### Task 5: Admin/reporting UI convergence

**Files:**
- Modify: `frontend/src/views/AdminView.vue`
- Modify: `frontend/src/views/AdminDiagnosisView.vue`
- Modify: `frontend/src/views/QuestionBankView.vue`
- Modify: `frontend/src/views/MicroLessonAdminView.vue`
- Modify: `frontend/src/views/MicroLessonView.vue`
- Modify: `frontend/src/views/TeachingDashboardView.vue`
- Modify: `frontend/src/views/LearningReportView.vue`
- Test: existing view tests plus focused interaction tests

- [x] Normalize page headers, filters, tables, empty/error/loading states, pagination/result counts, and responsive table behavior using existing tokens.
- [x] Add field validation and explicit confirmation for destructive actions; surface success/failure instead of silent mutation.
- [x] Preserve existing route/query contracts and administrator/learner permissions.
- [x] Run frontend tests and production build.

### Task 6: Documentation and final acceptance evidence

**Files:**
- Modify: `README.md`
- Modify: `docs/api.md`
- Modify: `docs/ui-design.md`
- Modify: `docs/test-report.md`
- Modify: `docs/development/continuation-ledger.md`
- Modify: `docs/毕业设计工程验收报告.md`

- [x] Update migration/version references to V15 and future V16+.
- [x] Reconcile endpoint fields, environment template, test counts, and startup prerequisites with the implementation.
- [x] Record changed files, security review conclusions, academic-compliance checks, and a concise defense demonstration path.
- [x] Run full Maven tests, Vitest, build, smoke checks, and `git diff --check`; include actual results in the acceptance report.
