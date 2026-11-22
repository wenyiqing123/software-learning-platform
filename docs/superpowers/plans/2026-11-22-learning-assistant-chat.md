# 学习助手多会话对话功能 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有 `/tutor` 问答页升级为面向软件工程课程学习的多会话对话工作台，支持会话管理、连续上下文、SSE 流式状态、失败重试和课程知识上下文跳转。

**Architecture:** 复用当前 `ai_session`/`ai_message` 数据表和 `/api/ai/ask/stream` SSE 契约，不新增 WebSocket AI 通道。后端只补充会话重命名接口并继续以当前 JWT 用户做资源归属校验；前端把 `TutorView.vue` 拆为会话列表、消息列表和输入区三个职责单一的组件，由页面负责状态编排。

**Tech Stack:** Spring Boot 3、Spring Security、JdbcTemplate、Flyway、Vue 3、TypeScript、Vue Router、Vitest、现有 SSE 解码器和设计系统 CSS。

**Spec:** `docs/superpowers/specs/2026-11-22-learning-assistant-chat-design.md`

## Global Constraints

- 学习助手使用现有 SSE 流式问答，不新增 WebSocket AI 流协议。
- 只允许 `STUDENT` 访问学习助手会话接口和 `/tutor` 路由。
- 任何会话读、改、删、写消息操作都必须按 JWT 当前用户校验 `ai_session.user_id`。
- `DIRECT`、`TRIPLES`、`RANKED` 和 `COMPARE` 的现有请求/响应契约保持兼容。
- `RANKED` 后台固定证据约束系统提示词不得出现在浏览器、会话 metadata 或导出内容中。
- 图谱或 AI 依赖失败时只展示真实错误状态，不生成静态回答或假证据。
- 不修改外部 `Mecha-QA`、Python Bridge、NebulaGraph 图空间、模型权重或现有图数据。
- 不引入好友、群聊、红包、转账、视频通话或客服工单等示例项目社交功能。
- 不新增前端依赖，不引入 CSS `!important`。

## File Map

- Modify `backend/learning-service/src/main/java/edu/wfust/learning/platform/tutor/TutorSessionController.java`: expose `PATCH /api/tutor/sessions/{sessionId}`.
- Modify `backend/learning-service/src/main/java/edu/wfust/learning/platform/tutor/TutorSessionService.java`: add validated title rename operation.
- Modify `backend/learning-service/src/test/java/edu/wfust/learning/platform/tutor/TutorSessionServiceTest.java`: cover successful rename and cross-user rejection.
- Modify `frontend/src/api/tutor.ts`: add typed `renameSession` request and keep existing exports unchanged.
- Create `frontend/src/components/TutorConversationList.vue`: render session navigation and session actions.
- Create `frontend/src/components/TutorMessageList.vue`: render persisted messages, streaming status, errors, evidence and retry controls.
- Create `frontend/src/components/TutorComposer.vue`: render context chips, quick prompts, mode selector and send/stop controls.
- Modify `frontend/src/views/TutorView.vue`: coordinate child components, route context, SSE lifecycle and persistence.
- Modify `frontend/src/views/TutorView.test.ts`: cover the integrated conversation flow and route context.
- Create `frontend/src/components/TutorConversationList.test.ts`: cover session actions.
- Create `frontend/src/components/TutorMessageList.test.ts`: cover message rendering and retry event.
- Create `frontend/src/components/TutorComposer.test.ts`: cover quick prompt and send/stop events.
- Modify `docs/api.md`, `docs/ui-design.md`, `docs/test-report.md`, and `docs/development/continuation-ledger.md`: record the final API, UI and verification results.

## Task 1: Add the owned session rename API

**Files:**
- Modify: `backend/learning-service/src/main/java/edu/wfust/learning/platform/tutor/TutorSessionController.java`
- Modify: `backend/learning-service/src/main/java/edu/wfust/learning/platform/tutor/TutorSessionService.java`
- Test: `backend/learning-service/src/test/java/edu/wfust/learning/platform/tutor/TutorSessionServiceTest.java`

**Interfaces:**
- Consumes: existing `Authentication` username parsed as the current user id and existing `SessionView` record.
- Produces: `TutorSessionService.rename(long userId, long sessionId, String title): SessionView` and `PATCH /api/tutor/sessions/{sessionId}` returning `SessionView`.

- [ ] **Step 1: Add failing service tests for rename and ownership**

Add tests with the existing `JdbcTemplate` mock style:

