# 平台角色、体验与实用功能重构施工任务书

> **施工规则**：低档编码模型须使用 `superpowers:executing-plans` 按任务编号执行；每次仅执行一个原子任务，最多连续执行两个相邻任务。任务完成后运行该任务定向测试、检查差异，再继续。任务涉及文件数含测试文件，严格为 2–5 个。任务之间不得并行编辑共享路由、同一 API DTO、同一 CSS 或同一 Flyway 版本。

**目标**：在保留现有兼容路径、用户数据边界和学术红线的前提下，减轻学生导航认知负担，巩固管理员工作台，完成全站设计系统瘦身、管理员手选案例批量实验、分步评价、学习上下文和账号安全增强，并形成可复核交付。

**架构**：前端继续使用 Vue 3、Vue Router、Pinia、Element Plus、Lucide、Vitest 和 Playwright；后端继续使用 Spring Security/JWT、Spring MVC、MyBatis、Flyway 及现有 REST/SSE。按既有模块和 API 域实现，先写失败回归测试，再做最小实现；不得引入第二套 UI 框架或新的 AI 推理路径。

**基线**：源码基线提交 `f627371`。本轮只读运行 `authenticated-ui-smoke.mjs` 得 `158/158`（STUDENT 88、ADMIN 68、GUEST 2；1440×900 与 1280×800 各 79 项）；console/page errors、横向溢出、按钮挤压、默认控件、未样式 table 均为 0。四份全局 CSS `47,560 B`（design system `19,882 B`、shell `12,040 B`、graph `3,549 B`、learning `12,089 B`）；`!important` 命中 0。历史约 79.9 KB 非当前值。前端 27 views、19 components。Smoke 使用 API fixtures，只证明浏览器呈现/路由和 fixture 场景，不代表真实后端联调。本工作区只新增目标任务书；截图临时目录待交付前删除。

## 红线

1. 禁止修改外部 Mecha-QA、Python Knowledge Bridge、NebulaGraph 图空间/图数据、第三方模型权重或重新训练外部模型。
2. 图谱/Bridge/模型不可用时显示真实故障和可重试状态；禁止静态假图、假证据、伪造有效回答或把失败实验改为成功。
3. `RANKED` 后台固定证据约束系统提示词不得出现在浏览器 bundle/state、接口响应或 CSV/JSON 导出。只允许保留服务端已提供且安全的版本/hash 元数据；继续执行 `AiExperimentController.sanitizeResults`、`AiExperimentResponseGuardTest` 和 `frontend/tests/compliance-guard.test.ts`。
4. 推荐只可称为“可解释规则推荐”或“基于规则的建议”；不得写成“机器学习知识追踪”。
5. 认知负荷实验不采集生理信号，不预设或声称系统已降低认知负荷；仅记录知情同意、任务顺序、测验、服务端用时、Paas 心理努力自评和可选反馈。
6. 学生交卷前 `/api/quizzes` 和相应页面不含 `answer/correctAnswer/explanation`；答案只允许出现在服务端判题响应之后。不能用管理员题库 DTO 驱动学生题目列表。
7. 不硬编码或提交真实 API Key、密码、JWT/refresh token、个人身份数据或模型权重。测试凭据只用合成占位值；日志、导出不输出 bearer token。
8. 不改已执行的 Flyway V1–V15，不清空数据库或 migration history；新 schema 从 V16 递增。任何迁移必须在 H2/MySQL 兼容测试和专用 MySQL 空库验证。
9. 服务端身份只来自 JWT `Authentication` subject。Spring `hasRole('ADMIN')` 自动匹配 `ROLE_ADMIN`；不要写成 `hasRole('ROLE_ADMIN')`，也不得以 localStorage role 作为服务端授权。
10. 现有业务/REST/SSE 契约默认向后兼容；新增字段须可选或通过有版本的新增 endpoint，失败语义、所有权校验和匿名口径不得静默改变。
11. 不删减、不跳过既有测试。发现测试代用身份错误时改正其角色/账号数据，不能删除工作流或断言。
12. CSS 禁止强制优先级声明、禁止堆叠文件末尾覆盖补丁；优先改唯一源头 token/选择器。不得用内联 style 替代响应式规则。
13. API 拦截 Playwright 仅证明前端在给定 fixture 下的渲染，不等同真实全栈联调。报告必须清楚区分。

## 核实过的源码基线

### 路由与菜单

- `frontend/src/router.ts`：学生路由 `/dashboard`、`/courses`、`/knowledge`、`/mastery-map`、`/tutor`、`/practice`、`/adaptive-practice`、`/wrong-book`、`/review`、`/study-plan`、`/micro-lessons`、`/evaluation`、`/learning-report`、`/recommendations`、`/experiment-tasks`；ADMIN 路由 `/teaching-dashboard`、`/admin/content`、`/admin/experiments`、`/experiments`。`/` 与 `/login` 登录落点由 `homeForRole` 决定。
- 已有旧管理路由重定向：`/admin`、`/admin/teaching-dashboard`、`/admin/question-bank`、`/admin/micro-lessons`、`/admin/diagnostics`、`/admin/experiment-tasks`、`/admin/evaluations`。目标 Tab query 当前分别为 `course/question-bank/micro-lessons/diagnosis/tasks/evaluation`；重构时逐项测试。
- `frontend/src/App.vue` 目前学生导航是 3 组 15 项，管理员导航是 3 项。`AdminContentCenterView.vue` 已有 4 Tab，`AdminExperimentCenterView.vue` 已有 3 Tab。工作台必须沿用这两个页面和 query-key 兼容。

### 后端安全与 DTO

- `/api/auth` 当前只有 register/login/refresh/logout/me，`AuthService` 使用 BCrypt、refresh token hash 存储、单次轮换；当前无密码更新端点。`RefreshTokenMapper.findActive` 按 token hash 锁行。
- `SecurityConfig` 已启用 method security；JWT authority 加 `ROLE_`。课程阅读、Quiz、个人 dashboard、图谱、学习报告、推荐、复习、学习计划、实验任务 student controller、Tutor sessions、评价和笔记目前均有 `hasRole('STUDENT')`（个别服务内部为 `isAuthenticated`，调用入口仍由 controller 限制）。
- `AiExperimentController` 当前无 class-level role 限制，但用户界面 `/experiments` 是 ADMIN route；实验 run/history/export 均以当前 userId 过滤。M1 应按当前产品边界收紧为 ADMIN 并覆盖拒绝测试。
- 管理教学看板和评价 mapper 当前 SQL 已用 `sys_user.role='STUDENT'` 过滤；任务派发 service 已拒绝非 STUDENT。保留并增加回归护栏。
- `AdminStudyWorkflowTest` 用 H2 初始化 `admin-study-base.sql`，含管理员维护流程、学生课程/练习流程；不要移除此测试类。
- V15 `learning_evaluation_session` 已有 `task_order` 与 `assignment_id`；`experiment_group/ExperimentGroupEntity/GroupRequest` 当前无顺序，学生领取 DTO 被前端固定写成 `A→B`。分组顺序必须从服务端发放，计划用 V16 给 experiment group 加 nullable `task_order` 并给旧组回填确定的兼容默认值。
- `/api/experiments/run` 请求字段为 `question,includeAblations,referenceAnswer,expectedEntities,experimentType,taskId`；实验类型 `METHODS|QWEN_MATRIX`，消融是现有 4 条 ablation，QWEN_MATRIX 是 6 条 model/mode 条件。当前运行同步返回，并逐个结果保留 error；失败模式不可人工评分。
- 现有实验 CSV/JSON 导出有 BOM、CRLF、CSV quoting；JSON schema `experiment-export-v2`。`AiExperimentController.sanitizeResults` 去除 RANKED prompt body；批量导出必须复用字段语义并保持 schema 兼容。
- 当前页面的 admin 题库有课程、题目、选项、答案和解析编辑；错因诊断配置在 `/api/admin/questions/{id}/diagnostics`；内容中心含课程、题库、错因、微课四个 Tab。

### 当前视觉诊断证据

运行现有 `authenticated-ui-smoke.mjs` 于 1440×900 / 1280×800，双角色加 Guest 共 158/158；错误/溢出/裸控件均为 0。通过不代表所有层级都已精简。对临时 PNG 逐类审阅确认：

- `/admin/content?tab=course`：课程列表与右侧“新增课程”表单并列，但课程列表卡形成高而空的列；同页课程内容/题目维护又是另一个长表单区域，需做编辑态聚焦/一致间距，不能删功能。
- `/admin/content?tab=question-bank`：批量 JSON 文本区占据大面积，版本记录另占一张完整卡；需收紧导入器和版本摘要层级，保留上传、逐行错误、发布确认。
- `/admin/experiments`：实验配置、执行状态、结果网格、个人统计表和历史同时纵向堆叠，桌面 7 结果网格很长；需加批次/历史切换和结果快速定位，失败卡仍醒目。
- `/courses`：课程信息横条、目录卡、笔记卡、阅读主卡三列/嵌套感偏强；笔记编辑与已有笔记占左侧，阅读主体很长。优化为目录 + 正文的层级，笔记可收纳，不能丢最近上下文。
- `/tutor`：会话侧栏、策略说明、回答卡、证据、输入、分层提示纵向较长；使用中消息区应维持主视觉，策略/提示收折到二级区域。
- `/dashboard`：四个指标、三张下一步入口、知识路线与周活动、推荐 strip 同屏信息较多；后续让唯一主动作突出、弱化辅助路径，不删除个人数据或空/错误态。
- `/practice` 判题后卡片包含题干、选项、结果、错因区、侧边学习提示，层级长；保留提交前答案隐私、判题后对错差异和定向补救。
- `/teaching-dashboard` 空态两张图表与五项 KPI 在首屏留有较多下方空白；数据态继续呈现真实匿名统计，不造示例图。
- 本轮 fresh 1440×900/1280×800 截图逐页复核：`/courses` 在两种视口都保留“课程摘要 + 章节目录 + 笔记控制条 + 正文”连续首屏，笔记折叠后仍占 rail 高度，导致阅读正文可用宽度变窄；任务要求把笔记收成低占位 disclosure，而不是第三列。
- `/practice` 判题态在单屏同时显示四选项、结果卡、错因依据、侧栏学习提示和统计；布局没有溢出但纵向信息竞争，应在题卡内将判题结果/诊断作为一组可扫描的次层级，答案/解释仍只在服务端判题后出现。
- `/tutor` 空会话首屏消息区留白明显，静态策略卡和分层提示占据 composer 周边；应让输入/对话成为主工作区，将策略说明/分层提示折叠到辅助层，不能隐藏实际生成状态或证据。
- `/knowledge` 证据抽屉以右侧实体 panel 包裹次级浅底 triples，属于可接受的侧栏层级，但工具栏、相邻知识、证据和 2D/3D 操作竞争横向空间；应保留唯一画布主面、把证据行弱化为分隔清单，窄屏不盖住画布。
- `/dashboard` 4 个 KPI、3 个下一步入口、知识路径、周活动和推荐条都在首屏可见，层级清楚但行动入口数量偏多；保留数据，强调“继续阅读”单一首要动作，弱化其他快捷入口。
- `/admin/experiments` 两视口截图显示输入表单、7 张条件结果、个人统计、历史纵向连续堆叠，页面全长约 2,659px（1440 视口截图）；当前并排网格卡内指标/评分/证据密集，重点不是色彩问题而是同一页多种阅读任务没有分段。
- `/admin/content?tab=question-bank` 截图中“创建版本”表单字段只占卡片上部，却拉出高留白；JSON textarea 又占主要面积，底部版本历史独立成大卡。应收短创建区、限制 JSON editor 高度并将版本历史做紧凑列表，不删上传、逐行错误或发布确认。
- `/teaching-dashboard` 空数据显示为两个较高图表框下的空态，五个 KPI 与图表之间有大块视觉空白；应压缩空态图表高度，不生成假 series。
- `/admin/content` 的 Playwright 几何探针测到 `cardDepth=2`，实际对应内容中心嵌入式标题与子视图同用 H1、子表单也有重复白面；M2-T16/T17/T22 将标题降级并去重 surface，而不是通过隐藏功能降低深度。

### CSS 与幽灵类名扫描

- 当前四 CSS 有 22 组壳层独立 class 在 `styles-design-system.css` 与 `styles.css` 双处出现：`.app-shell,.sidebar,.brand,.course-switch,.course-switch-copy,.primary-nav,.nav-label,.nav-item,.nav-count,.sidebar-bottom,.sidebar-version,.main-area,.topbar-actions,.course-status,.help-quick,.user-menu,.page-content,.metric-grid,.dashboard-columns,.recommend-strip,.recommend-check,.help-box`。部分是 media query 专属声明，不应删除行为；应将壳层基础规则和各断点声明集中在 `styles.css`，design-system 留 token、Element Plus 桥接和跨页原语。
- `styles-graph.css` 当前 3,549 B，专管图谱；`styles-learning.css` 12,089 B，专管题目/复习/计划；`styles.css` 12,040 B 管壳层与核心原语；`styles-design-system.css` 19,882 B 混合主题、Element Plus、responsive 壳层和 admin 原语。任务按此所有权整理，不为了净瘦身删除图谱/学习功能。
- 静态 class 初扫 47 个 Vue 文件。literal-only heuristic 对照全局及同文件 scoped selector 得到 27 occurrences / 24 unique candidates；这是“没有任何 CSS literal selector 命中”的候选，不是已确认视觉 bug。候选：`admin-page,answer-result,compact-empty,course-study,diagnosis-admin-page,evaluation-history,experiment-task-admin-page,experiment-tasks-page,feedback-loading,graph-page,icon-button-small,legend-mastered,mastery-page,micro-lesson-admin-page,micro-lesson-page,mode-selector,notes-list,path-node-main,question-bank-page,role-notice,teaching-chart-activity,teaching-chart-weak,teaching-dashboard-page,tutor-page`。另 `admin-panel/question-columns` 由 AdminView scoped `:deep()` 规则覆盖。M2 执行时须用 Vue SFC parser 读 template/style，以 CSS selector parser 处理 `:deep` 和 scope，动态 class object 只提取静态 keys、未知表达式标为 dynamic-unknown；再逐项看 computed style 和 template 用途。真实缺样式才修，纯语义/test hook 写 allowlist，不盲加空规则或删 class。
- CI contract 已有唯一 root、Element primary/table token、全局禁止强制覆盖、tabular numerals 和 motion 检查（`frontend/tests/design-system-contract.test.ts`）。扩充它与检测脚本使跨文件重复只对确实冲突的 base declaration 报告，并检查 scoped style、动态 class 分支。

## 任务执行约定

