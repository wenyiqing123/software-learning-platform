# Spaced Review and Daily Learning Plan Implementation Plan

> **For agentic workers:** Implement this plan against the existing learning platform; verify each backend/frontend step before claiming completion.

**Goal:** Implement a student-isolated spaced review queue with honest recall ratings and a persistent, budget-bounded daily plan linking real review, reading, and practice targets.

**Architecture:** Add append-only Flyway V6 tables for review schedules/logs and daily plans/tasks. Implement authenticated Java APIs with injected Asia/Shanghai clock, deterministic intervals, idempotent review keys, and deterministic candidate ordering; render review and plan interactions in Vue 3 without modifying Python Bridge or NebulaGraph. Reuse existing MySQL, question bank, course progress, authentication, and UI patterns.

**Tech Stack:** Java 21, Spring Boot 4, MyBatis-Plus/MyBatis, MySQL, Flyway, JUnit/H2; Vue 3, TypeScript, Vitest.

**Spec:** `docs/功能扩展清单.md` (F1/F2/F3 and general constraints).

## Global Constraints

- Do not edit Mecha-QA, Python Bridge, NebulaGraph schema or graph data.
- Add only Flyway V6; never edit applied migrations.
- Scope every learner read/write by authenticated user ID.
- Use transparent fixed intervals, not an FSRS claim. Wrong objective answers cannot receive a good/easy recall rating or become mastered.
- Persist review idempotency; use fixed/injected clock for scheduling and date boundaries.
- Build only plans from existing due reviews, enabled question-bank items, and enabled unfinished course-study sections; never ask an LLM to invent tasks.
- Preserve pre-existing uncommitted work; do not reset, clean, or commit.

---

### Task 1: Review contract and scheduling tests

**Files:** Add `SpacedReviewServiceTest.java`, add review domain/service and mapper files.

- [ ] Define schedule response, request (answer keys, recall rating 0–3, UUID request key), deterministic interval boundaries, incorrect-rating constraint, user isolation, duplicate-key behavior, overdue eligibility, pause/resume/mastered transitions.
- [ ] Run targeted tests and verify expected RED before implementation.
- [ ] Implement review schedule service with injected clock and transaction-safe persistence; keep regular quiz submissions distinct from spaced-review rating.
- [ ] Run targeted tests and verify GREEN.

### Task 2: Daily-plan contract and deterministic tests

**Files:** Add `StudyPlanServiceTest.java`, plan service/mapper/controller and records.

- [ ] Cover 5–240 minute budget, deterministic priority/tie order, no budget overflow, actual enabled targets, user isolation, state preservation on refresh, and course reading/actual review preconditions for completion.
- [ ] Verify RED, implement persistence and `/api/study-plans` APIs, verify GREEN.

### Task 3: Database and integrated backend API

**Files:** Create `V6__spaced_review_and_daily_plans.sql`; update relevant Java test schemas/tests.

- [ ] Add schedule, event-log, plan, and task tables, unique idempotency keys, foreign keys, and user/date indexes.
- [ ] Add authenticated HTTP tests for due queue, review/duplicate submission, cross-user denial, plan generation/refresh, task state and budget.
- [x] Run learning-service test suite and package.

### Task 4: Student UI and API clients

**Files:** Update `frontend/src/api/client.ts`, `frontend/src/router.ts`, `frontend/src/App.vue`; add `ReviewView.vue` and `StudyPlanView.vue`; add view tests.

- [ ] Add daily plan navigation, budget selection/generation, linked real targets, reason/minute/status display, refresh, skip and completion actions.
- [ ] Add due-review UI with answer feedback, recall grade, clear incorrect-grade constraint, pause/resume/mastery actions and safe retries.
- [ ] Keep authentication, user isolation, and accessible error/loading/empty states consistent with existing app.
- [x] Run Vitest and production frontend build.

### Task 5: Verification and progress tracking

**Files:** Update `docs/功能扩展清单.md`, `docs/api.md`, this plan.

- [x] Verify targeted and full backend tests, frontend tests/build, and authenticated API smoke.
- [x] Update requirements proved by test evidence; leave F4–F8 as planned and record Python Bridge availability as a runtime prerequisite.

### M2 Progress

- [x] F3 backend hint contract, evidence-safe levels, no-evidence response and controller validation.
- [x] F3 Vue tutor controls, evidence display and existing-session metadata persistence.
- [ ] Add participant-facing hint-use evaluation fields as part of F4; do not infer learning effect from hint usage alone.
