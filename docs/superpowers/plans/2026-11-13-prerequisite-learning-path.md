# 先修学习路径实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a learner-facing prerequisite path and an admin-only prerequisite quality report without replacing or writing graph data.

**Architecture:** Implement deterministic graph analysis and path assembly in small tested Java services over existing MySQL point rows. Add authenticated read APIs, then integrate the path view into the existing G6 knowledge page and add a read-only report panel to the existing course admin screen.

**Tech Stack:** Java 21, Spring Boot 4, MyBatis-Plus, JUnit 5, Vue 3, TypeScript, Vitest, existing AntV G6 and Vue Router.

**Spec:** `docs/superpowers/specs/2026-11-13-prerequisite-learning-path-design.md`

## Global Constraints

- Keep Mecha-QA and NebulaGraph read-only; do not change `spaceId=1` or add graph writes.
- Do not add database migrations or runtime dependencies.
- Preserve existing graph browsing, quiz, recommendation scoring, authentication, and admin permission boundaries.
- The mastery threshold for path status is 60, matching the existing recommendation prerequisite threshold.
- Do not automatically repair prerequisite data; diagnostics are read-only.

---

### Task 1: Analyze prerequisite data quality

**Files:**
- Create: `backend/learning-service/src/main/java/edu/wfust/learning/platform/admin/PrerequisiteQualityService.java`
- Modify: `backend/learning-service/src/main/java/edu/wfust/learning/platform/admin/AdminController.java`
- Test: `backend/learning-service/src/test/java/edu/wfust/learning/platform/admin/PrerequisiteQualityServiceTest.java`
- Test: `backend/learning-service/src/test/java/edu/wfust/learning/platform/admin/AdminStudyWorkflowTest.java`

**Interfaces:**
- Produces `GET /api/admin/courses/{courseId}/prerequisite-quality`.
- Report contains checked-point count, valid edge count, issues with stable codes and affected point IDs/names.
- Issue codes: `INVALID_JSON`, `INVALID_SHAPE`, `INVALID_ID`, `DUPLICATE_REFERENCE`, `MISSING_REFERENCE`, `CROSS_COURSE_REFERENCE`, `SELF_REFERENCE`, `CYCLE`.

- [x] **Step 1: Add failing analyzer tests**
  Test valid arrays, malformed/incorrect-shape JSON, non-positive/non-integral IDs, duplicate IDs, missing IDs, cross-course IDs, self-reference, and one cycle. Assert stable issue codes and no mutation of input entities.
- [x] **Step 2: Run targeted tests and verify RED**
  Run: `mvn -f backend/pom.xml -pl learning-service -Dtest=PrerequisiteQualityServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`
  Expected: compilation/test failure because the analyzer does not exist.
- [x] **Step 3: Implement deterministic analysis**
  Parse each point's JSON with Jackson; validate references against all known IDs and course IDs; count only valid unique same-course non-self edges. Use DFS color/stack tracking to report cycle members without repeated traversal or mutation.
- [x] **Step 4: Add and test the admin endpoint**
  `AdminController` delegates to the service. Reuse its class-level ADMIN security. Extend the H2 MockMvc integration test for a report and verify a STUDENT receives 403.
- [x] **Step 5: Run focused backend tests**
  Run both targeted test classes and confirm they pass.

### Task 2: Return a per-user prerequisite path

**Files:**
- Create: `backend/learning-service/src/main/java/edu/wfust/learning/platform/knowledge/PrerequisitePathService.java`
- Modify: `backend/learning-service/src/main/java/edu/wfust/learning/platform/knowledge/KnowledgeController.java`
- Test: `backend/learning-service/src/test/java/edu/wfust/learning/platform/knowledge/PrerequisitePathServiceTest.java`
- Test: `backend/learning-service/src/test/java/edu/wfust/learning/platform/knowledge/KnowledgeControllerTest.java`

