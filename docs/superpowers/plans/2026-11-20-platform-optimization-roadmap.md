# Platform Optimization Roadmap

Baseline: backend 199 tests pass; frontend 48 tests pass; production build succeeds.

## Phase 1: Navigation, design system, and dependency status

- Group learner, research, and admin navigation.
- Add an authenticated status endpoint with no secrets or internal addresses.
- Show honest dependency failures while keeping local learning features usable.

## Phase 2: Learning workflow

- Improve loading, empty, error, retry, and next-step states across learning views.
- Preserve explainable rule-based recommendation wording, answer privacy, and account isolation.

## Phase 3: Backend resilience and security

- Review downstream timeouts, SSE cleanup, outbox idempotency, Redis invalidation, JWT refresh, and IDOR boundaries.
- Add regression tests before production changes.

## Phase 4: Experiments and cognitive-load evaluation

- Separate automatic metrics from human ratings and keep failed runs out of scoring.
- Preserve the hidden RANKED system prompt and anonymized Paas/task-time/quiz/feedback exports.

## Phase 5: Verification and documentation

- Run full backend/frontend verification, update project docs, inspect the diff, and report remaining runtime prerequisites.

## Non-goals

- Do not modify Mecha-QA, Python Bridge code, model weights, or NebulaGraph data.
- Do not fabricate evidence, hide failures, or claim a proven cognitive-load reduction.