- **任务数**：M1 9 项、M2 23 项、M3 25 项、M4 4 项，共 61 个编号唯一的原子任务。
- 下面每项的“涉及文件”是唯一可写路径，修改数严格为 2–5 个；`(Create)` 是明确的新路径。写为 read-only reference 的路径只能阅读、不得编辑且不计修改数。测试以新/修改源码为先 RED→GREEN；任务提到的非 involved tests 可以执行但不可修改。
- 路径均相对于仓库根目录。`npx` 命令均先 `Set-Location frontend`。Maven 命令在仓库根运行；使用 `-am` 并指定 test class 时带 `-Dsurefire.failIfNoSpecifiedTests=false`。
- 具体新增 endpoint/DTO 仅在对应任务定义后成立；未完成契约任务前，后续任务不得自行命名另一套字段。

## M1：角色分流与管理员架构

### - [x] M1-T1 学生五个一级导航入口
- **涉及文件**：`frontend/src/App.vue`；`frontend/src/AppRoleNavigation.test.ts`；`frontend/src/styles.css`
- **前置依赖**：无。
- **精确改造指令**：主侧栏学生态只呈现 5 个一级入口，分别导航到 `/dashboard,/knowledge,/practice,/study-plan,/experiment-tasks`；不再把 15 条子 route 全展开在一级列表。为每组配置固定子 route：概览与课程 `[/dashboard,/courses]`；图谱与助教 `[/knowledge,/mastery-map,/tutor,/micro-lessons]`；练习与复习 `[/practice,/adaptive-practice,/wrong-book,/review]`；计划与推荐 `[/study-plan,/recommendations]`；实验与评估 `[/experiment-tasks,/evaluation,/learning-report]`。保留 `activeNavPath` 对 `/experiments` 的 ADMIN 别名和所有路由，不删任何学生 route。管理员继续只看三项。
- **受影响既有测试与更新指引**：`AppRoleNavigation.test.ts` 保留管理员菜单非空/不含学生闭环断言；增加学生侧栏只出现五入口、所有学生 URL 均能从二级导航访问、ADMIN 不渲染学生入口、当前 route 所属 group 高亮。
- **单任务验证命令**：`cd frontend; npx vitest run src/AppRoleNavigation.test.ts`；预期零失败。

### - [x] M1-T2 学生上下文二级分段导航
- **涉及文件**：`frontend/src/App.vue`；`frontend/src/components/StudentLearningSections.vue`（Create）；`frontend/src/components/StudentLearningSections.test.ts`（Create）；`frontend/src/styles.css`
- **前置依赖**：M1-T1。
- **精确改造指令**：新组件接收 `groupId: string`、`path: string`、`query: Record<string, string | string[] | null | undefined>`，在 App topbar 下呈现当前 STUDENT 分组的 router-link 分段导航；分组由 `route.path` 计算，不加独立 route watcher。链接只使用 M1-T1 列出的 canonical route；课程链接只保留 `courseId/sectionId/knowledgePointId/entity`，评价/实验链接只保留 `taskId/assignmentId/conditionKey/taskKey/taskOrder`，不将不相关 query 传播到目标页。ADMIN 和 Guest 不渲染该分段栏。五组入口和 15 个学生 URL 均能到达；直接进入现有学生 URL 时按 path 显示所属分组。小屏为可键盘滚动的水平导航，active link 具备 `aria-current="page"` 与清晰焦点。
- **受影响既有测试与更新指引**：新增组件测试覆盖角色、path→group 映射、相关 query whitelist/不相关 query 排除及键盘语义；App 测试确认 ADMIN 无 student section strip。
- **单任务验证命令**：`cd frontend; npx vitest run src/components/StudentLearningSections.test.ts src/AppRoleNavigation.test.ts`；预期所有 query 值与 route 精确一致。

### - [x] M1-T3 兼容路由与角色门禁矩阵
- **涉及文件**：`frontend/src/router.ts`；`frontend/src/router-role.test.ts`
- **前置依赖**：M1-T1。
- **精确改造指令**：canonical route matrix：STUDENT-only 当前 15 routes，ADMIN-only 4 routes。现有 `resolveRole` 在 token 变更时必须向 `/api/auth/me` 验证真实 role；`slp-user-role` 仅为非权威 UI hint，不得作为 route authorization source。为避免每次导航重复网络请求，在内存按 access-token 缓存已解析 promise；token 改变/401/logout 时废弃缓存。参数化断言 STUDENT→4 admin canonical + 7 legacy 回 `/dashboard?notice=student`；ADMIN→15 student routes 回 `/teaching-dashboard?notice=admin`；`/`、登录页、无 token 分流；所有 legacy alias query 精确保留。所有新增路由声明 `meta.roles`。
- **受影响既有测试与更新指引**：替换/扩展已有三个 router 测试为参数化全矩阵，不删现有缓存 role 回退行为。
- **单任务验证命令**：`cd frontend; npx vitest run src/router-role.test.ts`；预期各矩阵样例逐项通过。

### - [x] M1-T4 管理工作台 Tab 与旧入口契约
- **涉及文件**：`frontend/src/router.ts`；`frontend/src/views/AdminContentCenterView.vue`；`frontend/src/views/AdminExperimentCenterView.vue`；`frontend/src/views/AdminCentersView.test.ts`
- **前置依赖**：M1-T3。
- **精确改造指令**：保留内容 Tab `course/question-bank/diagnosis/micro-lessons`、实验 Tab `comparison/tasks/evaluation` 和 router query；为每个 tab 加稳定 `data-testid`，确认 direct deep link 与刷新都恢复选中页。导航采用 `role=tablist/tab/aria-selected`，各中心只有一个顶层 H1，嵌入视图标题用次级 heading。`frontend/src/router.ts` 此任务实际修改的唯一新行为是补充“旧 ADMIN alias 和 `/experiments` 无 slash”保护用例；若所有 alias 已符合期望则不改 redirect 代码。旧 `/admin/*` redirect 不移动到平级按钮。未来若图谱核验入口新增，只允许只读且标记“只读检查”，不挂到学生个人记录。
- **受影响既有测试与更新指引**：保留 `AdminCentersView.test.ts` 两个切换测试，新增剩余五个 tab direct link、H1 数、ARIA 与 query refresh 断言。
- **单任务验证命令**：`cd frontend; npx vitest run src/views/AdminCentersView.test.ts`；预期七个 Tab 均唯一正确挂载。

### - [x] M1-T5 后端学生/管理员实验接口互斥
- **涉及文件**：`backend/learning-service/src/main/java/edu/wfust/learning/platform/experiment/AiExperimentController.java`；`backend/learning-service/src/main/java/edu/wfust/learning/platform/practice/AdaptivePracticeController.java`；`backend/learning-service/src/main/java/edu/wfust/learning/platform/microcourse/PublishedMicroLessonController.java`；`backend/learning-service/src/test/java/edu/wfust/learning/platform/security/RoleBoundaryMvcTest.java`（Create）
- **前置依赖**：M1-T3。
- **精确改造指令**：`AiExperimentController` 当前无 class-level role guard；为 `GET /api/experiments`、两种 export 与 `PUT /{id}/ratings` 加 ADMIN-only method guard。`POST /run` 暂保留现有登录访问，M3-T5 再明确 ADMIN personal / STUDENT assigned 分支；不要用 class-level ADMIN guard 把该学生窄化入口一并锁死。`AdaptivePracticeController` 与 `PublishedMicroLessonController` 增 class-level `hasRole('STUDENT')`。仅变更 role annotation，不改请求/响应。新 MVC test 用 MockMvc 验证 ADMIN 可读实验历史、STUDENT 403；STUDENT 可读自适应练习与已发布微课、ADMIN 403；拒绝请求不调用 service/client。使用 `.roles("ADMIN")`/`.roles("STUDENT")`，Spring 自动添加 `ROLE_`。
- **受影响既有测试与更新指引**：`AiExperimentResponseGuardTest` 不受 role mock 影响且必须保留；新增 MVC 测试。不因拒绝 `/api/experiments` 删除历史查询/导出测试。
- **单任务验证命令**：`mvn -f backend/pom.xml -pl learning-service -am -Dtest=RoleBoundaryMvcTest,AiExperimentResponseGuardTest -Dsurefire.failIfNoSpecifiedTests=false test`；预期 role allow/deny 矩阵通过，拒绝请求无 service 调用。

### - [x] M1-T6 管理工作流测试账号语义校准
- **涉及文件**：`backend/learning-service/src/test/java/edu/wfust/learning/platform/admin/AdminStudyWorkflowTest.java`；`backend/learning-service/src/test/java/edu/wfust/learning/platform/security/RoleBoundaryMvcTest.java`；`backend/learning-service/src/test/resources/admin-study-base.sql`（Read-only reference; do not modify）
- **前置依赖**：M1-T5。
- **精确改造指令**：只修改两个 Java 测试，不改 SQL fixture。核实 `admin-study-base.sql` 被 `AdminStudyWorkflowTest` setup 读取后，用同测试的 JdbcTemplate 插入独立 id=3 ADMIN；保留 seed 里的 STUDENT id=1/2。所有 `/api/admin/**` 写操作主体改用 `.user("3").roles("ADMIN")`；课程阅读/progress/知识点/测验/错题由 `.user("1").roles("STUDENT")`。集成测试新增 ADMIN GET `/api/courses/1/study` 与 PUT progress 均 403，STUDENT 只能写本人且题目 GET 不含答案字段。RoleBoundaryMvcTest 用同样角色 subject 约定覆盖 role guard，但不能重复调用数据库 fixture 或改生产代码。
- **受影响既有测试与更新指引**：保留 `AdminStudyWorkflowTest` 管理课程/题库/先修质量、跨课程进度、提交前答案隐私等测试；保留 M1-T5 新建的 `RoleBoundaryMvcTest`，只同步 role 主体，不删除断言。
- **单任务验证命令**：`mvn -f backend/pom.xml -pl learning-service -am -Dtest=AdminStudyWorkflowTest,RoleBoundaryMvcTest -Dsurefire.failIfNoSpecifiedTests=false test`；预期现有工作流和直接角色矩阵全部通过。

### - [x] M1-T7 教学统计排除 ADMIN 的 SQL 护栏
- **涉及文件**：`backend/learning-service/src/main/java/edu/wfust/learning/platform/mapper/TeachingDashboardMapper.java`；`backend/learning-service/src/test/java/edu/wfust/learning/platform/dashboard/TeachingDashboardMapperRoleTest.java`（Create）
- **前置依赖**：M1-T6。
- **精确改造指令**：现有 overview、dailyPractice、dailyReading、weakPoints 子查询已 join `sys_user.role='STUDENT'`，优先以测试锁住，而非无意义改写 SQL。新增 reflection test 读取 MyBatis `@Select` 的完整 SQL，检查所有读取个人 attempts/progress/mastery/wrong counts 的子句都有 STUDENT role join；`totalSections` 属课程静态总量，不应加入用户过滤。为 SERVICE 回归确认 aggregate DTO 不添加 username/userId 字段。发现漏 join 时只在该 SQL 子查询补角色 join。
- **受影响既有测试与更新指引**：保留既有 `TeachingDashboardServiceTest.returnsAnonymousAggregatesAndWeakPoints`（本任务不修改其文件）；新 mapper test 用 reflection 逐项锁定角色过滤，不新增重复统计 service 测试。
- **单任务验证命令**：`mvn -f backend/pom.xml -pl learning-service -am -Dtest=TeachingDashboardMapperRoleTest,TeachingDashboardServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`；预期所有个人计数 SQL 有 role 谓词，课程 section 总量语义不变。

### - [x] M1-T8 评价统计 STUDENT 样本护栏
- **涉及文件**：`backend/learning-service/src/main/java/edu/wfust/learning/platform/mapper/EvaluationMapper.java`；`backend/learning-service/src/test/java/edu/wfust/learning/platform/evaluation/EvaluationMapperRoleTest.java`（Create）
- **前置依赖**：M1-T7。
- **精确改造指令**：当前 summary 与 completedRows 已 join `sys_user.role='STUDENT'`。reflection test 覆盖两个 SQL 查询且检查没有 userId/username 出现在 public `SummaryView` 与 CSV header；detail 内部 userId 仅可供 `EvaluationStatistics.participantCode` 匿名编号算法使用。不得修改匿名编号、统计分组或样本口径。需求中的“管理员学生名单查看”在已核实当前 AdminController/EvaluationAdminController 中不存在任何 roster endpoint；本版不新增身份可见名单，ADMIN 只可看匿名聚合和既有实验派发。若产品后来要求名单访问，必须另开权限/合法性决策，不得把 userId 暴露在本任务里。
- **受影响既有测试与更新指引**：保留既有 `EvaluationServiceTest.summaryKeepsExperimentalConditionsSeparate`、`acceptsDelayedMeasurementAndExportsAnonymousParticipantCode`（不改该测试文件）；新 mapper test 覆盖 `summary` 与 `completedRows` SQL 都 join `sys_user.role='STUDENT'`。
- **单任务验证命令**：`mvn -f backend/pom.xml -pl learning-service -am -Dtest=EvaluationMapperRoleTest,EvaluationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`；预期 role predicate 和匿名导出断言通过。

### - [x] M1-T9 实验分组派发学生身份护栏
- **涉及文件**：`backend/learning-service/src/main/java/edu/wfust/learning/platform/experimenttask/ExperimentTaskService.java`；`backend/learning-service/src/main/java/edu/wfust/learning/platform/experimenttask/ExperimentTaskStudentController.java`；`backend/learning-service/src/test/java/edu/wfust/learning/platform/experimenttask/ExperimentTaskServiceTest.java`
- **前置依赖**：M1-T5。
- **精确改造指令**：在 `ExperimentTaskService.studentAssignments/startForStudent/bindRun/completeRun` 将 `isAuthenticated()` 改为 `hasRole('STUDENT')`；同步核验 `ExperimentTaskStudentController` 已有 class-level `hasRole('STUDENT')`，不得削弱。保留 assign 中 `SysUser.role == STUDENT` 校验和 owner 条件。service test 通过带 `SysUserMapper` 的构造器覆盖 ADMIN/不存在用户拒绝且 assignment mapper 零写、STUDENT 正常派发。仅校验身份边界；派发列表全有或全无和数量上限属于 M3-T3。
- **受影响既有测试与更新指引**：保留 `assignmentRejectsAdministratorAccounts`、`studentAssignmentProjectionDoesNotExposeAnotherIdentity`、`bindingARealRunStartsAssignmentAndCompletionRequiresServerRun`；只修正漏设 role 的 fixture，不删测试。
- **单任务验证命令**：`mvn -f backend/pom.xml -pl learning-service -am -Dtest=ExperimentTaskServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`；预期派发和 ownership 测试全绿。

### M1 收口门禁