**Interfaces:**
- Produces `GET /api/knowledge/learning-path?targetId={id}` for authenticated users.
- Node fields: `id`, `courseId`, `chapterId`, `name`, `graphEntity`, `mastery`, `prerequisiteIds`, `status`.
- Path fields: `targetId`, `courseId`, ordered `nodes`, `cycleDetected`.
- Status values: `MASTERED`, `IN_PROGRESS`, `UP_NEXT`, `BLOCKED`.

- [x] **Step 1: Add failing path assembly tests**
  Cover transitive prerequisite order with target last, mastery threshold/statuses, no prerequisites, missing/out-of-course IDs, duplicate IDs, and cycles returning a bounded unique list.
- [x] **Step 2: Run targeted test and verify RED**
  Run: `mvn -f backend/pom.xml -pl learning-service -Dtest=PrerequisitePathServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`
  Expected: test compilation fails because the path service does not exist.
- [x] **Step 3: Implement path assembly and query boundary**
  Build a deterministic DFS postorder over enabled points within the target's enabled course. Keep only valid same-course prerequisites, deduplicate IDs, flag cycles, then append the target. Resolve the current user's mastery from the existing mastery mapper. Return 404 for missing/disabled target or course.
- [x] **Step 4: Add endpoint authentication and response tests**
  Assert expected node ordering and per-user mastery; assert unauthenticated access is denied and a nonexistent target is 404.
- [x] **Step 5: Run focused backend tests**
  Run both targeted classes and confirm they pass.

### Task 3: Add learner path and admin diagnostics UI

**Files:**
- Modify: `frontend/src/api/client.ts`
- Modify: `frontend/src/views/GraphView.vue`
- Modify: `frontend/src/views/AdminView.vue`
- Modify: `frontend/src/styles-graph.css`
- Modify: `frontend/src/views/AdminCourseViews.test.ts`
- Create: `frontend/src/views/PrerequisitePathView.test.ts`

**Interfaces:**
- Add typed `PrerequisitePath`, node/status, and `PrerequisiteQualityReport` responses and API methods.
- Graph page exposes a visible segmented control for graph exploration versus prerequisite path.
- Admin course panel shows a read-only quality summary and issue list for the currently selected course.

- [x] **Step 1: Add failing UI tests**
  Test path loading, status labels, mastery display, cycle warning, and exact RouterLink query parameters for `/courses`, `/practice`, and `/knowledge`. Extend Admin tests for clean report, issue rows, and API request scoped to selected course.
- [x] **Step 2: Run frontend tests and verify RED**
  Run: `npm --prefix frontend test -- PrerequisitePathView.test.ts AdminCourseViews.test.ts`
  Expected: tests fail because the response types and UI are missing.
- [x] **Step 3: Implement typed API and path panel**
  Add API/types and a focused `PrerequisitePathPanel.vue`. Keep graph canvas unchanged. Show ordered prerequisite rows, one status per row, mastery, course-section link, targeted quiz link, and graph link.
- [x] **Step 4: Integrate graph/path mode and admin quality report**
  Add mode control to GraphView. Preserve existing Nebula entity search and graph layout. Add async admin quality loading with empty, loading, error, and issue states; do not add any edit/repair action.
- [x] **Step 5: Run frontend tests and build**
  Run: `npm --prefix frontend test` and `npm --prefix frontend run build`.

### Task 4: Verify integrated implementation

**Files:**
- Modify: `README.md`
- Modify: `docs/api.md`

- [x] **Step 1: Document the two read APIs and UI behavior**
- [x] **Step 2: Run the complete backend suite**
  Run: `mvn -f backend/pom.xml test` with JDK 21.
- [x] **Step 3: Run complete frontend tests and build**
  Run: `npm --prefix frontend test` and `npm --prefix frontend run build`.
- [x] **Step 4: Review the final diff and scope**
  Confirm no Python source, graph writes, dependency additions, or migrations were introduced.
