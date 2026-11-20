# Platform Optimization Roadmap

Baseline: backend 199 tests pass; frontend 48 tests pass; production build succeeds.

## Phase 1: Navigation, design system, and dependency status

- Group learner, research, and admin navigation.
- Add an authenticated status endpoint with no secrets or internal addresses.
- Show honest dependency failures while keeping local learning features usable.

## Phase 2: Learning workflow

- **Wave 2 delivered:** Improve loading, empty, error, retry, and next-step states across the core learning views; add shared context navigation, honest graph evidence drawer, safe Markdown output, 2D/3D graph toolbar, and mobile/desktop responsive constraints.
- Preserve explainable rule-based recommendation wording, answer privacy, and account isolation.

## Phase 3: Backend resilience and security

- Review downstream timeouts, SSE cleanup, outbox idempotency, Redis invalidation, JWT refresh, and IDOR boundaries.
- Add regression tests before production changes.

## Phase 4: Experiments and cognitive-load evaluation

- **Wave 3 delivered:** Separate automatic metrics from human ratings, add effective BLEU order, server-side CSV/JSON reproducibility exports, explicit failed-condition states, task-order/DELAYED evaluation fields, Paas anchors, and anonymized descriptive statistics.
- Preserve the hidden RANKED system prompt and anonymized Paas/task-time/quiz/feedback exports.

## Phase 5: Verification and documentation

- Run full backend/frontend verification, update project docs, inspect the diff, and report remaining runtime prerequisites.

## Non-goals

- Do not modify Mecha-QA, Python Bridge code, model weights, or NebulaGraph data.
- Do not fabricate evidence, hide failures, or claim a proven cognitive-load reduction.

## Wave 4 Delivery Note (2026-11-20)

- Delivered explicit QA strategy dispatch and grounded evidence-rule extraction without changing public REST/SSE contracts.
- Delivered empty-model, graph-client timeout/contract validation, unknown-event retry/DLQ, idempotency, and answer-privacy regressions.
- Delivered management-console confirmations, JSON import bounds, shared feedback states, tokenized styles, and final documentation alignment.
- Final evidence: AI 99 tests, learning-service 117 tests, gateway 2 tests, frontend 59 tests, and production build all pass; remaining risk is the known Three.js chunk-size warning and the need for formal fixed-set/teacher blind evaluation.