| 项目 | 命令 | 通过标准 |
| --- | --- | --- |
| role/tab/router | `cd frontend; npx vitest run src/AppRoleNavigation.test.ts src/router-role.test.ts src/views/AdminCentersView.test.ts` | 学生/管理员互斥、登录落点、所有旧 URL/Tab query 正确 |
| learning-service 安全/统计 | `mvn -f backend/pom.xml -pl learning-service -am -Dtest=RoleBoundaryMvcTest,AdminStudyWorkflowTest,TeachingDashboardMapperRoleTest,TeachingDashboardServiceTest,EvaluationMapperRoleTest,EvaluationServiceTest,ExperimentTaskServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` | 真实 role guard 和 STUDENT-only aggregation 全绿 |
| 快照审计 | `git diff --check` | 无 whitespace error；不许删旧测试 |

M1 提交信息：`feat: tighten role boundaries and student navigation`

## M2：全站视觉体系与 CSS 净瘦身

### 精确设计规范（所有 M2 任务必守）

| Token | 精确值 | 用途 |
| --- | --- | --- |
| `--canvas` / `--surface-soft` | `#F7F8FA` | 主画布 |
| `--surface` | `#FFFFFF` | 单层主要内容表面 |
| `--surface-tint` | `#EEF2F3` | 次级分组、引用、工具条 |
| `--text` | `#172B27` | 标题/正文 |
| `--text-muted` | `#475B56` | 正文次级信息 |
| `--text-subtle` | `#52645F` | metadata |
| `--accent` / `--accent-strong` | `#0F665E` / `#0B514B` | 唯一品牌强调 |
| `--accent-soft` / `--border-focus` | `#E9F3F1` / `#2C7C72` | 激活底、focus |
| `--border-default` / `--border-soft` / `--border-strong` | `#DCE5E3` / `#E8EEEC` / `#849B95` | 结构边界 |
| `--info` / soft | `#355F75` / `#EDF4F7` | info only |
| `--warning` / soft | `#80591C` / `#FAF3E5` | warning only |
| `--danger` / soft | `#94493F` / `#FBEFED` | error only |
| `--info` surface text | `#355F75` | 与 `--info-soft` 搭配；semantic colors 只用于状态标签/图表编码，不作为第二品牌色 |
| `--canvas` | alias to `--surface-soft` (`#F7F8FA`) | 根应用画布 |
| `--focus-ring` | `0 0 0 3px rgb(15 102 94 / .20)` | 键盘 focus shadow，另保留 2px outline |
| `--shadow-xs/sm/md` | `0 1px 3px rgb(23 43 39 / .035)` / `0 3px 12px rgb(23 43 39 / .045)` / `0 8px 24px rgb(23 43 39 / .075)` | 细微表面层级；同层列表不得每项叠 shadow |
| success/error/info surfaces | existing accent/warning/danger/info soft tokens | 低饱和浅底 + 深字，不用大块纯色 |
| radius | control `8px`; card `12px`; pill `999px` | 全站统一 |
| spacing | `4,8,12,16,24,32px` | 组件内部至区块留白；不随 viewport 缩放 |
| typography | meta `12px`; body `14px`; reading `15px`; section `16px`; embedded H2 `18px`; page H1 `22–24px`; KPI `24px` | 层级固定 |
| motion | `150ms cubic-bezier(0.2, 0, 0, 1)` | hover/focus/active；遵守 reduced-motion |
| reading | `68–76ch`; line-height `1.65–1.75` | Course/Tutor/MicroLesson |

`--el-*` 映射必须在唯一 `:root` 完成，不直接覆盖 Element Plus vendor 文件。下列名字按 Element Plus 2.14.8 实际 CSS 展开；“组件局部变量”以组件 CSS 实际声明为准，不得把臆测变量当成可生效的 API。颜色 light steps 为避开繁复混色统一使用该 semantic 颜色与白色混合，`--el-color-*-light-9` 提供浅底，soft badge text 使用深色 semantic token。不得修改 `node_modules`：

| 实际读取变量 | 映射值 |
| --- | --- |
| Base palette/semantic | 定义所有 `--el-color-white/black`、primary/success/warning/danger/error/info 及每种 `-rgb/-dark-2/-light-3/-light-5/-light-7/-light-8/-light-9`；primary=`--accent`，success=`--accent-strong`，warning=`--warning`，danger/error=`--danger`，info=`--info`；各 RGB 对应 `15,102,94 / 11,81,75 / 128,89,28 / 148,73,63 / 148,73,63 / 53,95,117`；dark-2 用同语义深色；light-3/5/7/8 使用该语义色分别 80/60/40/25% + 白色；light-9 分别用 `--accent-soft/--warning-soft/--danger-soft/--danger-soft/--info-soft` | 不保留 Element 默认高饱和色阶 |
| Base font | `--el-font-family=var(--font-body)`；`--el-font-size-extra-large/large/medium/base/small/extra-small=20/18/16/14/13/12px`；`--el-font-weight-primary=600`；`--el-font-line-height-primary=1.6` | 统一中文字体、字号和组件字重 |
| Base canvas/text | `--el-bg-color/bg-color-page/bg-color-overlay=--surface/--surface-soft/--surface`；`--el-text-color-primary/regular/secondary/placeholder/disabled=--text/--text-muted/--text-subtle/--text-subtle/--text-subtle` | 页面、浮层和文字 |
| Base border/fill | `--el-border-color/color-light/color-lighter/color-extra-light/color-dark/color-darker/color-hover=--border-default/--border-soft/--border-soft/--surface-tint/--border-strong/--border-strong/--border-focus`；`--el-border-width=1px`、`--el-border-style=solid`、`--el-border=1px solid var(--border-default)`；`--el-fill-color/blank/light/lighter/extra-light/dark/darker=--surface-tint/--surface/--surface-tint/--surface-soft/--surface-soft/--border-soft/--border-default`；`--el-disabled-bg-color/disabled-border-color=--surface-soft/--border-soft` | 覆盖 base 实际定义的同名变量 |
| Base radius/size/shadow | `--el-border-radius-base/small/round/circle=8px/8px/999px/50%`；`--el-component-size-large/component-size/component-size-small=40px/40px/34px`；`--el-box-shadow/box-shadow-light/box-shadow-lighter/box-shadow-dark=--shadow-md/--shadow-sm/--shadow-xs/--shadow-md` | 圆角、控件尺寸与轻量浮层阴影 |
| Base motion/overlay/stack | `--el-transition-duration/fast=150ms`；`--el-transition-function-ease-in-out-bezier/fast-bezier=cubic-bezier(0.2,0,0,1)`；transition-all/border/box-shadow/color 分别 `all/border-color/box-shadow/color 150ms cubic-bezier(0.2,0,0,1)`；fade/fade-linear/md-fade 分别 `opacity 150ms cubic-bezier(0.2,0,0,1)` / `opacity 150ms linear` / `transform 150ms cubic-bezier(0.2,0,0,1),opacity 150ms cubic-bezier(0.2,0,0,1)`；index-normal/top/popper=`1/1000/2000`；svg monochrome grey=`--text-subtle`；overlay color/light/lighter=`rgb(23 43 39 / .38/.30/.16)`；mask/extra-light=`rgb(255 255 255 / .90/.30)` | 覆盖 Element Plus base 定义的 transition、z-index、overlay vars |
| Input/Textarea actual component vars | `--el-input-bg-color/text-color/placeholder-color/border-color=--surface/--text/--text-subtle/--border-default`；border=`1px solid var(--border-default)`；radius=`8px`；hover border=`--border-focus`；focus border=`--accent`；focus ring=`--focus-ring`；height/inner-height=`40px/38px`；width=`100%`；transparent border=`0 0 0 1px transparent inset`；clear-hover/icon=`--text-muted/--text-subtle` | 覆盖 `el-input.css` 与 `el-textarea.css` 实际变量，不另叠 wrapper 边框 |
| Select actual component vars | `--el-select-border-color-hover/disabled-border/disabled-color/input-color/input-focus-border-color/font-size/input-font-size/multiple-input-color/close-hover-color/width=--border-focus/--border-soft/--text-subtle/--text/--accent/14px/14px/--text/--danger/100%` | 使用实际 `el-select.css` variable names；不创建 `--el-select-input-focus-font-size` |
| Button actual component vars | 为 `bg/text/border/hover bg/hover text/hover border/active bg/active text/active border/active color/disabled bg/disabled text/disabled border/outline/divide/hover-link/font-weight/size` 全部定义对应 `--el-button-*`：default primary=`--accent/--surface/--accent`；hover/active=`--accent-strong/--surface/--accent-strong`；active-color=`--accent-strong`；disabled=`--surface-soft/--text-subtle/--border-soft`；outline=`--focus-ring`；divide=`--border-soft`；hover-link=`--accent-strong`；weight/size=`600/40px` | Plain/outline variants 用语义 light-9 + 深字；小型 native utility 另由应用 CSS 控制 |
| Checkbox actual component vars | bg=`--surface`；border-radius=`3px`；checked bg/border=`--accent`；checked icon=`--surface`；checked text=`--text`；disabled border/checked-border=`--border-soft`；disabled checked icon=`--text-subtle`；disabled checked fill/input fill=`--surface-soft`；disabled icon/text=`--text-subtle`；font-size/weight=`14px/600`；input border=`1px solid var(--border-default)`；hover border=`--border-focus`；input width/height=`16px`；checkbox height=`20px` | 对应 `el-checkbox.css` 全部 `--el-checkbox-*` definitions |
| Radio actual component vars | bg=`--surface`；input border=`1px solid var(--border-default)`；border color=`--border-default`；hover=`--border-focus`；radius=`50%`；width/height=`16px`；font-size=`14px`；font-weight=`600`；text=`--text-muted` | Element radio 没有 checked-color token；checked state 用组件 state class/`accent-color` |
| Table actual component vars | bg/tr=`--surface`；header bg/text=`--surface-tint/--text`；border=`--border-soft`；hover=`--surface-soft`；current row=`--accent-soft`；expanded=`--surface-soft`；text=`--text-muted`；fixed left/right=`--surface`；fixed shadow=`--shadow-xs`；index=`1` | Element table + 原生 table 另需 caption/scope semantics |
| Dialog actual component vars | bg=`--surface`；radius=`12px`；shadow=`--shadow-md`；title/content=`18px/14px`；line-height=`1.6`；padding=`24px`；top margin=`8vh`；width=`min(560px, calc(100vw - 32px))`；popup bg=`--surface`；modal bg/opacity=`rgb(23 43 39 / .38)/.38`；message close size=`16px` | 适配窄屏，不另建厚重面板层 |
| Tabs actual component vars | `--el-tabs-header-height=40px` | 激活颜色复用 primary token |
| Tag/Popper actual component vars | `--el-tag-bg-color/border-color/text-color/border-radius/border-radius-rounded/hover-color/font-size=--accent-soft/--border-focus/--accent-strong/8px/999px/--accent-strong/12px`；`--el-icon-size=14px`；popper border=`8px`；light/dark bg=`--surface/--text`；popover radius=`8px` | semantic tag 背景按对应 light-9 token |

Element Plus CSS 只通过实际消费的变量覆盖；不得给不存在的变量如 `--el-select-input-focus-font-size` 或 radio checked color token 赋值并声称已生效。native baseline scope 为 `.app-shell`、`.login-page` 及 teleported `.el-overlay`：普通按钮默认 neutral；项目已有 `.button-dark/.primary-button` 继续作为显式 primary，icon/text utility 按原尺寸；focus-visible 清晰，disabled 不可 hover。input/select/textarea 40px、`min-width:0;max-width:100%`、水平 padding 10px、单一 focus ring；checkbox/radio/range/file 排除尺寸和 appearance reset，只设 accent-color；文件 picker 不可隐藏唯一可访问触发器。table 100% width、border-collapse、tabular-nums、caption/thead、10–12px cell padding、行分隔/hover。Playwright 检查 computed style 且不得重边框覆盖 Element Plus wrapper。

### - [ ] M2-T1 全局壳 CSS 重复归并
- **涉及文件**：`frontend/src/styles-design-system.css`；`frontend/src/styles.css`；`frontend/tests/design-system-contract.test.ts`
- **前置依赖**：M1 全部门禁通过。
- **精确改造指令**：`styles.css` 是壳层唯一基础布局 owner；将 design-system 当前 responsive shell selectors `.app-shell,.sidebar,.brand,.course-switch,.course-switch-copy,.primary-nav,.nav-label,.nav-item,.nav-count,.sidebar-bottom,.sidebar-version,.main-area,.topbar-actions,.course-status,.help-quick,.user-menu,.page-content,.metric-grid,.dashboard-columns,.recommend-strip,.recommend-check,.help-box` 的 900px/620px 响应式声明合并到 `styles.css` 对应 base/媒体段；保留 mobile bottom nav、collapsed icon sidebar、active item visible 和 44px touch area。design-system 删除这些壳层 selector 副本和重复 `.course-switch:hover`，保留 token、Element Plus 与非壳共享原语。更新 contract 读取四文件并对 selector/property ownership 作断言，不把仅 media 的不同声明误报为冲突。
- **受影响既有测试与更新指引**：更新 `design-system-contract.test.ts`，仍要求单一 root、Element map、主标题 24px/设计系统移动字级、数字 token、motion 和零强制覆盖。
- **单任务验证命令**：`cd frontend; npx vitest run tests/design-system-contract.test.ts src/AppRoleNavigation.test.ts`；通过标准：测试全绿，移动/900px 规则仍存在且唯一归属 styles.css。

### - [ ] M2-T2 原生控件与 Element Plus 双向主题桥
- **涉及文件**：`frontend/src/main.ts`；`frontend/src/styles-design-system.css`；`frontend/tests/design-system-contract.test.ts`；`frontend/tests/compliance-guard.test.ts`
- **前置依赖**：M2-T1。
- **精确改造指令**：按本节逐字变量表补齐 Element Plus base 与组件变量。把 `el-input.css`、`el-select.css`、`el-table.css`、`el-dialog.css`、`el-checkbox.css`、`el-radio.css`、`el-tabs.css` 加入 `main.ts` vendor CSS import 区，顺序 ConfigProvider/Icon → vendor component CSS → 四项目 CSS；不得改 vendor 文件。原生规则 scope `.app-shell`，primary/neutral button utility 区分；input/select/textarea 高 40px、最大宽度不超过容器、水平 padding 10px、focus-visible 环只保留一套。原生 table 带 caption/header/scope、浅表头、cell padding、行分隔与 hover。checkbox/radio/range/file 排除 appearance/尺寸重置，仅指定 accent-color。保留 `.el-button` 组件规则，不在 `.el-input__wrapper` 叠加边框；语义色限 badge/图表状态，不做第二品牌色；尊重 reduced-motion。合规扫描覆盖管理页与新 suite copy，不加入渐变/Emoji/假图数据/越界认知负荷结论。
- **受影响既有测试与更新指引**：`design-system-contract.test.ts` 针对本表实际变量、native selector 和零强制优先级建断言；`compliance-guard.test.ts` 扩展新增 view/template copy 范围，保留原有禁词守卫。
- **单任务验证命令**：`cd frontend; npx vitest run tests/design-system-contract.test.ts tests/compliance-guard.test.ts`；预期所有 Element/native contracts 与合规扫描通过。

