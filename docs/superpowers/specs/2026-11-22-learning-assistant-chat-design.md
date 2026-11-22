# 学习助手多会话对话功能设计

## 1. 背景与目标

当前平台已经具备 `/tutor` 学习助教页面、`DIRECT`/`TRIPLES`/`RANKED` 三种问答模式、SSE 流式问答、个人会话持久化和图谱证据展示。本功能不重新建设一套通用社交聊天系统，而是参考 `server-websocket`/`web-websocket` 项目的会话列表、消息流转、会话管理和错误恢复体验，将现有能力整理成面向软件工程课程学习的连续对话工作台。

目标是让学生可以在一个稳定的学习会话中连续提问、查看上下文、切换问答策略、恢复历史、重试失败回答，并从课程/知识图谱页面带着知识点上下文进入助教。功能必须保持现有学习平台的角色边界、问答实验契约和学术合规边界。

## 2. 范围

### 2.1 本轮实现

- 学生专属的学习助手多会话工作台。
- 会话列表、新建、切换、重命名、删除和空会话引导。
- 用户消息、AI 消息、流式生成状态和失败重试的清晰展示。
- 复用现有 SSE 事件：`status`、`knowledge`、`delta`、`done`、`error`。
- 保留 `DIRECT`、`TRIPLES`、`RANKED` 与对比模式，不改变模式含义和接口契约。
- 继续持久化用户消息、助手消息、模式、三元组证据和回答元数据。
- 从课程、知识图谱、掌握图谱跳转到助教时保留 `courseId`、`sectionId`、`knowledgePointId`、`entity` 上下文。
- 会话重命名接口及当前用户归属校验。
- 前端交互、API、服务层和测试文档同步更新。

### 2.2 明确不做

- 不复制示例项目中的好友、群聊、红包、转账、人工客服、视频通话等社交功能。
- 不修改外部 `Mecha-QA`、Python Bridge、NebulaGraph 图空间或模型权重。
- 不新增一套与现有 SSE 并行的 WebSocket AI 流式协议。
- 不把 RANKED 后台固定证据约束系统提示词返回到浏览器、消息元数据或导出内容。
- 不使用静态图谱节点或伪造的模型回答填充离线/失败状态。

## 3. 关键决策

### 3.1 传输方式：继续使用 SSE

示例项目的 WebSocket 适合双向在线聊天、心跳和用户间消息投递；本平台的学习助手主要是浏览器发起问题、服务端单向流式生成回答。当前 `POST /api/ai/ask/stream` 已具备超时、取消、证据事件、增量文本和结构化结束事件，因此本功能继续使用 SSE。

这样可以避免新增 WebSocket 握手鉴权、网关升级、反向代理升级、心跳和连接状态维护，同时保持现有 AI 服务的证据校验与降级链路。WebSocket 不作为本轮功能依赖；将来如果需要实时课堂广播，可另行设计独立通道。

### 3.2 会话模型：复用 V5 数据表

继续使用 `ai_session` 和 `ai_message`：

- `ai_session.user_id` 是唯一归属边界。
- `ai_session.title` 保存会话标题。
- `ai_message.role` 只允许 `USER` 或 `ASSISTANT`。
- `ai_message.mode` 保存当前问答模式或 `COMPARE`。
- `ai_message.metadata_json` 保存前端所需的安全结果元数据，例如三元组、实体、延迟、错误码和提示层级。
- 后台固定系统提示词正文不进入 `metadata_json`；如已有响应包含 provenance，只保留版本/hash 等非敏感元数据。

### 3.3 会话标题

- 新会话默认标题使用首个用户问题的前 100 个字符。
- 用户可以手动重命名，标题最大 120 个字符，空白标题拒绝或回退为“新的学习会话”。
- 重命名接口必须按当前 JWT 用户校验 `session_id` 所有权，找不到或不属于当前用户统一返回 404，避免暴露资源存在性。

## 4. 用户流程

### 4.1 首次进入

1. 路由守卫确认当前用户为 `STUDENT`。
2. 加载当前用户最近 50 个会话。
3. 默认显示空状态和三个学习快捷问题。
4. 若 URL 带有课程/知识点查询参数，展示上下文标签，并预填面向该知识点的问题，但不自动发送请求。

### 4.2 发送问题

1. 学生选择 `DIRECT`、`TRIPLES` 或 `RANKED`，也可以启用三模式对比。
2. 若没有当前会话，先创建会话并使用问题生成标题。
3. 先保存 `USER` 消息；保存失败时不继续调用 AI，避免历史与回答脱节。
4. 调用现有 SSE 接口，传入最近 10 条消息和最近 20 个已识别实体作为上下文。
5. 根据事件显示“正在整理课程知识”“正在生成回答”、图谱证据数量和增量文本。
6. 收到 `done` 后保存 `ASSISTANT` 消息和安全结果元数据。
7. 自动滚动到最新消息；用户主动滚动查看历史时不强制抢回滚动位置。