```java
@Test
void renameUpdatesOnlyOwnedSession() {
    var renamed = service.rename(11, 7, "数据流图复习");

    assertThat(renamed.title()).isEqualTo("数据流图复习");
    verify(db).update("UPDATE ai_session SET title=? WHERE id=? AND user_id=?", "数据流图复习", 7L, 11L);
}

@Test
void renameRejectsBlankOrOversizedTitle() {
    assertThatThrownBy(() -> service.rename(11, 7, " "))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("会话标题");
}

@Test
void renameDoesNotRevealOtherUsersSession() {
    when(db.query(anyString(), any(RowMapper.class), eq(7L), eq(11L)))
        .thenReturn(List.of());

    assertThatThrownBy(() -> service.rename(11, 7, "其他会话"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting("statusCode.value")
        .isEqualTo(404);
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run from `backend/learning-service`:

```powershell
mvn -Dtest=TutorSessionServiceTest test
```

Expected: compilation/test failure because `rename` is not implemented.

- [ ] **Step 3: Implement the service operation**

Use the existing `owned(userId, sessionId)` helper before updating, trim the title, reject blank or over-120-character values with `400`, then execute:

```java
@Transactional
public SessionView rename(long userId, long sessionId, String title) {
    owned(userId, sessionId);
    String name = title == null ? "" : title.strip();
    if (name.isBlank() || name.length() > 120) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "会话标题长度必须为 1-120 个字符");
    }
    db.update("UPDATE ai_session SET title=? WHERE id=? AND user_id=?", name, sessionId, userId);
    return owned(userId, sessionId);
}
```

- [ ] **Step 4: Add the controller request DTO and PATCH mapping**

Add a validated record and mapping while keeping `@PreAuthorize("hasRole('STUDENT')")`:

```java
public record RenameSession(@NotBlank @Size(max = 120) String title) {}

@PatchMapping("/{sessionId}")
public SessionView rename(Authentication auth, @PathVariable long sessionId,
                          @Valid @RequestBody RenameSession body) {
    return sessions.rename(id(auth), sessionId, body.title());
}
```

- [ ] **Step 5: Run the focused test and commit**

Run:

```powershell
mvn -Dtest=TutorSessionServiceTest test
```

Expected: all tests in `TutorSessionServiceTest` pass. Commit:

```powershell
git add backend/learning-service/src/main/java/edu/wfust/learning/platform/tutor backend/learning-service/src/test/java/edu/wfust/learning/platform/tutor/TutorSessionServiceTest.java
git commit -m "feat: add owned tutor session rename"
```

## Task 2: Add typed frontend session operations

**Files:**
- Modify: `frontend/src/api/tutor.ts`
- Test: `frontend/src/api/tutor.test.ts` if the existing API test file is present; otherwise add `frontend/src/api/tutor.test.ts`.

**Interfaces:**
- Consumes: `PATCH /tutor/sessions/{id}`.
- Produces: `renameSession(id: number, title: string): Promise<Session>` with a stable `Session` type exported from the module.

- [ ] **Step 1: Add the failing API test**

Mock `api.patch` and assert the exact request:

```ts
it('renames a tutor session through the owned session endpoint', async () => {
  api.patch.mockResolvedValue({ data: { id: 7, title: '数据流图复习' } })
  await expect(renameSession(7, '数据流图复习')).resolves.toEqual({ id: 7, title: '数据流图复习' })
  expect(api.patch).toHaveBeenCalledWith('/tutor/sessions/7', { title: '数据流图复习' })
})
```

- [ ] **Step 2: Run the focused API test and verify it fails**

Run:

```powershell
npm test -- --run src/api/tutor.test.ts
```

Expected: failure because `renameSession` is not exported.

- [ ] **Step 3: Implement the typed API helper**

Define the shared `TutorSession` shape and add:

```ts
export const renameSession = (id: number, title: string) =>
  api.patch<TutorSession>('/tutor/sessions/' + id, { title }).then((r) => r.data)