### - [ ] M2-T3 图谱 CSS 规则归属整理
- **涉及文件**：`frontend/src/styles-graph.css`；`frontend/src/styles-design-system.css`；`frontend/src/views/GraphView.vue`；`frontend/src/components/GraphEvidenceDrawer.vue`；`frontend/src/components/GraphEvidenceDrawer.test.ts`（Create）
- **前置依赖**：M2-T1、M2-T2。
- **精确改造指令**：`styles-graph.css` 只保留 graph canvas/toolbar/browser/viewport utility；将 Tutor/Practice/Mastery/Review 共用 `.spin/.spinner/@keyframes spin` 整体迁到 design-system，保留 class、尺寸和时长。GraphView 目前无 style block，`.graph-page{min-width:0}` 写在 `styles-graph.css`（本任务已有该文件）。`.legend-mastered` 复用 `.legend-dot` + `.mastery-mastered` 已有颜色，不重复定义；`.path-node-main` 是结构 marker，无须假造视觉规则，后续由 scanner allowlist 说明。离线仍保证 nodes/edges/evidence 空、真实 error/retry，不改 G6/Three data contract。
- **受影响既有测试与更新指引**：保留既有 `GraphView.test.ts`、`MasteryMapView.test.ts` 和 `VisualizationViewport.test.ts`；新增 `GraphEvidenceDrawer.test.ts` 检查真实 triples、空证据及 keyboard disclosure。
- **单任务验证命令**：`cd frontend; npx vitest run src/views/GraphView.test.ts src/views/MasteryMapView.test.ts src/components/GraphEvidenceDrawer.test.ts src/components/VisualizationViewport.test.ts`。

### - [ ] M2-T4 学习域 CSS 重复与空态统一
- **涉及文件**：`frontend/src/styles-learning.css`；`frontend/src/styles-design-system.css`；`frontend/tests/design-system-contract.test.ts`
- **前置依赖**：M2-T2。
- **精确改造指令**：`styles-learning.css` 只保留 quiz/answer/wrong/review/plan/recommend/experiment domain；通用 feedback/skeleton/table/control/notice 属 design system；`.feedback-state` 唯一 owner，spinner 已由 M2-T3 迁走。按每个重复 selector+property 源头合并，只删除被 AST 证明确实重复且无 distinct media/scoped behavior 的声明；保留 focus、判题 contrast、Paas/task 状态、responsive/reduced-motion。不得压成不可读单行。四份 CSS 合计目标 `<=43,000 B`，如果为达到目标必须破坏行为则保留规则并报告实际 bytes。
- **受影响既有测试与更新指引**：保留 `design-system-contract.test.ts` 单 root、tokens、motion、zero-important；更新其所有权 assertions 指向 design system/learning-domain owner。
- **单任务验证命令**：`cd frontend; npx vitest run tests/design-system-contract.test.ts src/views/PracticeView.test.ts src/views/WrongBookView.test.ts src/views/StudyPlanAndReviewView.test.ts`；先记录四份 CSS bytes，再比较合并后 bytes。

### - [ ] M2-T5 登录与学生总览层级
- **涉及文件**：`frontend/src/views/LoginView.vue`；`frontend/src/views/DashboardView.vue`；`frontend/src/views/LoginView.test.ts`；`frontend/src/views/DashboardView.test.ts`；`frontend/src/styles.css`
- **前置依赖**：M2-T2。
- **精确改造指令**：登录标题/表单是首屏主操作，保留无凭据提示；Dashboard 顺序为 4 个真实指标→继续上次学习唯一深色主操作→待复习/推荐次级→周活动/知识点。将头部“问问 AI 助教”从第二个深色主按钮改成 outline，保持 click handler。数值 tabular-nums；loading/error/retry/zero data 不混淆。1440x900/1280x800 真实 screenshot 核验首屏和按钮对比。
- **受影响既有测试与更新指引**：保留 Login 错误、注册/登录切换测试；Dashboard 指标接口、上下文 query 跳转、空数据测试均保留并补充按钮排序断言。
- **单任务验证命令**：`cd frontend; npx vitest run src/views/LoginView.test.ts src/views/DashboardView.test.ts`；预期所有业务触发与跳转不变。

### - [ ] M2-T6 教学总览 KPI 与图表层级
- **涉及文件**：`frontend/src/views/TeachingDashboardView.vue`；`frontend/src/components/TeachingDashboardCharts.vue`；`frontend/src/views/TeachingDashboardView.test.ts`；`frontend/src/styles.css`
- **前置依赖**：M2-T5。
- **精确改造指令**：KPI 只重排现有五字段，不改 DTO/统计口径：activeLearners/accuracy/completedReads/averageMastery 作为主数，openWrong 作为次数；不触碰独立 `MetricTile.vue`（其由 Dashboard 使用）。charts 保留真实空态，不加静态 axis 假数；图表高度 252px，宽度 `>850px` 两列、`<=850px` 单列。数值 tabular-nums；筛选 query/API 不变。不写因果效果。
- **受影响既有测试与更新指引**：保留 `TeachingDashboardView.test.ts` 的真实 DTO 到图表适配、course filter、空态 tests；新增 KPI 语义顺序和无数据不得显示 chart series。
- **单任务验证命令**：`cd frontend; npx vitest run src/views/TeachingDashboardView.test.ts src/domain/teachingDashboardCharts.test.ts`；预期真实 DTO mapping 不变。

### - [ ] M2-T7 课程阅读与笔记单层工作台
- **涉及文件**：`frontend/src/views/CourseView.vue`；`frontend/src/components/LearningNotesPanel.vue`；`frontend/src/components/LearningNotesPanel.test.ts`；`frontend/src/views/CourseView.test.ts`（Create）
- **前置依赖**：M2-T4。
- **精确改造指令**：保留目录、课程进度、当前 section、正文、已读状态、notes/bookmark 和 LearningContextActions。桌面布局只保留导航树/正文两个一级 surface，笔记作为可折叠次级面板而不是第三个白卡套白卡。正文 `max-width:72ch`、font 15px、line-height 1.7；大于 760px 固定目录可滚动，小屏目录收为顶部 disclosure。note 保存/编辑状态须可见，离开确认由 M3 hook 管理，不先在本任务增加 dirty behavior。
- **受影响既有测试与更新指引**：保留 LearningNotesPanel CRUD、收藏和 API errors；新增 CourseView context query/目录 hash/正文 width class/notes collapsed test。
- **单任务验证命令**：`cd frontend; npx vitest run src/components/LearningNotesPanel.test.ts src/views/CourseView.test.ts`；预期学习上下文参数逐项保持。

### - [ ] M2-T8 图谱及证据抽屉
- **涉及文件**：`frontend/src/views/GraphView.vue`；`frontend/src/views/MasteryMapView.vue`；`frontend/src/components/GraphEvidenceDrawer.vue`；`frontend/src/components/VisualizationViewport.vue`；`frontend/src/views/GraphView.test.ts`
- **前置依赖**：M2-T3、M2-T7。
- **精确改造指令**：toolbar 可见 action 固定高 40px、工具间距 8/12/16px；画布与 drawer 各为单层 surface，triples 是浅底行。状态图例文本与图标均有编码，窄屏证据内容流式排在画布之后。Graph offline 按真实 response availability/error 展示并保持 `nodes=[]/edges=[]/evidence=[]`；mastery 只显示账户 MySQL 统计，不展示伪 NebulaGraph triples。保持 G6/Three create/destroy 行为。当前 `GraphEvidenceDrawer.vue` 已存在，本任务不可重复创建 Drawer test 文件（若必须增加断言，只能改列出的既有 `GraphView.test.ts`，或把 test 新建作为本任务第 5 个 file 且减少任一非必要源码文件）。
- **受影响既有测试与更新指引**：保留 GraphView offline/evidence、MasteryMap real empty evidence、VisualizationViewport toolbar、3D nonblank screenshot tests；不要修改接口 fixture 伪造 online 数据。
- **单任务验证命令**：`cd frontend; npx vitest run src/views/GraphView.test.ts src/views/MasteryMapView.test.ts src/components/VisualizationViewport.test.ts`；若 `GraphEvidenceDrawer.test.ts` 已在仓库存在，再附加该文件；不得因测试不存在而引用 phantom path。

### - [ ] M2-T9 Three.js 与先修路径细节
- **涉及文件**：`frontend/src/components/ThreeGraphCanvas.vue`；`frontend/src/views/PrerequisitePathPanel.vue`；`frontend/src/views/PrerequisitePathPanel.test.ts`；`frontend/src/styles-graph.css`
- **前置依赖**：M2-T8。
- **精确改造指令**：保持 Three canvas 透明/背景、pointer drag、dispose 生命周期；工具栏 control 固定大小不因 hover 改 layout。先修节点用 mastered/in-progress/up-next/blocked 四语义色浅底深字，连接线以 `border-soft`，当前目标有非颜色指示。不能靠静态 fallback 节点显示 Bridge 离线。
- **受影响既有测试与更新指引**：保留 prerequisite ordering/cycle test；SMOKE 继续测 canvas 非空像素和拖动状态差，新增各节点状态及 320px 布局断言。
- **单任务验证命令**：`cd frontend; npx vitest run src/views/PrerequisitePathPanel.test.ts src/components/VisualizationViewport.test.ts; npm run build`；预期图谱 chunks 存在且无新增 chunk warning。

### - [ ] M2-T10 Tutor 对话工作台布局
- **涉及文件**：`frontend/src/views/TutorView.vue`；`frontend/src/components/TutorConversationList.vue`；`frontend/src/components/TutorMessageList.vue`；`frontend/src/views/TutorView.test.ts`
- **前置依赖**：M2-T8。
- **精确改造指令**：桌面保持会话列+消息列；策略说明折叠；会话列定高内部滚动。Composer 保持在消息列底部流中，不做 viewport fixed overlay；三种模式、compare、quick prompt、send/stop 各一处。保留实体/知识点 query，不自动提交；将 canonical knowledge-point ID 对应名称预填纳入 M3-T18，M2 只保留现有实体上下文行为。SSE delta/stop/retry/history-save error 用独立状态样式/文案。
- **受影响既有测试与更新指引**：仅保留并扩展 `TutorView.test.ts` 内 session/history/SSE/stop/compare/hint；它的依赖 mock 如需扩展，要在相同文件内改，不新增对不存在的 `TutorConversationList.test.ts` 等引用。覆盖窄屏单列和 prompt fill 不发请求。
- **单任务验证命令**：`cd frontend; npx vitest run src/views/TutorView.test.ts`。

### - [ ] M2-T11 Tutor Answer/Mode/Markdown 子组件
- **涉及文件**：`frontend/src/components/TutorAnswerCard.vue`；`frontend/src/components/TutorModeSelector.vue`；`frontend/src/components/MarkdownContent.vue`；`frontend/src/components/TutorAnswerCard.test.ts`；`frontend/src/components/MarkdownContent.test.ts`
- **前置依赖**：M2-T10。
- **精确改造指令**：三种模式改为水平 segmented control，button 高至少 44px、选中同时有边线/背景/aria；比较模式显式不同。回答 max-width 72ch, line-height 1.7；流式用小型状态 line，不闪烁。证据 triples 可折叠，每条 subject/predicate/object 对齐。Markdown 不执行 HTML；copy button 键盘操作、clipboard rejected feedback。RANKED 无 prompt body。
- **受影响既有测试与更新指引**：TutorAnswerCard 文案、Markdown raw HTML escaped、clipboard success/error、prompt guard tests 保留并补充键盘/窄屏。
- **单任务验证命令**：`cd frontend; npx vitest run src/components/TutorAnswerCard.test.ts src/components/MarkdownContent.test.ts src/components/TutorComposer.test.ts`。

### - [ ] M2-T12 课程练习与自适应练习
- **涉及文件**：`frontend/src/views/PracticeView.vue`；`frontend/src/components/PracticeQuestionCard.vue`；`frontend/src/views/AdaptivePracticeView.vue`；`frontend/src/views/PracticeView.test.ts`；`frontend/src/components/PracticeQuestionCard.test.ts`
- **前置依赖**：M2-T4。
- **精确改造指令**：单题作为唯一主 surface，题干 18px、选项整行可点最小 44px；selected/disabled/correct/incorrect 各使用文字+图标或浅色双编码，禁止降低判题后对比度。回答提交前不可从 DOM/API 泄露标准答案；诊断仅展示服务端配置，不自行推断。自适应列表以解释理由、到期状态、目标知识点可扫读，保持 query context。
- **受影响既有测试与更新指引**：保留 pre-submit answer privacy、submission result, diagnosis exact mapping tests；复用现有 `PracticeQuestionCard`/`AdaptivePracticeView` 测试并增加 keyboard/contrast 验收。
- **单任务验证命令**：`cd frontend; npx vitest run src/views/PracticeView.test.ts src/components/PracticeQuestionCard.test.ts src/views/AdaptivePracticeView.test.ts`。

### - [ ] M2-T13 错题本与间隔复习
- **涉及文件**：`frontend/src/views/WrongBookView.vue`；`frontend/src/views/ReviewView.vue`；`frontend/src/views/WrongBookView.test.ts`；`frontend/src/views/StudyPlanAndReviewView.test.ts`
- **前置依赖**：M2-T12。
- **精确改造指令**：错题列表搜索/知识点筛选在一条 toolbar；行内容题干、错误次数、状态、定向复习和已掌握动作分列，小屏操作换行但无横向页面溢出。Review 单题聚焦显示 recall choices、interval/next due 反馈；暂停/恢复/评分 requestKey 不改业务。错误诊断仍只展示配置证据。
- **受影响既有测试与更新指引**：保留搜索过滤、IDOR 的 UI 参数、mastered confirmation、review idempotency、interval output assertions。
- **单任务验证命令**：`cd frontend; npx vitest run src/views/WrongBookView.test.ts src/views/StudyPlanAndReviewView.test.ts`。

### - [ ] M2-T14 学习计划与规则推荐
- **涉及文件**：`frontend/src/views/StudyPlanView.vue`；`frontend/src/views/RecommendationsView.vue`；`frontend/src/views/RecommendationsView.test.ts`；`frontend/src/views/StudyPlanAndReviewView.test.ts`
- **前置依赖**：M2-T13。
- **精确改造指令**：计划预算/筛选作为浅底工具条，任务按序号、时长、类型、理由、状态排；complete/skip 只有当前合法状态可操作。推荐理由完整换行，不截断，规则类型与优先级次级显示，使用固定合规短语“可解释规则推荐”。
- **受影响既有测试与更新指引**：保留 rule reason text、空态、context links、plan completion source validation；加 1280/390px 无溢出断言。
- **单任务验证命令**：`cd frontend; npx vitest run src/views/RecommendationsView.test.ts src/views/StudyPlanAndReviewView.test.ts`。