### 4.3 停止、失败和重试

- 停止按钮调用现有 `AbortController`，显示“已停止生成”，不伪装为成功答案。
- `error` 事件只展示结构化错误码对应的用户提示，保留重试按钮。
- 重试使用原始用户问题和原模式，不重复插入用户消息；如上次已保存失败的助手占位消息，前端不重复保存。
- SSE 连接中断时保留已经收到的增量文本，并将当前回答标记为“未完成”，不能当作完整答案或写入有效答案指标。

## 5. 前端设计

### 5.1 组件拆分

- `TutorView.vue`：页面级编排、路由上下文、会话状态和问答提交。
- `TutorConversationList.vue`：会话列表、新建、选择、重命名、删除。
- `TutorMessageList.vue`：用户/助手消息、流式状态、证据折叠、重试入口。
- `TutorComposer.vue`：模式选择、快捷问题、输入框、停止/发送按钮。
- 继续复用 `TutorModeSelector.vue`、`TutorAnswerCard.vue`、`MarkdownContent.vue`、`FeedbackState.vue`。

### 5.2 视觉和交互约束

- 延续统一设计系统，不引入示例项目的旧版蓝色大卡片和社交聊天视觉。
- 左侧会话列表在桌面端固定宽度，窄屏变为可折叠抽屉或顶部选择器，不能产生横向滚动。
- 消息气泡区控制阅读宽度，Markdown、列表和代码块复用现有安全渲染器。
- 证据只展示真实接口返回的实体/三元组；图谱不可用时展示明确依赖故障态。
- 不展示 `RANKED` 后台固定证据约束提示词；“回答策略”只展示面向学生的模式说明。

## 6. API 与后端边界

### 6.1 保持兼容的接口

- `GET /api/tutor/sessions`
- `POST /api/tutor/sessions`
- `GET /api/tutor/sessions/{sessionId}/messages`
- `POST /api/tutor/sessions/{sessionId}/messages`
- `DELETE /api/tutor/sessions/{sessionId}`
- `POST /api/ai/ask/stream`
- `POST /api/ai/ask`
- `POST /api/ai/compare`

### 6.2 新增接口

`PATCH /api/tutor/sessions/{sessionId}`

请求：

```json
{ "title": "数据流图复习" }
```

响应：沿用 `SessionView`，返回更新后的 `id`、`title`、`createdAt`、`updatedAt`。

错误：

- `400`：标题为空或超过 120 个字符。
- `404`：会话不存在或不属于当前用户。
- `401/403`：未登录或非 `STUDENT`。

### 6.3 后端安全边界

- `TutorSessionController` 保持 `@PreAuthorize("hasRole('STUDENT')")`。
- 所有 session 读、改、删、写消息操作继续通过 `user_id` 归属查询。
- 不接受前端传入的 `userId` 作为资源归属依据。
- 消息 metadata 继续限制大小；不保存 token、API Key、系统提示词正文或原始模型内部思考内容。

## 7. 测试策略

### 7.1 前端

- 会话列表渲染、空态、新建、切换、重命名、删除。
- 学习上下文 query 参数被保留并展示。
- SSE `status`/`knowledge`/`delta`/`done`/`error` 状态正确映射。
- 停止生成、失败重试不重复保存消息。
- RANKED 页面源码和渲染文本不包含后台固定提示词正文。
- 桌面与窄屏下无横向溢出。

### 7.2 后端

- 重命名成功、非法标题拒绝、跨用户重命名返回 404。
- 现有 TutorSessionService 所有归属测试继续通过。
- SSE 契约和现有 AI 服务测试继续通过。
- 会话消息数量、metadata 大小和角色枚举边界继续受限。

### 7.3 回归门禁

- `frontend/npm test`
- `frontend/npm run build`
- `backend/ai-service/mvn test`
- `backend/learning-service/mvn test`
- `backend/learning-gateway/mvn test`
- `git diff --check`
- 现有双角色 `authenticated-ui-smoke.mjs`

## 8. 验收标准

1. 学生可以完成“新建会话 → 连续提问 → 流式回答 → 切换历史 → 重命名 → 删除”的完整闭环。
2. 课程、知识图谱和掌握图谱进入助教时，学习上下文在页面和请求链路中保持可见且不丢失。
3. AI 服务或图谱依赖失败时，页面展示真实错误状态，不生成静态回答或假证据。
4. 既有问答实验、RANKED 隐藏提示词、学生角色边界和会话 IDOR 防护均不回归。
5. 以上测试和构建全部通过，文档与实际接口保持一致。
