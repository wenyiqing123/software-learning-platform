# 软件工程课程学习平台 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Deliver a runnable Vue 3 + Spring Cloud learning platform that uses the existing Python/NebulaGraph service without modifying Mecha-QA.

**Architecture:** Spring Cloud Gateway routes a modular learning service and an AI service. Learning owns MySQL business data, Redis cache, and RabbitMQ learning events. AI calls the existing Python Bridge and Spring AI. Vue talks only to the Gateway.

**Tech Stack:** JDK 21; Spring Boot 4.x; Spring Cloud 2025.1.x; Spring AI 2.x; MyBatis-Plus Boot 4 starter; local MySQL 8.0.33, Redis 5.0.14.1, RabbitMQ 4.2.3; Vue 3, TypeScript, Vite, Pinia, Element Plus, G6, ECharts.

**Spec:** docs/superpowers/specs/2026-11-11-software-engineering-learning-platform.md

## Global Constraints

- Keep all files in the new software-learning-platform project directory.
- Never modify Mecha-QA or agent-chat-studio.
- Keep spaceId=1; Python Bridge and NebulaGraph are read-only dependencies.
- Use GA releases from compatible Spring BOMs; pin resolved versions.
- Do not upgrade or reset local MySQL, Redis, RabbitMQ or Nacos data/services in place.
- Secrets are environment-only or in ignored local files; commit placeholders only.
- Write a failing test before production behavior changes and prove RED then GREEN.

## Implementation Status (2026-11-12)

Task 1 is implemented and verified: pinned backend/frontend versions, JDK 21, packaging and frontend build. Task 2 is implemented: V1-V5 migrations are applied to the dedicated MySQL schema, including auth, question bank, reading content, personal progress, refresh tokens, sessions and experiment evaluation. The database preserves all existing data outside this application's schema.

Task 3 is implemented with unit and in-process HTTP contract tests for the current Bridge request shape, spaceId=1, topK bounds, graph deduplication and offline responses. The planned Redis graph-response cache is not implemented; Redis is used for recommendation snapshots/cache. Live Bridge and NebulaGraph verification is still pending because the existing Bridge is offline.

Task 4 is implemented and exercised through local MySQL/RabbitMQ: server-side grading, answer privacy, owned wrong-book review, prerequisite-aware explainable recommendation, persisted recommendation snapshots, transactional event outbox, broker confirms and dead-letter topology. Some duplicate-consumer behavior remains covered by implementation constraints rather than a dedicated concurrent integration test.

Task 5 is implemented: three main modes and four evidence-controlled ablations; one evidence snapshot and immutable model settings per comparison; exact Python ranked prompt; token/timing/provenance metadata; bounded SSE; backend-owned experiment persistence and reference metrics. Unit/HTTP simulation tests pass. Live provider/Python experiment remains pending because the model key is empty and Bridge is offline; no model calls are made without explicit configuration.

Task 6 is implemented: responsive student course, graph, tutor, quiz, wrong-book, recommendation and experiment views; ADMIN-only course/question maintenance; local-only admin onboarding; user-scoped tutor history and reading progress. Vue unit tests and browser-driven student workflow checks pass. A dedicated Playwright suite is not included; scripts/smoke-test.ps1 is the repeatable API end-to-end check.

Task 7 is partially complete. The application starts locally, V1-V5 are applied, and 20 local API checks pass. The live student learning loop and model/graph unavailable states were verified in a browser. Mecha-QA and NebulaGraph remain unchanged. Actual graph retrieval and real model answers still require the user's original Python/Nebula services and a valid model endpoint/key. See docs/test-report.md.

---

### Task 1: Version-locked build skeleton

**Files:** root pom.xml; backend parent/service POMs; four Java modules; frontend package.json/src; .gitignore; .env.example; README.md.

**Interfaces:** Gateway listens on 8080; learning service on 8081; AI service on 8082; Vite proxies /api to 8080. Health endpoints are GET /actuator/health.

- [x] Resolve the Spring Boot 4, Cloud 2025.1, AI 2, MyBatis-Plus Boot 4 GA matrix from official BOMs and pin patch versions.
- [x] Add Maven parent modules, compile-only health apps and Java release 21.
- [x] Add Vue 3/TypeScript/Vite/Pinia/Element Plus/G6/ECharts and npm lockfile.
- [x] Run mvn test and npm run build; fix the skeleton until both succeed.

### Task 2: MySQL schema and account flow

**Files:** learning-service/src/main/resources/db/migration/V1__baseline.sql; auth controller/service/DTO/mapper/entities; auth tests.