### - [ ] M2-T15 报告与发布微课阅读
- **涉及文件**：`frontend/src/views/LearningReportView.vue`；`frontend/src/views/MicroLessonView.vue`；`frontend/src/views/LearningReportView.test.ts`；`frontend/src/views/MicroLessonView.test.ts`
- **前置依赖**：M2-T14。
- **精确改造指令**：报告用 KPI→活动趋势→章节/知识点表格顺序，数字 tabular-nums、CSV action 唯一主按钮；微课页限制 Markdown 72ch/1.7，证据列表和常见错因作浅底分组。不得把推荐表述为机器学习，不把历史统计说成实时因果结果。
- **受影响既有测试与更新指引**：保留 `LearningReportView.test.ts` report export columns/scope；`MicroLessonView.test.ts` 当前确实存在，保留 published-only/真实依赖 empty/error tests。
- **单任务验证命令**：`cd frontend; npx vitest run src/views/LearningReportView.test.ts src/views/MicroLessonView.test.ts`。

### - [ ] M2-T16 课程与基础题库管理视图
- **涉及文件**：`frontend/src/views/AdminView.vue`；`frontend/src/components/AdminCoursePanel.vue`；`frontend/src/components/AdminQuestionPanel.vue`；`frontend/src/views/AdminCourseViews.test.ts`
- **前置依赖**：M2-T1、M1-T4。
- **精确改造指令**：课程编辑/列表用两列 `minmax(0,1fr)`，内容贴顶，禁止固定高空容器；题编辑区与题列表分区。`AdminView` 新增 optional `embedded` prop，并传给 `SectionHeading :embedded`，使内容中心中它渲染 H2，standalone 仍 H1。保存/编辑/新建/停用事件不变。
- **受影响既有测试与更新指引**：保留 `AdminCourseViews.test.ts` 创建/编辑题/课程/启停/质量报告；管理员题库显示答案属于预期，不能错误增加 ADMIN 不可见答案断言。center tab tests 作为相邻任务已覆盖。
- **单任务验证命令**：`cd frontend; npx vitest run src/views/AdminCourseViews.test.ts`。

### - [ ] M2-T17 错因诊断与版本题库视图
- **涉及文件**：`frontend/src/views/AdminDiagnosisView.vue`；`frontend/src/views/QuestionBankView.vue`；`frontend/src/views/AdminDiagnosisView.test.ts`；`frontend/src/views/QuestionBankView.test.ts`
- **前置依赖**：M2-T16。
- **精确改造指令**：Diagnosis toolbar 单行；draft mapping 用 divider row，未配置时明确提示。Diagnosis `SectionHeading` 传 embedded prop。QuestionBank toolbar 合并 course/version；编辑区限制 editor 可视高度且可内部滚动，版本 history 改紧凑分隔列表。QuestionBank standalone heading 改为 `embedded ? h2 : h1` 并响应 dynamic prop。文件 picker 保留可读键盘入口。不得改 diagnostics/import API。
- **受影响既有测试与更新指引**：保留错因 map save/error/empty 和题库逐行导入/发布确认/JSON 大小限制 tests；更新 design-system contract 禁止全局强制覆盖。
- **单任务验证命令**：`cd frontend; npx vitest run src/views/AdminDiagnosisView.test.ts src/views/QuestionBankView.test.ts tests/design-system-contract.test.ts`。

### - [ ] M2-T18 微课与实验任务管理员
- **涉及文件**：`frontend/src/views/MicroLessonAdminView.vue`；`frontend/src/views/ExperimentTaskAdminView.vue`；`frontend/src/views/MicroLessonAdminView.test.ts`；`frontend/src/views/ExperimentTaskAdminView.test.ts`
- **前置依赖**：M2-T16。
- **精确改造指令**：MicroLesson toolbar/source/draft history 用 8/16/24 spacing；draft list 只一层 surface，记录以 divider 分隔；`MicroLessonAdminView` 与 `ExperimentTaskAdminView` 新增 optional `embedded` 并将页头 H1 降为嵌入态 H2；generate/publish/status 操作保留现有确认和 request。不要在 M2 前增加 group/assign controls（它们由 M3-T4 实现）。
- **受影响既有测试与更新指引**：保留 MicroLesson generate/publish、task create/status confirmation；当前 `ExperimentTaskAdminView` 未调用 group/assign endpoints，不能虚构其已支持 UI；新 taskOrder 字段在 M3 加入后 UI test 对应更新。
- **单任务验证命令**：`cd frontend; npx vitest run src/views/MicroLessonAdminView.test.ts src/views/ExperimentTaskAdminView.test.ts`。

### - [ ] M2-T19 学生评估向导视觉与任务入口
- **涉及文件**：`frontend/src/views/EvaluationView.vue`；`frontend/src/views/ExperimentTasksView.vue`；`frontend/src/views/EvaluationView.test.ts`；`frontend/src/views/ExperimentTasksView.test.ts`
- **前置依赖**：M2-T4、M1-T9。
- **精确改造指令**：本任务只定义后续 M3 流程的视觉 slots，不变更后台顺序 contract：评估序列 stepper 固定知情说明→前测→任务/计时→后测→Paas→延后测验；不可达 step 灰态并有文字。Paas 1–9 保持等宽横排桌面、窄屏 3 列、可键盘选择、每格至少 44px；selected 有编号/文字辅助。`ExperimentTasksView` task question 仅来自 assigned DTO，不暴露管理员列表/身份。
- **受影响既有测试与更新指引**：保留“不采集任何生理信号”、Paas anchors、consent, assignment query, DELAYED measure tests；M3 再加 wizard progression tests。
- **单任务验证命令**：`cd frontend; npx vitest run src/views/EvaluationView.test.ts src/views/ExperimentTasksView.test.ts`。

### - [ ] M2-T20 管理统计表与匿名导出视图
- **涉及文件**：`frontend/src/views/EvaluationAdminView.vue`；`frontend/src/views/EvaluationAdminView.test.ts`；`frontend/src/styles-learning.css`
- **前置依赖**：M2-T2、M2-T19。
- **精确改造指令**：描述统计用原生语义 table，桌面不横滚，小屏仅 table region 横滚；表头含准确列名/单位并使用 `scope=col` 与 caption。所有指标 tabular-nums。`EvaluationAdminView` 接收 `embedded` 并使用 SectionHeading H2，Admin 页只展示匿名汇总/匿名 CSV，不输出 userId/name/原始回答，不写因果结论。
- **受影响既有测试与更新指引**：保留匿名聚合、SD/IQR、筛选、blob download tests；增加 table caption/scope 和不含 identity 的 DOM/export tests。
- **单任务验证命令**：`cd frontend; npx vitest run src/views/EvaluationAdminView.test.ts src/views/AdminCentersView.test.ts`。

### - [ ] M2-T21 实验结果网格和结果历史
- **涉及文件**：`frontend/src/views/ExperimentsView.vue`；`frontend/src/components/ExperimentResultGrid.vue`；`frontend/src/components/ExperimentResultGrid.test.ts`；`frontend/src/views/ExperimentsView.test.ts`
- **前置依赖**：M2-T2、M2-T19。
- **精确改造指令**：将实验输入、结果、统计、历史分为 query/tab 分段，默认输入/最近结果，不删除个人汇总与历史；七条件可见状态但不占用全部视口高度。结果卡用 `subgrid` 或等价稳定 grid rows，失败卡不显示指标/人工评分；成功答复、证据、评分分别分段。CSV/JSON 按既有按钮导出，RANKED prompt 继续隐藏，布局新状态不创建虚假的进度结论。
- **受影响既有测试与更新指引**：保留 failed mode, RANKED prompt guard, manual rating, history, evaluation metrics tests；增加 tabs aria/query, failed no rating, 7 conditions single scroll owner。
- **单任务验证命令**：`cd frontend; npx vitest run src/components/ExperimentResultGrid.test.ts src/views/ExperimentsView.test.ts`；保留现有单次运行与 server save 语义，不改 backend API。截图/metrics 验证历史和统计移出首屏主阅读区。

### - [ ] M2-T22 两个管理员工作台密度与可访问性
- **涉及文件**：`frontend/src/views/AdminContentCenterView.vue`；`frontend/src/views/AdminExperimentCenterView.vue`；`frontend/src/views/AdminCentersView.test.ts`；`frontend/src/components/SectionHeading.vue`
- **前置依赖**：M2-T16、M2-T17、M2-T18、M2-T20、M2-T21。
- **精确改造指令**：每个中心一个 H1 和一句描述，tablist 无额外重复 banner；子内容嵌入标题为 H2/H3；active tab 与 route query 保持同步并在 viewport 可见。内容中心四 Tab、实验中心三 Tab 的 tab 数量和 query key 不变。用浅底分隔，不新增白卡套白卡框。
- **受影响既有测试与更新指引**：扩展 `AdminCentersView.test.ts` 检查 heading level、tab ARIA、导航后深链刷新。另保留 admin role redirect tests。
- **单任务验证命令**：`cd frontend; npx vitest run src/views/AdminCentersView.test.ts src/AppRoleNavigation.test.ts`。

### - [ ] M2-T23 AST 级幽灵类名验收工具
- **涉及文件**：`frontend/package.json`；`frontend/package-lock.json`；`frontend/scripts/scan-vue-classes.mjs`（Create）；`frontend/tests/design-system-contract.test.ts`
- **前置依赖**：M2-T4、M2-T22。
- **精确改造指令**：将锁文件现有版本直接提升为根 `devDependencies`：`@babel/parser@7.29.13`、`css-tree@3.2.1`、`postcss@8.5.32`，并同步 package-lock，不升级任何其他依赖。scanner 用 Vue SFC `parse/compileTemplate` 读取 App/views/components；用 Babel `parseExpression` 识别 `:class` 的静态对象 key 和数组 literal，其他表达式列入 dynamicUnknown；用 PostCSS 读取 scoped+4 全局 CSS，用 css-tree AST 解析 selector 和 `:deep`，正确处理逗号 selector、class/attribute/pseudo。输出 JSON `{missingClasses,dynamicUnknown,allowlisted}`，只有用户元素真实缺样式规则计 missing；semantic marker/data-testid/test hook 必须在源码 allowlist 中附 file/class/reason，不用空规则消音。`--check` 在 missingClasses 非空时 exit 1；不报告 dynamicUnknown 为 failure。Contract test 通过 `spawnSync(process.execPath,['scripts/scan-vue-classes.mjs','--check'])` 执行，不把 scanner 逻辑重复写进测试。
- **受影响既有测试与更新指引**：扩展现有 `design-system-contract.test.ts` 调用 scanner 并断言 exit 0、genuine missing=0；保留其 token/zero-important contracts。新包只显式声明 lock 已有精确版本，不升级 lock 里的 transitive package。
- **单任务验证命令**：`cd frontend; npm install --package-lock-only --ignore-scripts --offline --no-audit; node scripts/scan-vue-classes.mjs --check; npx vitest run tests/design-system-contract.test.ts`；不得联网更新非上述三个依赖，最终 scanner missing count 必须为 0。

### M2 收口门禁

| 项目 | 命令 | 通过标准 |
| --- | --- | --- |
| 全前端测试 | `cd frontend; npm test` | 所有既有 + 新增 Vitest 通过，compliance/design-system guards 在内 |
| 编译/拆包 | `cd frontend; npm run build` | vue-tsc 与 Vite 0 error，0 chunk warning |
| 真实前端截图巡检 | `cd frontend; node scripts/authenticated-ui-smoke.mjs` | 1440×900/1280×800，双角色+Guest；0 console/page error、0 horizontal overflow、0 squeezed button、0 default controls/table |
| CSS 卫生 | `rg -n -F 'important' frontend/src --glob '*.css' --glob '*.vue'` 并人工确认命中均为零；计算四 CSS bytes | 项目 CSS/Vue 命中必须为零；四 CSS 合计 `<=43,000 B`。若无损清重复规则仍无法达到，保留合法 scoped/component 规则并记录实际 bytes/原因；不使用旧 45,000 B 备用阈值 |

M2 提交信息：`style: unify visual system across student and admin views`

## M3：批量实验、向导、上下文与账号安全

### - [ ] M3-T1 V16 为实验组持久化顺序
- **涉及文件**：`backend/learning-service/src/main/resources/db/migration/V16__experiment_group_task_order.sql`（Create）；`backend/learning-service/src/main/java/edu/wfust/learning/platform/entity/ExperimentGroupEntity.java`；`backend/learning-service/src/test/java/edu/wfust/learning/platform/experimenttask/ExperimentTaskOrderMigrationTest.java`（Create）
- **前置依赖**：M1 全部门禁通过。
- **精确改造指令**：追加 V16 为 `experiment_group` 加 nullable `task_order VARCHAR(3)` 保存 wire values `A→B`/`B→A`。迁移后所有旧 group 继续 NULL，不回填默认顺序、不声称随机化；不新增索引除非 query 实际需要，不写个人或图数据。M3-T3 才校验新的非空 group order。
- **受影响既有测试与更新指引**：新 `ExperimentTaskOrderMigrationTest` 从 V14 schema 开始、加载 V15/V16 Flyway migrations，断言旧 group 顺序为 NULL，新列可读写；不要改 `AdminStudyWorkflowTest` 作为迁移验证，那个 fixture 自己关闭 Flyway。
- **单任务验证命令**：`mvn -f backend/pom.xml -pl learning-service -am -Dtest=ExperimentTaskOrderMigrationTest -Dsurefire.failIfNoSpecifiedTests=false test`；预期从 V14 开始的 MySQL-compatible H2 迁移通过并保留旧行。

### - [ ] M3-T2 Assignment DTO 返回服务器顺序
- **涉及文件**：`backend/learning-service/src/main/java/edu/wfust/learning/platform/mapper/ExperimentAssignmentMapper.java`；`backend/learning-service/src/main/java/edu/wfust/learning/platform/experimenttask/ExperimentTaskService.java`；`backend/learning-service/src/test/java/edu/wfust/learning/platform/experimenttask/ExperimentTaskServiceTest.java`
- **前置依赖**：M3-T1。
- **精确改造指令**：`ExperimentAssignmentMapper.findForUser` join `experiment_group` select `g.task_order AS taskOrder`；`AssignmentRow/StudentAssignmentView` 带 nullable `taskOrder`。学生 public DTO 和 SQL projection 删除 `userId/referenceAnswer`，只保留题干、状态、group/condition 和时间；admin export header 不变。
- **受影响既有测试与更新指引**：保留 `studentAssignmentProjectionDoesNotExposeAnotherIdentity`、`bindingARealRunStartsAssignmentAndCompletionRequiresServerRun`；扩展 projection 断言无 `userId/referenceAnswer` 并包含 server `taskOrder`。
- **单任务验证命令**：`mvn -f backend/pom.xml -pl learning-service -am -Dtest=ExperimentTaskServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`。