```

Keep existing `sessions`, `createSession`, `sessionMessages`, `saveMessage`, `deleteSession`, and `streamAsk` exports compatible.

- [ ] **Step 4: Run the API test and commit**

Run `npm test -- --run src/api/tutor.test.ts`, expect all focused tests to pass, then commit:

```powershell
git add frontend/src/api/tutor.ts frontend/src/api/tutor.test.ts
git commit -m "feat: expose tutor session rename client"
```

## Task 3: Build the conversation list component

**Files:**
- Create: `frontend/src/components/TutorConversationList.vue`
- Create: `frontend/src/components/TutorConversationList.test.ts`

**Interfaces:**
- Consumes props `{ sessions: TutorSession[]; activeId: number | null; disabled?: boolean; error?: string }`.
- Produces events `select(id: number)`, `create()`, `rename(id: number, title: string)`, and `remove(id: number)`.

- [ ] **Step 1: Write component tests for rendering and actions**

Cover an empty state, active session, create button, rename submit, delete button and emitted values:

```ts
it('emits session actions without owning persistence', async () => {
  const wrapper = mount(TutorConversationList, { props: { sessions: [{ id: 7, title: '需求分析' }], activeId: 7 } })
  await wrapper.get('[data-testid="tutor-new-session"]').trigger('click')
  await wrapper.get('[data-testid="tutor-rename-7"]').trigger('click')
  await wrapper.get('[data-testid="tutor-rename-input"]').setValue('数据流图复习')
  await wrapper.get('[data-testid="tutor-rename-submit"]').trigger('click')
  await wrapper.get('[data-testid="tutor-delete-7"]').trigger('click')

  expect(wrapper.emitted('create')).toHaveLength(1)
  expect(wrapper.emitted('rename')?.[0]).toEqual([7, '数据流图复习'])
  expect(wrapper.emitted('remove')?.[0]).toEqual([7])
})
```

- [ ] **Step 2: Run the component test and verify it fails**

Run `npm test -- --run src/components/TutorConversationList.test.ts`; expected failure because the component does not exist.

- [ ] **Step 3: Implement the component**

Use the design-system classes, stable `data-testid` hooks from the test, keyboard-accessible buttons, and an inline rename form. The component must emit actions only; API calls stay in `TutorView.vue`.

- [ ] **Step 4: Run the component test and commit**

Run the focused test and commit:

```powershell
git add frontend/src/components/TutorConversationList.vue frontend/src/components/TutorConversationList.test.ts
git commit -m "feat: add tutor conversation list"
```

## Task 4: Build message and composer components

**Files:**
- Create: `frontend/src/components/TutorMessageList.vue`
- Create: `frontend/src/components/TutorMessageList.test.ts`
- Create: `frontend/src/components/TutorComposer.vue`
- Create: `frontend/src/components/TutorComposer.test.ts`

**Interfaces:**
- `TutorMessageList` consumes `{ messages; streamingAnswer; streamingEvidence; loading; error; retryQuestion? }` and emits `retry(question: string, mode: string)`.
- `TutorComposer` consumes `{ question; mode; compareAll; loading; context; quickPrompts }`, emits `update:question`, `select-mode`, `submit`, `stop`, and `quick-prompt`.

- [ ] **Step 1: Write message list tests**

Assert user and assistant content render, evidence is collapsed by default, streaming content is visible, and retry emits the original question after an error.

- [ ] **Step 2: Write composer tests**

Assert quick prompt click emits text, empty input disables submit, loading shows stop, and mode selection emits `DIRECT`, `TRIPLES`, or `RANKED`.

- [ ] **Step 3: Run both focused tests and verify they fail**

Run:

```powershell
npm test -- --run src/components/TutorMessageList.test.ts src/components/TutorComposer.test.ts
```

Expected: failure because the components do not exist.

- [ ] **Step 4: Implement message list**

Render persisted messages using `MarkdownContent`; render assistant metadata results with `TutorAnswerCard` without exposing `prompt`; use `FeedbackState` for empty/error/loading states; keep evidence data limited to returned entities/triples.

- [ ] **Step 5: Implement composer**

Reuse `TutorModeSelector`; keep textarea controlled via `v-model`; expose quick prompts and a single primary send action; show stop only while loading.

- [ ] **Step 6: Run focused tests and commit**

Run the two test files and commit:

```powershell
git add frontend/src/components/TutorMessageList.vue frontend/src/components/TutorMessageList.test.ts frontend/src/components/TutorComposer.vue frontend/src/components/TutorComposer.test.ts
git commit -m "feat: add tutor message and composer surfaces"
```

## Task 5: Integrate the conversation workbench and route context

**Files:**
- Modify: `frontend/src/views/TutorView.vue`
- Modify: `frontend/src/views/TutorView.test.ts`
- Inspect and modify only if needed: `frontend/src/domain/learningContext.ts`, `frontend/src/api/client.ts`

**Interfaces:**
- Consumes child component events and existing `learningApi` tutor methods.
- Produces the complete `/tutor` flow: session list, context chip, streaming state, persistence, retry, rename and delete.

- [ ] **Step 1: Extend integration tests before implementation**

Add tests that mount the page with mocked `learningApi` and assert:

```ts
expect(wrapper.text()).toContain('需求分析')
await wrapper.get('[data-testid="tutor-quick-prompt"]').trigger('click')
await wrapper.get('[data-testid="tutor-send"]').trigger('click')
expect(learningApi.saveMessage).toHaveBeenCalledWith(expect.any(Number), expect.objectContaining({ role: 'USER' }))
expect(wrapper.text()).toContain('正在整理课程知识')
```

Also cover `route.query.entity`, `route.query.courseId`, `route.query.sectionId`, and `route.query.knowledgePointId` being represented as context without automatically sending a request.

- [ ] **Step 2: Run the focused integration test and verify it fails**

Run `npm test -- --run src/views/TutorView.test.ts`; expected failure for the new hooks and child component output.

- [ ] **Step 3: Replace page-local session toolbar with the workbench layout**

Keep the existing mode and answer contracts. Move session action persistence into handlers:

```ts
async function renameCurrentSession(id: number, title: string) {
  const updated = await learningApi.renameSession(id, title)
  sessions.value = sessions.value.map((item) => item.id === id ? updated : item)
}
```

Use `TutorConversationList`, `TutorMessageList`, and `TutorComposer`; preserve `requestHint`, `streamAsk`, `compare`, `saveMessage`, `stopGenerating`, and existing history metadata behavior.

- [ ] **Step 4: Add route context extraction**

Create a small computed context object from `route.query` with string-only values. Display the context and use it only in the initial question/history request; do not add hidden prompt text to the UI or session metadata.

- [ ] **Step 5: Implement retry without duplicate user messages**

Retry must reuse the failed user question, clear the previous transient error, and call the same `submit` path with a flag that skips inserting another `USER` message when the original message already exists.

- [ ] **Step 6: Run the integration tests and commit**

Run:

```powershell
npm test -- --run src/views/TutorView.test.ts
```

Expected: all TutorView tests pass. Commit:

```powershell
git add frontend/src/views/TutorView.vue frontend/src/views/TutorView.test.ts frontend/src/api/tutor.ts frontend/src/components
git commit -m "feat: integrate multi-session learning assistant"
```

## Task 6: Update API/UI documentation and run full verification

**Files:**
- Modify: `docs/api.md`
- Modify: `docs/ui-design.md`
- Modify: `docs/test-report.md`
- Modify: `docs/development/continuation-ledger.md`

- [ ] **Step 1: Document the new PATCH endpoint and student-only behavior**

Add `PATCH /api/tutor/sessions/{id}` with request, response, `400`, `404`, `401/403` behavior; document SSE event states, retry behavior, and context query parameters without exposing internal prompt text.

- [ ] **Step 2: Document the visual workbench**

Record the conversation sidebar, message list, composer, evidence folding, empty/error states and narrow-screen behavior in `docs/ui-design.md`.

- [ ] **Step 3: Run backend focused and full tests**

Run from each backend module:

```powershell
mvn -Dtest=TutorSessionServiceTest test
mvn test
```

Expected: no failures and no skipped replacement of existing tests.

- [ ] **Step 4: Run frontend tests and build**

Run from `frontend`:

```powershell
npm test -- --run
npm run build
```

Expected: all test files pass and build exits 0 with no chunk warnings.

- [ ] **Step 5: Run repository hygiene and UI smoke checks**

Run:

```powershell
git diff --check
node scripts/authenticated-ui-smoke.mjs
```

Confirm `.ui-audit/` is removed after the smoke run and no CSS `!important` was added.

- [ ] **Step 6: Record results and commit documentation**

Update the actual test counts and smoke result, then commit:

```powershell
git add docs/api.md docs/ui-design.md docs/test-report.md docs/development/continuation-ledger.md
git commit -m "docs: close learning assistant chat verification"
```

## Final Acceptance Checklist

- [ ] Student can create, switch, rename, delete and restore a learning conversation.
- [ ] Consecutive questions carry bounded history and entity context.
- [ ] SSE status, evidence, delta, done and error states render correctly.
- [ ] Stop and retry do not create duplicate user messages or fake successful answers.
- [ ] Cross-user session rename/read/write/delete remains rejected.
- [ ] RANKED fixed system prompt is absent from frontend source, rendered UI and stored metadata.
- [ ] Frontend and all three backend module test suites pass.
- [ ] Production build and authenticated UI smoke pass.
- [ ] Working tree is clean after the final commit.