**Interfaces:** POST /api/auth/register accepts username/password/displayName; POST /api/auth/login accepts username/password; GET /api/auth/me returns user ID, username and role.

- [x] Test registration validation, duplicate username conflict, BCrypt persistence, login success/failure and protected-route authentication.
- [x] Add Flyway tables for users, courses, chapters, knowledge points, JSON-backed questions, quiz attempts, wrong questions, learning events, mastery, recommendations, refresh tokens, reading progress, sessions and experiments.
- [x] Seed one software-engineering course, 8 reading sections and 27 objective questions; do not import or overwrite existing MySQL databases.
- [x] Run H2 tests and apply Flyway to dedicated software_learning schema in local MySQL after creating a least-privilege app user (schema at V5).

### Task 3: Course and graph read path

**Files:** course/knowledge DTO/controller/service; PythonKnowledgeClient/response DTOs; graph mapping tests.

**Interfaces:** GET /api/courses; GET /api/knowledge/points; GET /api/knowledge/graph?entity=... returns nodes, edges, evidence, available.

- [x] Test triple mapping, duplicate relations, malformed triples, bounded timeout and dependency response mapping.
- [x] Send POST /api/knowledge/retrieve with spaceId=1 and topK bounded to 200; never add graph write operations.
- [ ] Add Redis graph cache prefix slp:graph: with five-minute TTL; offline response is implemented as 503.
- [x] Test the existing Python contract using an in-process HTTP server; live :8999/NebulaGraph verification is pending because Bridge is offline.

### Task 4: Quiz, wrong book and recommendation loop

**Files:** quiz/wrong-book/recommendation DTO/controllers/services/mappers; RabbitMQ publisher/consumer; behavior tests.

**Interfaces:** GET /api/quizzes?knowledgePointId=; POST /api/quizzes/{id}/submit; GET /api/wrong-book; POST /api/wrong-book/{id}/review; GET /api/recommendations.

- [x] Test objective grading, user isolation, invalid option rejection, wrong-book review and prerequisite recommendations.
- [x] Save attempt/wrong item and unique learning outbox event transactionally; process mastery idempotently.
- [x] Declare project-specific durable exchange/queue, publisher confirms, listener retries and dead-letter routing; never change RabbitMQ global config.
- [x] Compute recommendations from error attempts, repeated mistakes, prerequisites, recency, sequence and mastery; cache Redis and persist explainable reasons.

### Task 5: AI tutor and experiment comparison

**Files:** ai-service controllers/services; Python client DTOs; Spring AI config; mode policy tests; experiment persistence.

**Interfaces:** POST /api/ai/ask; POST /api/ai/compare; POST /api/ai/ask/stream emits status, knowledge, delta, done or error SSE events.

- [x] Test direct mode bypasses Python; triple mode sends only triple text; ranked mode passes Python prompt unchanged.
- [x] Fix all comparison arms to the same model, temperature, token limit, question and shared retrieval; persist actual response, provenance, evidence, timings, optional token usage and human rating.
- [x] Add sorting/scoring factorial ablation modes with raw-order evidence bounds for experiment screen only.
- [x] Add Python/model timeouts, missing key, no-evidence, request cancellation and error-result tests.

### Task 6: Vue student and admin workflows

**Files:** frontend/src router, API, stores, views, components, styles and component tests.

**Interfaces:** API base /api; GraphResponse contains nodes/edges/evidence/available; SSE event names follow Task 5.

- [x] Test login/session, mode selection, quiz feedback, SSE framing, admin form validation and unavailable dependencies.
- [x] Implement dashboard, course reading, graph explorer, tutor, quiz, wrong-book, recommendation and experiment screens.
- [x] Implement admin course/question maintenance and persisted personal history with role guards.
- [x] Run typecheck, Vue tests and production bundle build.

### Task 7: End-to-end handoff

**Files:** Playwright configuration/tests; scripts/start.ps1; scripts/stop.ps1; database bootstrap SQL; README and test report.

- [x] Run student flow: register/login -> course reading -> graph -> quiz -> wrong-answer review -> recommendation. AI comparison endpoint returns the explicit missing-model response until credentials are configured.
- [x] Run failure flow with Python Bridge/model unavailable; non-AI modules remain usable.
- [x] Keep Mecha-QA and NebulaGraph read-only and verify local secrets are Git-ignored.
- [x] Start API/frontend on ports 8080/5173, inspect the student UI and expose the local URL. Repeatable API smoke script: scripts/smoke-test.ps1.