### - [ ] M3-T3 管理端组读取与派发原子性
- **涉及文件**：`backend/learning-service/src/main/java/edu/wfust/learning/platform/experimenttask/ExperimentTaskController.java`；`backend/learning-service/src/main/java/edu/wfust/learning/platform/experimenttask/ExperimentTaskService.java`；`backend/learning-service/src/test/java/edu/wfust/learning/platform/experimenttask/ExperimentTaskServiceTest.java`
- **前置依赖**：M3-T1、M3-T2。
- **精确改造指令**：新增 ADMIN-only `GET /api/admin/experiment-tasks/{id}/groups`；保留既有 `POST /{id}/groups`，新 group request 必须含 `taskOrder: 'A→B'|'B→A'`，空/其他值 400；既有 NULL group 不改。`POST /{id}/assign` 校验 1–500 个正整数 ID、无重复；先验证 task/group 归属和全部用户存在且 role=STUDENT，再写；任一 invalid 则整批 0 insert；valid users 用同一 transaction + `INSERT IGNORE` 幂等派发。禁止新增学生名册/PII endpoint，不称随机化。
- **受影响既有测试与更新指引**：扩展 ExperimentTaskServiceTest：group owner/not found、混合 STUDENT+ADMIN 全批零写入、无效/重复/501 IDs、合法多个 STUDENT、幂等重复派发。任何拒绝断言 mapper 零写。
- **单任务验证命令**：`mvn -f backend/pom.xml -pl learning-service -am -Dtest=ExperimentTaskServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`；预期无部分派发。

### - [ ] M3-T4 管理端分组/顺序与学生 ID 派发 UI
- **涉及文件**：`frontend/src/api/admin.ts`；`frontend/src/api/types.ts`；`frontend/src/views/ExperimentTaskAdminView.vue`；`frontend/src/views/ExperimentTaskAdminView.test.ts`
- **前置依赖**：M3-T3。
- **精确改造指令**：新增 `ExperimentGroup` type 和 `listExperimentGroups/createExperimentGroup/assignExperimentGroup` helpers。Admin row 展示真实 group；新建 group 必选 `A→B`/`B→A` 并在 task description 写清 A/B 表示的 protocol steps，不称随机。ID 输入按逗号/换行解析 1–500 unique positive integers。确认框说明 task/group/order/count；不请求/展示学生 PII；400 保留原输入，成功 refresh groups/count。状态顺序 DRAFT→配组→派发→OPEN。
- **受影响既有测试与更新指引**：已有 `ExperimentTaskAdminView.test.ts`，不新建；扩展 group GET/POST、order values、ID normalize/reject、confirm cancel 0 requests、400 retain input、success reload。
- **单任务验证命令**：`cd frontend; npx vitest run src/views/ExperimentTaskAdminView.test.ts`。

### - [ ] M3-T5 指派学生实验的服务端 Rubric 与答案隔离
- **涉及文件**：`backend/learning-service/src/main/java/edu/wfust/learning/platform/experiment/AiExperimentController.java`；`backend/learning-service/src/main/java/edu/wfust/learning/platform/experimenttask/ExperimentTaskService.java`；`backend/learning-service/src/test/java/edu/wfust/learning/platform/experiment/AiExperimentResponseGuardTest.java`；`backend/learning-service/src/test/java/edu/wfust/learning/platform/experiment/AiExperimentControllerTest.java`（Create）；`backend/learning-service/src/test/java/edu/wfust/learning/platform/experiment/ExperimentExportTest.java`
- **前置依赖**：M1-T5、M3-T2、M3-T3。
- **精确改造指令**：StudentAssignment DTO 不含 `userId/referenceAnswer/expectedEntities`。从 assignment/task server data 建内部 `AssignedRunSpec(assignmentId,taskId,question,referenceAnswer,expectedEntities,experimentType,includeAblations)`，不可作为 JSON response 返回。`RunRequest.question` 保留 max 2000 但移除 record-level `@NotBlank`，controller 分支校验：ADMIN 的 personal run 必须 question 非空，保持旧自由输入/reference 语义；STUDENT 必须 `taskId` 且有 owner assignment+OPEN task，忽略 request question/reference/entities/type/ablations，按 server spec 运行。Student request 可不传 question，role-specific response `evaluation` 删除 rubric 字段；DB 可存内部指标评估输入。GET `/api/experiments`、CSV/JSON exports、ratings 为 ADMIN-only，POST run 仍按角色分支。失败结果不能有 answer/metrics/rating。此次不改 assignment completion（M3-T9/T10 负责）。
- **受影响既有测试与更新指引**：`ExperimentTaskServiceTest` + `AiExperimentResponseGuardTest` 断言学生投影/response 不含 userId/reference rubric/system prompt；新 `AiExperimentControllerTest` 覆盖 STUDENT 无 taskId、伪造问题/rubric 被忽略、合法 owner assignment、ADMIN 原自由模式、student list/export/ratings 403；更新既有 `ExperimentExportTest` 断言不能导出 rubric/identity。
- **单任务验证命令**：`mvn -f backend/pom.xml -pl learning-service -am -Dtest=AiExperimentControllerTest,AiExperimentResponseGuardTest,ExperimentTaskServiceTest,ExperimentExportTest -Dsurefire.failIfNoSpecifiedTests=false test`。

### - [ ] M3-T6 学生分配任务受限实验运行页
- **涉及文件**：`frontend/src/router.ts`；`frontend/src/views/EvaluationView.vue`；`frontend/src/views/EvaluationView.test.ts`；`frontend/src/views/ExperimentsView.vue`；`frontend/src/views/ExperimentsView.test.ts`
- **前置依赖**：M3-T5。
- **精确改造指令**：新增 STUDENT-only `/experiment-run` 指向 `ExperimentsView` `studentTaskMode=true`；保留 ADMIN `/experiments`。EvaluationView link 改目标并保留 `taskId/assignmentId`。学生模式 GET owner-scoped assignments、校验 ID 匹配、只读显示题干，隐藏 reference/entities/type/history/export/summary/rating/free editor。学生点击运行用现有 `api.post('/experiments/run',{taskId})`，不发送 question/rubric；后端从 assignment 取题目。失败照实呈现可重试；完成状态等 EvaluationService 最终完成才刷新，不以 run response 提前结单。ADMIN compare 行为不变。
- **受影响既有测试与更新指引**：更新 EvaluationView test link query；在其同一文件 import real router，断言新 route roles=STUDENT 且 ADMIN cross-visit 重定向。ExperimentsView test 覆盖 student mode 隐藏 rubric/editor/history/export/summary/rating、body exact `{taskId}`、未分配/403 显真实错误。
- **单任务验证命令**：`cd frontend; npx vitest run src/views/EvaluationView.test.ts src/views/ExperimentsView.test.ts`。

### - [ ] M3-T7 评估顺序以 assignment group 为权威
- **涉及文件**：`backend/learning-service/src/main/java/edu/wfust/learning/platform/mapper/ExperimentAssignmentMapper.java`；`backend/learning-service/src/main/java/edu/wfust/learning/platform/evaluation/EvaluationService.java`；`backend/learning-service/src/main/java/edu/wfust/learning/platform/experimenttask/ExperimentTaskService.java`；`backend/learning-service/src/test/java/edu/wfust/learning/platform/evaluation/EvaluationServiceTest.java`
- **前置依赖**：M3-T2、M3-T3、M3-T4、M3-T5、M3-T6。
- **精确改造指令**：新增 mapper query 以 assignmentId+JWT userId join `experiment_group/task` 返回 order/status。`taskOrder` wire values 只允许 `A→B`/`B→A`；它们表示该管理员任务描述中明示的两个 protocol step 顺序，不内置“图谱/直答”含义、不称随机分配。`EvaluationService.start` assignment-linked 时 server order 唯一权威；legacy NULL 或客户端发送非匹配 order 时拒绝 409/400，不猜默认值；assignment 不存在/非本人 404、非 OPEN 或 completed 409。非 assignment session 保留明确填入的 order。
- **受影响既有测试与更新指引**：保留 `rejectsEvaluationStartForAnotherUsersAssignment`；覆盖 server 两种顺序、client tampering、closed task、nonowner/completed assignment。SessionView taskOrder 必须是持久化服务端顺序。
- **单任务验证命令**：`mvn -f backend/pom.xml -pl learning-service -am -Dtest=EvaluationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`。

### - [ ] M3-T8 服务端权威用时
- **涉及文件**：`backend/learning-service/src/main/java/edu/wfust/learning/platform/evaluation/EvaluationService.java`；`backend/learning-service/src/test/java/edu/wfust/learning/platform/evaluation/EvaluationServiceTest.java`
- **前置依赖**：M3-T7。
- **精确改造指令**：不改 schema/controller。Assignment-linked elapsed 起点用 `experiment_assignment.started_at`（首次真实 run bind 时 server 写入）；unassigned session 用 `learning_evaluation_session.started_at`。`EvaluationService.start` insert 后用 `findSession(userId,id)` 读回 DB timestamp。现有 request `elapsedSeconds` 字段继续 required 以保持旧接口契约，但 service 完成时忽略其值；用 `Duration.between(serverStart,now(clock))` 算 1–86400 秒，缺起点/越界 400。Owner+session WHERE 保持，不重算历史。
- **受影响既有测试与更新指引**：扩展 `EvaluationServiceTest` fixed Clock：assignment server start 已过 120 秒且客户端传 1 时存 120；unassigned 用 session.startedAt；覆盖 >24h、起点 null 与 owner 404。保留既有 CompleteRequest 必传字段签名。
- **单任务验证命令**：`mvn -f backend/pom.xml -pl learning-service -am -Dtest=EvaluationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`。

### - [ ] M3-T9 问答运行不得提前完成实验分配
- **涉及文件**：`backend/learning-service/src/main/java/edu/wfust/learning/platform/experiment/AiExperimentController.java`；`backend/learning-service/src/main/java/edu/wfust/learning/platform/experimenttask/ExperimentTaskService.java`；`backend/learning-service/src/test/java/edu/wfust/learning/platform/experimenttask/ExperimentTaskServiceTest.java`；`backend/learning-service/src/test/java/edu/wfust/learning/platform/experiment/AiExperimentControllerTest.java`
- **前置依赖**：M3-T5。
- **精确改造指令**：移除当前 run 成功后调用 `completeRun` 的副作用；`bindRun` 只将 ASSIGNED 改为 IN_PROGRESS；任意实验 run success/failure 都不完成 assignment。只有 M3-T10 EvaluationService 在评估条件都满足后调用 owner-scoped completion。不得接受浏览器提供的成功标志；不新增表/mapper。
- **受影响既有测试与更新指引**：更新 `ExperimentTaskServiceTest.bindingARealRunStartsAssignmentAndCompletionRequiresServerRun` 为 binding 后 IN_PROGRESS。扩展由 M3-T5 创建的 `AiExperimentControllerTest`，分别运行成功和失败结果，断言 assignment 都仍为 IN_PROGRESS 且 controller 不调用 `completeRun`；保留 owner/security tests，不移除现有断言。
- **单任务验证命令**：`mvn -f backend/pom.xml -pl learning-service -am -Dtest=ExperimentTaskServiceTest,AiExperimentResponseGuardTest -Dsurefire.failIfNoSpecifiedTests=false test`。

### - [ ] M3-T10 提交评估后才完成 assignment
- **涉及文件**：`backend/learning-service/src/main/java/edu/wfust/learning/platform/evaluation/EvaluationService.java`；`backend/learning-service/src/main/java/edu/wfust/learning/platform/mapper/EvaluationMapper.java`；`backend/learning-service/src/test/java/edu/wfust/learning/platform/evaluation/EvaluationServiceTest.java`；`backend/learning-service/src/test/java/edu/wfust/learning/platform/evaluation/EvaluationMapperProgressIntegrationTest.java`（Create）
- **前置依赖**：M3-T8、M3-T9。
- **精确改造指令**：新增 owner-scoped `EvaluationMapper.hasMeasurement(userId,sessionId,phase)` 与 `listOwnedPhaseStatuses(userId)`。后者一条 SQL 返回 owned session 的 sessionId 和 PRE/POST/DELAYED phase；`mine()` 以两次查询组装 `SessionView.completedPhases`，取值只能是 phase 子集、不对别的 user 返回行。Assignment-linked complete 必须有 PRE+POST+effort 1–9+同一 owner assignment 的已持久化成功 run；通过现有 `AiExperimentMapper.selectList` 按 user_id+assignment_id 读 internal results JSON 并检查 error 状态，不暴露给 response/export。缺条件 409，不完成 evaluation/assignment；全部通过才在一 transaction 写 evaluation 与调用现有 owner-scoped `completeRun(true)`。无 assignment session 维持原契约。
- **受影响既有测试与更新指引**：扩展 `EvaluationServiceTest` 覆盖 unassigned compatibility、assignment 缺 PRE/POST/run、non-owner、completedPhases restore 和完成。创建 `EvaluationMapperProgressIntegrationTest` 使用 H2 MySQL mode、最小 session/measurement schema、两个 user，断言 query 只返回 owner 行并正确聚合 3 phase；已有 `ExperimentTaskServiceTest` owner guard 只运行不修改。
- **单任务验证命令**：`mvn -f backend/pom.xml -pl learning-service -am -Dtest=EvaluationServiceTest,EvaluationMapperProgressIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`。

### - [ ] M3-T11 服务端固定实验案例目录
- **涉及文件**：`backend/learning-service/src/main/java/edu/wfust/learning/platform/experiment/FixedExperimentSuite.java`（Create）；`backend/learning-service/src/main/java/edu/wfust/learning/platform/experiment/FixedExperimentSuiteController.java`（Create）；`backend/learning-service/src/test/java/edu/wfust/learning/platform/experiment/FixedExperimentSuiteTest.java`（Create）
- **前置依赖**：M2-T21。
- **精确改造指令**：新增 ADMIN-only `GET /api/admin/experiments/fixed-suite`，仅返回 `{suiteVersion,cases:[{caseId,question,sourceSection,knowledgePoint}]}`；不要包含 `answer/correctAnswer/explanation/referenceAnswer/expectedEntities/prompt/model output/credentials`。8 个 case 必须逐字复用 `V4__course_content_question_bank.sql` 当前 seed：

| caseId | sourceSection | knowledgePoint | question |
| --- | --- | --- | --- |
| `REQ_ACCEPTANCE` | 从真实问题到可验证的需求 | 需求分析 | 下列哪条需求最容易通过明确的验收测试验证？ |
| `DFD_BALANCE` | 用数据流图解释业务数据如何变换 | 数据流图DFD | 将 DFD 中的一个加工分解为子图时，应优先检查什么？ |
| `UML_ACTOR` | 围绕参与者目标组织 UML 用例 | 用例图 | 在软件系统的 UML 用例图中，“支付平台”可以作为参与者的条件是什么？ |
| `SRS_TRACE` | 把需求规格变成设计与验收的共同依据 | 软件需求规格说明书 | 为需求 REQ-BOOK-03 建立到设计项与测试用例的对应关系，主要有什么作用？ |
| `DFD_STORE` | 从逻辑数据集合走向一致的数据设计 | 数据存储 | DFD 中的“订单记录”数据存储表示什么？ |
| `LIFE_INCREMENT` | 按风险与反馈安排软件生命周期活动 | 软件生命周期 | 团队先交付航班查询，随后加入预订和退款功能，这主要体现什么？ |
| `MAINT_ADAPT` | 让交付后的改变保持可控 | 软件维护 | 为兼容支付平台新版本协议而修改接口，主要属于哪类维护？ |
| `ACCEPTANCE_SCENARIOS` | 贯穿需求、建模与验证的机票预订案例 | 机票预定系统 | 对“创建机票预订订单”进行验收时，哪些场景值得覆盖？ |

这些题目只用于问答实验，不改变 `/api/quizzes` 响应；因为仓库没有教师审核过的参考答案 corpus，此 manifest 绝不派生/回传答案 key，后续 ROUGE/BLEU/entity-reference metrics 为空，不能把无参考指标写为 0 分或成功证据。
- **受影响既有测试与更新指引**：新增固定目录测试检查 8 个唯一 ID 与 V4 seed 的题干/section/knowledgePoint 一致、ADMIN 200/STUDENT 403、没有任一答案/解析/rubric/prompt 字段。保留 `ExperimentRequestTest`、`AiExperimentResponseGuardTest`。
- **单任务验证命令**：`mvn -f backend/pom.xml -pl learning-service -am -Dtest=FixedExperimentSuiteTest,ExperimentRequestTest,AiExperimentResponseGuardTest -Dsurefire.failIfNoSpecifiedTests=false test`。

### - [ ] M3-T12 前端加载并选择固定实验案例
- **涉及文件**：`frontend/src/api/experiment.ts`；`frontend/src/api/types.ts`；`frontend/src/views/ExperimentsView.vue`；`frontend/src/views/ExperimentsView.test.ts`
- **前置依赖**：M3-T11、M3-T5。
- **精确改造指令**：新增 typed `FixedExperimentSuite/Case` 与 `getFixedExperimentSuite()` helper；ADMIN `ExperimentsView` 载入 8 个 manifest cases，默认全选、可取消选择，展示 question/source/section/name。固定 case 不可编辑 question，不显示答案/reference 文字框；若允许 admin 对批次单独填写评价 target，应另标为“运行后人工对照数据”并不能成为服务端审核证据，本任务不实现。studentTaskMode 隐藏案例目录与选择器。加载失败保留明确 error/retry，不能回落到虚构案例。
- **受影响既有测试与更新指引**：保留现有单题实验输入与历史 tests；新增 manifest 8 项显示、默认 selected、deselect、API error retry、无 answer 字段、student hidden。
- **单任务验证命令**：`cd frontend; npx vitest run src/views/ExperimentsView.test.ts`。

### - [ ] M3-T13 固定案例串行批量运行
- **涉及文件**：`frontend/src/views/ExperimentsView.vue`；`frontend/src/views/ExperimentsView.test.ts`
- **前置依赖**：M3-T12。
- **精确改造指令**：将所选 snapshot 按顺序串行调用既有 `/api/experiments/run`，request 只发 case question 与已存在接口所需普通参数；reference/expectedEntities 留空，未生成 reference 指标。状态 `PENDING/RUNNING/SUCCESS/FAILED`；失败继续、单项可 retry、已成功不可重复运行。显示 done/selected/failed/current。停止或离页只取消未发项，已经 server-save 的 run 保留。进度是 browser in-memory，不声称 server queue/跨 refresh 恢复；不把 failed 条件伪装成有效 answer。
- **受影响既有测试与更新指引**：保留 current single run/history/error/rating tests；新测 queue order/count/error continue/retry-only-failed/success-once/cancel-unsent/student mode hidden。
- **单任务验证命令**：`cd frontend; npx vitest run src/views/ExperimentsView.test.ts`。

### - [ ] M3-T14 批量结果 UTF-8 CSV/JSON 导出
- **涉及文件**：`frontend/src/domain/experimentExport.ts`；`frontend/src/domain/experimentExport.test.ts`；`frontend/src/views/ExperimentsView.vue`；`frontend/src/views/ExperimentsView.test.ts`
- **前置依赖**：M3-T13。
- **精确改造指令**：CSV 保持 UTF-8 BOM/CRLF/RFC4180 quoting/既有列顺序，可在末尾追加 `suiteVersion,caseId,sourceSection,batchId,batchStatus`；JSON 保持 `experiment-export-v2` 并增加可选 `batch{suiteVersion,batchId,total,successCount,failedCount}`。失败行 answer/metrics/rating 空、error 保留。JSON 必须按字段白名单重建，不得 spread server run/response；不导出答案 key、reference/rubric、student PII 或 system prompt body；RANKED 只保留既有安全 version/hash metadata。无 teacher-reviewed reference 的指标须为空值，不是 0。
- **受影响既有测试与更新指引**：保留 BOM/header/escaping/schema/prompt omission tests；新增 partial failure/Unicode/旧格式兼容/no rubric/no PII/no reference score tests。
- **单任务验证命令**：`cd frontend; npx vitest run src/domain/experimentExport.test.ts src/views/ExperimentsView.test.ts`。

### - [ ] M3-T15 学生领取任务读取真实分组顺序
- **涉及文件**：`frontend/src/api/types.ts`；`frontend/src/views/ExperimentTasksView.vue`；`frontend/src/views/ExperimentTasksView.test.ts`
- **前置依赖**：M3-T2、M3-T4、M3-T7。
- **精确改造指令**：`ExperimentAssignment.taskOrder?: 'A→B'|'B→A'`；DTO 的 userId/referenceAnswer 已在 M3-T2 移除。ExperimentTasksView order label 取 server 值；legacy NULL 显“顺序未记录”并禁用开始 assessment，不默认 A→B。当前 click 仅跳到 Evaluation consent page，不在 consent 前 POST `/start`；assignment.startedAt 应在真正 run 发生时开始。保留 taskId/assignmentId query。
- **受影响既有测试与更新指引**：更新 ExperimentTasksView.test 固定 B→A 响应并 assert navigation；增加缺值错误。
- **单任务验证命令**：`cd frontend; npx vitest run src/views/ExperimentTasksView.test.ts src/views/EvaluationView.test.ts`。

### - [ ] M3-T16 评估分步向导状态机
- **涉及文件**：`frontend/src/views/EvaluationView.vue`；`frontend/src/views/EvaluationView.test.ts`；`frontend/src/views/ExperimentTasksView.vue`；`frontend/src/views/ExperimentTasksView.test.ts`
- **前置依赖**：M3-T8、M3-T10、M3-T12。
- **精确改造指令**：流程 consent→PRE→assigned run→POST→Paas 1–9→optional DELAYED。consent 前不创建 evaluation session；PRE/POST 使用当前 measurement endpoint。Assignment timer 在首个 run 被 server bind 时开始，elapsed 用 M3-T8 server calculation。完成要求 PRE/POST/successful run/effort；DELAYED 独立保存。使用 M3-T10 `completedPhases` 从 server 恢复已保存步骤；本地未提交输入不得标 complete。API error 保持当前 step 并可 retry。
- **受影响既有测试与更新指引**：保留 no physiology wording/consent/order/DELAYED tests；新增 step blocking、resume、server time、complete gating。
- **单任务验证命令**：`cd frontend; npx vitest run src/views/EvaluationView.test.ts src/views/ExperimentTasksView.test.ts`。

### - [ ] M3-T17 跨页学习上下文回链
- **涉及文件**：`frontend/src/domain/learningContext.ts`；`frontend/src/domain/learningContext.test.ts`；`frontend/src/components/LearningContextActions.vue`；`frontend/src/components/LearningContextActions.test.ts`（Create）
- **前置依赖**：M2-T7、M2-T8。
- **精确改造指令**：沿用目前由 CourseView `chooseSection` 写入的 `slp-last-learning-context` 和既有 route query API；为 context 增可选 `updatedAt`，读取旧对象仍可用；只接受正数 ID/非空 entity。将共享 `LearningContextActions` 中的“课程→小节→知识点”路径与“返回此学习内容” link 在 courses/graph/practice/review/tutor 页面复用；缺 context 到 `/courses`，不调用无保护 `history.back()`。不能删除现有 query href。
- **受影响既有测试与更新指引**：保留 `learningContext.test.ts` sanitizer/legacy read；新增 `LearningContextActions.test.ts` 覆盖 breadcrumbs、href query preservation、missing context fallback、当前目标去重。
- **单任务验证命令**：`cd frontend; npx vitest run src/domain/learningContext.test.ts src/components/LearningContextActions.test.ts`。

### - [ ] M3-T18 Tutor 知识点上下文预填
- **涉及文件**：`frontend/src/views/TutorView.vue`；`frontend/src/views/TutorView.test.ts`；`frontend/src/api/knowledge.ts`
- **前置依赖**：M3-T17。
- **精确改造指令**：当前 entity query 有预填；`knowledgePoints(courseId)` 是现有 API。对 point-only context 取精确 ID 的 `name/graphEntity` 填入可编辑 question 建议；unknown/loading/error 不造名，不触发 submit。route.entity 优先，不覆盖；chips/quick prompts 继续显示。
- **受影响既有测试与更新指引**：保留 entity prefill/no auto-submit；新增 point ID exact match/unknown/API failure/editable prompt tests。
- **单任务验证命令**：`cd frontend; npx vitest run src/views/TutorView.test.ts src/domain/learningContext.test.ts`。

### - [ ] M3-T19 当前账户修改密码与刷新令牌撤销
- **涉及文件**：`backend/learning-service/src/main/java/edu/wfust/learning/platform/auth/AuthController.java`；`backend/learning-service/src/main/java/edu/wfust/learning/platform/auth/AuthService.java`；`backend/learning-service/src/main/java/edu/wfust/learning/platform/auth/RefreshTokenMapper.java`；`backend/learning-service/src/test/java/edu/wfust/learning/platform/auth/AuthApiIntegrationTest.java`
- **前置依赖**：M1-T9。
- **精确改造指令**：新 authenticated `PATCH /api/auth/password` body 固定 `{currentPassword,newPassword}`，user ID 从 JWT subject 得到。旧密码 BCrypt verify 不通过 401；新密码先验证至少 8 个 code points、UTF-8 bytes `<=72`，空白拒绝。事务内 BCrypt 更新并撤销该 user 所有尚有效 refresh token，仅更新 `revoked_at`，不返回 hash/token；成功 `204`。Access JWT 是 stateless；不得称 refresh 撤销会立即让已有 access token 失效。
- **受影响既有测试与更新指引**：AuthApiIntegrationTest 增错 current、长度、本人所有 refresh 被 revoke、另一用户 refresh 不变、新密码能 login/旧密码不能；保留 refresh rotation/logout。
- **单任务验证命令**：`mvn -f backend/pom.xml -pl learning-service -am -Dtest=AuthApiIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`。

### - [ ] M3-T20 前端修改密码安全对话框
- **涉及文件**：`frontend/src/api/auth.ts`；`frontend/src/App.vue`；`frontend/src/components/ChangePasswordDialog.vue`（Create）；`frontend/src/components/ChangePasswordDialog.test.ts`（Create）
- **前置依赖**：M3-T19。
- **精确改造指令**：user menu 增“修改密码”弹窗 current/new/confirm，密码 input type=password、new confirm equality、客户端 Unicode code point 长度>=8，再由后端检查 UTF8<=72；成功后清四 localStorage auth keys、resetSession/resetLearningMetrics、router replace login；error 不回显密码、不清已输入新密码，current password 可清。对话框有 label/alert/focus management，取消不发请求。
- **受影响既有测试与更新指引**：新建 ChangePasswordDialog tests 覆盖 mismatch/empty/success/logout/401 不显示 password；保留 `AppRoleNavigation.test.ts`。
- **单任务验证命令**：`cd frontend; npx vitest run src/components/ChangePasswordDialog.test.ts`。

### - [ ] M3-T21 Refresh 请求 Single-flight 回归
- **涉及文件**：`frontend/src/api/http.ts`；`frontend/src/api/client.test.ts`
- **前置依赖**：无；只给现有 `refreshInFlight` 加并发回归测试。
- **精确改造指令**：当前已有 `refreshInFlight` promise。先写 concurrent deferred adapter test：5 API request同时401→仅一次 `/api/auth/refresh`；成功后每个原 request 恰好 replay 一次并使用新 token；refresh 401 清会话一次、所有原 request reject；request 自身 401 或重试 401 不循环。若现逻辑全通过，只保留回归测试，不为“增强”重写正确行为。
- **受影响既有测试与更新指引**：保留 shouldRefreshAfterUnauthorized 排除 login/register/refresh/logout tests。
- **单任务验证命令**：`cd frontend; npx vitest run src/api/client.test.ts`。

### - [ ] M3-T22 未保存表单离开确认原语
- **涉及文件**：`frontend/src/composables/useUnsavedChanges.ts`（Create）；`frontend/src/composables/useUnsavedChanges.test.ts`（Create）
- **前置依赖**：M2-T22。
- **精确改造指令**：hook 暴露 `useUnsavedChanges(isDirty,confirmLeave,baselineActions)`；dirty route leave 调 async confirm，允许 cancel；dirty beforeunload 仅用同步 `preventDefault/returnValue` 浏览器确认；clean 不提示；卸载时注销 listener；成功保存调用 `resetBaseline()`。Vue Router `onBeforeRouteLeave` resolve boolean。不能在 asynchronous beforeunload 弹 Vue modal，不得对已提交 publish/status action 当作 dirty draft。
- **受影响既有测试与更新指引**：测试 accept/reject、route pending、beforeunload install/remove、reset baseline 后 clean。
- **单任务验证命令**：`cd frontend; npx vitest run src/composables/useUnsavedChanges.test.ts`。

### - [ ] M3-T23 Admin 课程与错因诊断表单 dirty guard
- **涉及文件**：`frontend/src/views/AdminView.vue`；`frontend/src/views/AdminCourseViews.test.ts`；`frontend/src/views/AdminDiagnosisView.vue`；`frontend/src/views/AdminDiagnosisView.test.ts`；`frontend/src/composables/useUnsavedChanges.ts`
- **前置依赖**：M3-T22。
- **精确改造指令**：AdminView deep compare courseDraft/questionDraft baseline；new/edit 先 set baseline，字段变化 dirty，save success/new/reset 更新 baseline。AdminDiagnosis drafts compare saved config；改变 course/question 先确认，取消则恢复 select 原值且 preserve drafts。Readonly list/filter 不计 dirty。
- **受影响既有测试与更新指引**：保留 AdminCourseViews CRUD 与 AdminDiagnosis save/error；新增 leave cancel/preserve、save success/allow。
- **单任务验证命令**：`cd frontend; npx vitest run src/views/AdminCourseViews.test.ts src/views/AdminDiagnosisView.test.ts src/composables/useUnsavedChanges.test.ts`。

### - [ ] M3-T24 题库导入 dirty guard
- **涉及文件**：`frontend/src/views/QuestionBankView.vue`；`frontend/src/views/QuestionBankView.test.ts`；`frontend/src/composables/useUnsavedChanges.ts`
- **前置依赖**：M3-T22。
- **精确改造指令**：QuestionBank `versionName/sourceFilename/questionsJson` 对 baseline deep compare；成功 create/import 更新 baseline，失败保留 dirty；dirty tab/query route leave 可确认取消。MicroLesson 目前没有可编辑 draft field，generate/publish 是已提交 action，不加确认。扩展现有 View test 覆盖 import/save success/failure、route leave accept/reject 和 payload。
- **受影响既有测试与更新指引**：扩展 M2-T17 已用的 `QuestionBankView.test.ts`，保留现有 draft/import row errors/publish confirmation tests，并新增成功清洁 baseline、失败保留 dirty、离开确认 accept/reject 和 payload 不变断言。MicroLesson 既有 generate/publish tests 只执行不修改。
- **单任务验证命令**：`cd frontend; npx vitest run src/views/QuestionBankView.test.ts src/composables/useUnsavedChanges.test.ts`。

### - [ ] M3-T25 ExperimentTask draft dirty guard
- **涉及文件**：`frontend/src/views/ExperimentTaskAdminView.vue`；`frontend/src/views/ExperimentTaskAdminView.test.ts`
- **前置依赖**：M3-T4、M3-T22。
- **精确改造指令**：title/question/description/referenceAnswer 相比空 baseline；任一字段改动 dirty；create success 清空并 reset baseline，失败保留字段和 dirty。OPEN/CLOSED 是已提交立即状态动作，沿用二次确认且不触发 draft guard。
- **受影响既有测试与更新指引**：扩展已存在的 `ExperimentTaskAdminView.test.ts`，保留 create/status API behavior；新增 failed create、success reset、leave accept/reject。
- **单任务验证命令**：`cd frontend; npx vitest run src/views/ExperimentTaskAdminView.test.ts src/composables/useUnsavedChanges.test.ts`。

### M3 收口门禁

| 项目 | 命令 | 通过标准 |
| --- | --- | --- |
 | learning-service/security/assignment | `mvn -f backend/pom.xml -pl learning-service -am -Dtest=ExperimentTaskOrderMigrationTest,ExperimentTaskServiceTest,AiExperimentControllerTest,AiExperimentResponseGuardTest,EvaluationServiceTest,AuthApiIntegrationTest,ExperimentExportTest -Dsurefire.failIfNoSpecifiedTests=false test` | V16 migration、atomic STUDENT assignment、student rubric isolation、run 不提前结单/评估后结单、server elapsed/password revocation/RANKED export guards 通过 |
 | 前端相关回归 | `cd frontend; npx vitest run src/views/ExperimentsView.test.ts src/domain/experimentExport.test.ts src/views/ExperimentTasksView.test.ts src/views/EvaluationView.test.ts src/views/ExperimentTaskAdminView.test.ts src/domain/learningContext.test.ts src/views/TutorView.test.ts src/api/client.test.ts src/composables/useUnsavedChanges.test.ts src/views/AdminCourseViews.test.ts src/views/AdminDiagnosisView.test.ts src/views/QuestionBankView.test.ts` | client batch progress/retry/export、student task answer isolation、server order/wizard、context、refresh、dirty guard 全绿 |
 | 全量回归 | `mvn -f backend/pom.xml test`；`cd frontend; npm test; npm run build` | 三后端模块 + 前端测试和 build 全绿 |

M3 commit message：`feat: secure assigned experiments and guided evaluation`

## M4：文档对齐和终版验收

### - [ ] M4-T1 README 与 API 合同对齐
- **涉及文件**：`README.md`；`docs/api.md`
- **前置依赖**：M1–M3 全部通过。
- **精确改造指令**：README 写清 5 学生分组/3 admin workbench、role route、password/refresh semantics、V16 nullable group order（不代表随机化）、ADMIN fixed-case manifest 与 browser-only batch queue。API 文档逐项对照最终 manifest controller 和所有 route/method/body/status/error/role；M3-T11 完成后，`GET /admin/experiments/fixed-suite` 必须记录为 ADMIN-only，并只列 `suiteVersion` 与 `caseId/question/sourceSection/knowledgePoint` 字段。不得写 V17 migration、题库答案作为 reference 或未实现 server durable queue；API 文档保持既有省略 `/api` 前缀约定。
- **受影响既有测试与更新指引**：flyway V1–V15 history 不改；V16 schema 用于新 order 字段。静态查每个 API route 对照 controller paths，不更改 DTO 来“方便文档”。
- **单任务验证命令**：`rg -n "fixed-suite|V16|V17|A_TO_B|A→B|password|/experiments/run" README.md docs/api.md`；M3-T11 已完成时，fixed-suite 的角色、路径和字段必须与 controller 一致；不得声称 V17 已实现或把 fixed-suite 说成不存在；随后 `git diff --check`。

### - [ ] M4-T2 UI 设计说明与答辩剧本对齐
- **涉及文件**：`docs/ui-design.md`；`docs/答辩现场演示剧本与故障应急清单.md`
- **前置依赖**：M4-T1。
- **精确改造指令**：更新 tokens/Element Plus 版本、四 CSS owner/byte、学生五组/管理三板块、8 条 fixed manifest 不含答案、batch 只在客户端运行、server order/evaluation/password flow。同步写本轮 screenshot 基线数与缺陷：课程笔记 rail、题后内容高度、Tutor empty-state、admin experiment 全长、question bank 表单留白、dashboard empty chart。答辩操作绑定 canonical route；图谱离线真实 error；API mock smoke 不说成后端联调；密码用合成账号。
- **受影响既有测试与更新指引**：页面路线必须全部来自 `router.ts`，实际旧路由与 role 与 smoke scenario 对齐。
- **单任务验证命令**：`rg -n "RANKED|生理|降低认知负荷|可解释规则推荐|1440|1280|158/158|batch|V16" docs/ui-design.md docs/答辩现场演示剧本与故障应急清单.md`；检查无过期 route、错误 UI status 或越界成功表述。

### - [ ] M4-T3 测试报告与工程验收报告同步
- **涉及文件**：`docs/test-report.md`；`docs/毕业设计工程验收报告.md`
- **前置依赖**：M4-T1、M4-T2。
- **精确改造指令**：只填该次实际 Maven module test counts、Vitest files/tests、两视口 smoke 总数/角色拆分/errors、production chunks/warnings、CSS exact bytes、V16 status、compliance/credential checks。保留历史章节，在终版追加实际日期与运行命令；不得追写历史伪数据。API fixture smoke 与 real backend integration 清楚分栏。
- **受影响既有测试与更新指引**：报告每个数字均由刚运行 CLI 输出核实；没有运行的凭据扫描/DB migration MySQL test 明记 not run，不写 pass。
- **单任务验证命令**：`rg -n "mvn|Vitest|Playwright|V[0-9]+|B|test files|chunk|warning" docs/test-report.md docs/毕业设计工程验收报告.md`；与最新终版日志人工交叉核验。

### - [ ] M4-T4 Continuation ledger 与 smoke matrix 锁定
- **涉及文件**：`docs/development/continuation-ledger.md`；`frontend/scripts/authenticated-ui-smoke.mjs`；`frontend/tests/design-system-contract.test.ts`
- **前置依赖**：M4-T3。
- **精确改造指令**：continuation ledger 记最终 HEAD、V16、实际结果与遗留风险；smoke routes 直接与 `router.ts` canonical + legacy 列表逐项对齐，含 STUDENT `/experiment-run` 与 ADMIN 四 canonical、guest/login redirects；视口 1440×900/1280×800。保留当前指标并扩 scenario: unsaved-route confirm、password modal validation、student assigned run hidden rubric、A/B task order。测试 console/pageerror/unexpected HTTP/overflow/topbar/buttons/native controls/table/role redirects；明确命名允许的 403 场景，不能 suppress 全局 errors。design contract 使用 M2-T23 AST scanner 及全部 4 CSS。
- **受影响既有测试与更新指引**：不要固定期望仍是 158；路由/场景更改后用真实输出更新访问数。截图输出 `.ui-visual-review/`，只删除本轮 task label 子目录或确认其是唯一内容后清空根目录；失败报告附 role/viewport/requested/final/screenshot。
- **单任务验证命令**：`cd frontend; npm run build; node scripts/authenticated-ui-smoke.mjs`；预期测试脚本所有 role/viewport/scenarios 零未预期失败。

### M4 终版门禁与提交

| 类别 | 精确命令 | 验收标准 |
| --- | --- | --- |
| Java 三模块 | `mvn -f backend/pom.xml test` | learning-service、ai-service、learning-gateway 全部通过；汇报实际 suite tests 数 |
| 前端完整测试 | `cd frontend; npm test` | 所有原有和新增测试通过，compliance guard 在内 |
| 前端生产构建 | `cd frontend; npm run build` | 0 errors、0 chunk warnings |
| 双角色运行态 | `cd frontend; node scripts/authenticated-ui-smoke.mjs` | 每个 canonical/legacy route 在两种视口与权限角色符合期望，0 未预期错误/overflow/control regression |
| CSS | `rg -n -F 'important' frontend/src --glob '*.css' --glob '*.vue'`；`cd frontend; node scripts/scan-vue-classes.mjs --check` | 项目 CSS `!important`=0、AST missing class=0；四 CSS 合计目标 `<=43,000 B`。如保护既有行为导致达不到，实报 exact bytes/原因 |
| 代码卫生 | `git diff --check` | 退出码 0 |
| 合规/凭据 | `cd frontend; npx vitest run tests/compliance-guard.test.ts`; 执行仓库可用的 secret scanner（先 `Get-Command gitleaks`；若无则运行 `rg` 的 key/password/private-key pattern 并逐个核对测试占位值） | 不改弱守卫、不输出真实 secrets；工具缺失时如实报告，不能声称扫描通过 |
 | 临时文件 | `Test-Path frontend/.ui-visual-review` | 终版截图与 metrics 目录均不存在；仅删除该次 smoke 使用的精确目录 |

M4 本地提交信息：`docs: finalize platform overhaul acceptance`; 除明确要求外不得自动 push。提交前 `git status --short` 应只含当前 milestone 计划内文件，提交后再次确认 clean。

## 低档模型避坑清单

1. **CSS 顺序**：先 Element Plus vendor CSS（新增 input/select/table/dialog/checkbox/radio imports），再项目 `styles.css` → `styles-graph.css` → `styles-learning.css` → `styles-design-system.css`。design-system 是最终 token/原语覆盖源；壳层 responsive owner 是 styles.css。不能改变顺序掩盖 duplicate，不能在 vendor CSS 里改。
2. **Element Plus selector**：勿覆盖 node_modules CSS；通过 `--el-*` token 与本项目 wrapper 调整。native `button:not(.el-button)` 不能套 Element button；input selector 排除 checkbox/radio/range/hidden/file。当前有些 Vue pages 自带 scoped styles，静态全局 scanner 必须读取同文件 `<style scoped>`。
3. **角色前缀**：Principal authority 是 `ROLE_ADMIN/ROLE_STUDENT`；注解写 `hasRole('ADMIN')` / `hasRole('STUDENT')`。只隐藏菜单不算安全；服务控制器和 Service 对高价值写操作都要角色校验。
4. **缓存 role 非权威**：`router.ts` 在 token 缺缓存 role 时请求 `/api/auth/me`；客户端缓存只负责 UI 快速渲染，后端 JWT subject/role 才授权。改密后 refresh 撤销不等于 stateless access token 即刻吊销。
5. **`RANKED` 清洗**：`AiExperimentController.sanitizeResults` 明确过滤 `prompt/systemPrompt/systemPromptBody/fixedSystemPrompt` 与敏感 key；`ExperimentExport` 只导版本/hash。任何新 batch/JSON mapper 必须先复用 sanitizer，再做导出；不能只依赖 Vue 的 `v-if`。
6. **固定案例与批次实验边界**：M3-T11 新增 ADMIN-only fixed-suite manifest，8 个案例仅复用 V4 seed 的题干、来源小节和知识点；它不含答案 key，也不构成教师审核过的参考答案 corpus。批次逐项调用真实 `/api/experiments/run` 并独立保存，失败继续；队列/进度仅驻留浏览器内存，不声称服务端持久队列或刷新恢复。现有 compare endpoint timeout 最长 600s，执行时禁重复 submit。
7. **迁移和测试夹具**：已执行 V1–V15 immutable。AdminStudyWorkflow 使用 H2 `admin-study-base.sql` 且自身 Migration 关闭；新 V16 需迁移专门测试，不能只在该 fixture 里加列就声称线上迁移通过。
8. **答案/隐私与计时**：`GET /quizzes` 只给题干/选项，提交后才有判题结果。Student assignment DTO 不返回 userId/reference rubric。`learning_evaluation_session.started_at` 由 DB 生成，elapsed 服务端以该时间和 server clock 计算，不能信任 browser `Date.now()`。
9. **匿名统计语义**：Teaching dashboard/evaluation mapper 当前已筛 STUDENT；保持五个 overview、daily、weak-point SQL 子查询都筛 STUDENT。COUNT section 总量是课程静态值，不要误筛学生。
10. **用户保护导航**：unload 原生确认是同步且浏览器可覆盖，SPA route guard 可自绘 async dialog。必须区分 dirty form 与已保存 command；失败提交保持 dirty，成功提交更新 baseline。

## 执行结束交付清单

- 所有 M1–M4 checkbox 完成或逐项标注明确 blocker；不接受无解释的未完成。
- 交付新增 migration 名称、API 字段兼容表、五组学生导航/三个 admin 板块映射、全部 Vue 视图与共享组件覆盖表、CSS 各文件前后 byte、四模块测试数、两视口 smoke 访问/错误数、规范化 CSV/JSON 样例（仅合成数据）、secret scan 结果。
- 确认案例为管理员手动输入且没有伪造“教师已核验”字段；空 reference 不生成 reference-based scores；batch error/status 区分；RANKED body 不进任何 browser/export path。
- 确认 `.ui-visual-review/` 不存在、`git diff --check` 通过、工作树只剩明确待审文件或 clean；本任务不授予修改外部依赖/向远程推送的许可。
