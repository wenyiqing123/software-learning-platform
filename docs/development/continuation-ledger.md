# 实施记录（2026-11-12）

- 用户已经批准开发；继续完成全部学习闭环，无需重复确认。
- 工作目录固定为当前独立新建项目，保留原 Mecha-QA 与 agent-chat-studio。现有项目无首个提交，在此目录原位实现以满足指定交付位置，不创建额外工作树。
- 本机 MySQL 8.0.33、Redis 5.0.14.1、RabbitMQ 4.2.3 保持原安装与数据，项目使用专属 schema/vhost/key 前缀。
- 主工作：认证异常、推荐/消息可靠性、会话与实验保存、联调、脚本和文档。
- 独立任务 A：管理端课程题库和课程阅读（V4）。
- 独立任务 B：AI 模式、同证据比较、超时与缺少配置处理。
- 独立任务 C：前端真实统计、定向错题和错误提示。
- 回归证据：认证集成测试首次 5 项中 4 项失败；修正后 5/5 通过。错误原因是 ERROR 分派被安全过滤器变为 401、expiresIn 写死。
- 后端实验由业务服务调用 AI 服务并保存；避免接收浏览器自由提交的科研结果。
- 最终验收需要区分契约测试与真实 Python/Nebula/LLM 依赖联调。

## 追加记录（2026-11-19）

- 按 `docs/功能扩展清单.md` 完成 F4–F8 首版：学习评估（V7）、规则式自适应专项练习、课程错因关联（V8/V9）、个人掌握图谱和阶段性学习报告/CSV 导出。
- F5–F8 均增加 Java 接口、Vue 页面、用户或管理员权限边界、测试和接口验收；F6 明确禁止根据一次错误答案由模型臆测错因，F7/F8 只使用 MySQL 中当前用户的学习记录，NebulaGraph 继续只读。
- 全量回归证据：learning-service 91/91、ai-service 85/85、learning-gateway 2/2；前端 16 个测试文件/40 项通过，生产构建成功。组合 API 冒烟覆盖专项题单、掌握图谱、报告 JSON/CSV 和管理员诊断读取。
- 当前运行服务为 Vue 5173、Gateway 8080、learning-service 8081、ai-service 8082；Python Bridge 8999 本轮未启动，故图谱/AI 检索依赖需按原有启动说明单独检查。
- 后续复核已启动 NebulaGraph 与 Python Bridge 8999；真实需求分析检索返回 7 条三元组，F7 “全部课程”修复已进入 8081 运行 JAR。
- F9 已完成第一版：V10 笔记/收藏表、用户隔离接口、课程阅读页笔记面板和 3 项服务测试；真实 API 已验证创建、查询、更新、删除和收藏切换，临时验收笔记已清理。

## 优化记录（2026-11-20）

- 基于基线 199 项后端测试、48 项前端测试和成功生产构建，新增优化路线图 `docs/superpowers/plans/2026-11-20-platform-optimization-roadmap.md`。
- 完成真实依赖状态聚合：`SystemStatusService` 探测 MySQL、Redis、RabbitMQ、Python Bridge/知识图谱和 AI 服务；MySQL 故障为 `DOWN`，其他依赖故障为 `DEGRADED`，不返回凭据或内部地址。新增 2 项聚合测试，并验证 Python Graph Client 的 `/health` 只读调用。
- 完成 App shell 导航减负：学习、练习、研究评估和管理工作台分组折叠；移动端使用核心学习底部导航；顶部增加低干扰依赖状态面板。
- 完成实验 CSV 展平导出，JSON 原始导出保持兼容；失败运行以 `FAILED` 保留但不进入成功指标。新增 CSV 单元测试和 ExperimentsView 回归测试。
- Playwright 视觉检查覆盖桌面/移动端，移动端无横向溢出；下一步全量回归后再决定是否继续拆分后端 AI 策略类，当前不做无证据的架构重构。
- 最终回归：后端 201/201、前端 52/52、生产构建成功；真实 `/api/system/status` 五项依赖均为 UP；`scripts/smoke-test.ps1` 18/18 通过。重新打包时发现并修复 Spring 多构造器注入问题，随后只重启本项目服务完成真实接口验收；新增状态请求会校验请求序号和令牌，避免旧账号响应回写。

## Wave 2 记录（2026-11-20）

- 前端样式系统：重构 `styles.css`、`styles-graph.css`、`styles-learning.css` 的根变量和关键容器/题卡/图谱/错题规则，新增统一 token bridge；`styles-design-system.css` 增加 surface、text、border、accent、radius、shadow、feedback、context、evidence、Markdown 和移动端布局 token。
- 学习闭环：新增 `frontend/src/domain/learningContext.ts` 及测试，接通总览继续学习/复习/推荐入口，课程目录 hash 锚点、最近学习上下文、本节图谱/助教/练习入口；路由新增 hash 平滑滚动。
- 图谱：`VisualizationViewport` 增加节点搜索、居中工具；`GraphEvidenceDrawer` 展示当前真实证据；`GraphView` 和 `MasteryMapView` 增加证据/节点上下文和 2D/3D 视口交互。Bridge/NebulaGraph 仍只读，离线时不生成静态图。
- 助教：新增安全 `renderSimpleMarkdown`/`MarkdownContent`，回答和流式输出支持分段、列表、代码块；RANKED 前端不展示后台固定系统提示词，三元组证据可折叠。
- 学习状态：新增 `FeedbackState` 共享组件；专项练习、每日计划、推荐、课程、练习、错题、复习页面的加载/错误/空数据入口统一使用设计 token；推荐文案明确为“基于掌握度、错题与先修关系的可解释规则推荐”。
- 视觉验收：真实临时本地账号检查 1440px 图谱页，返回 8 个实体/7 条关系并显示证据抽屉；390px 课程页 `scrollWidth === clientWidth`，底部导航位于 `y=782`、高度 62px，目录/进度/笔记/三条上下文入口均可见。
- Wave 3/4 待办：管理员页面反馈状态统一、业务大视图继续拆分、图谱 chunk 进一步拆分、固定评测集和认知负荷数据复核；不修改 Mecha-QA/Python/NebulaGraph，不虚构实验或学习效果。

## Wave 3 记录（2026-11-20）

- 问答实验后端：`ExperimentMetrics` 增加有效 BLEU 阶数；`ExperimentExport` 提供服务端 CSV/JSON 导出，保存问题、实验类型、证据返回顺序、耗时、模型/生成参数、提示词版本/hash、分项指标、人工评分和失败状态，明确排除 `prompt` 正文。
- 问答实验前端：执行状态面板区分预期条件、实际返回数、运行时长和失败条件；结果卡并排展示答案、证据返回顺序、耗时、ROUGE-L、BLEU、有效阶数、实体召回率、人工评分和运行配置。RANKED 卡片不展示提示词正文。
- 认知负荷后端：新增 V15 `evaluation_order_and_delayed_measurement` 迁移；评估会话支持 `taskOrder`、`assignmentId`，测量阶段支持 `DELAYED`；`EvaluationStatistics` 提供匿名参与者编号、均值/中位数/标准差/IQR；管理员 CSV 不返回真实 userId。
- 认知负荷前端：评估页增加知情说明、流程顺序、任务顺序、Paas 1/3/5/7/9 语义锚点、延后测验入口；实验任务页先进入评估流程，再进入真实问答任务；管理员页展示论文统计所需分项描述统计。
- Wave 3 验收：后端 206/206、前端 58/58、生产构建成功；新增测试验证提示词正文不进入导出、匿名编号不暴露用户 ID、延后测量独立保存和按实验条件分组统计。

## Wave 4 记录（2026-11-20）

- 后端 AI 问答执行增加显式策略选择器：Chat 策略处理 DIRECT、TRIPLES、消融和未启用专用模型的交互式 RANKED；Grounded 策略处理实验 RANKED 与专用 RANKED。AnswerService 的既有 REST/SSE 契约、Python 原始 Prompt 和隐藏系统提示词边界保持不变。
- GroundedRankedAnswer 的证据覆盖、单/多事实判定、回退相关性评分和归一化规则已拆到独立 GroundedEvidenceValidator；新增空知识证据、实体/关系/宾语覆盖和多事实判定测试。
- 稳定性终审：普通模型空文本返回 MODEL_EMPTY；流式请求没有文本时发送结构化错误；PythonGraphClient 增加可配置连接/读取超时、空响应与非法三元组拒绝；未知学习事件不再静默确认，Rabbit listener 使用不重回队的 DLQ 配置；已处理事件保持幂等，推荐 Redis 失效仍在事务提交后执行。
- 安全终审：学生题目列表投影明确不含标准答案字段；错题变更测试验证 user_id 归属；管理员端继续由 ADMIN 方法鉴权保护；仓库扫描未发现真实凭据、私钥或模型权重。
- 管理台终审：课程和题目启停、题库版本发布、微课发布加入二次确认；题库 JSON 文件限制为 2MB；Admin、错因、题库、微课、教学看板和学习报告接入共享反馈状态并统一设计 token，窄屏不依赖固定宽度表格。
- Wave 4 实测：AI 99/99、learning-service 117/117、gateway 2/2，后端合计 218/218；前端 30 个测试文件、59 项通过；生产构建成功。未修改 Mecha-QA、Python Bridge、NebulaGraph、模型权重或既有图空间。

## 任务 2 记录（2026-11-21）

- 全局 CSS 从 75,586 bytes 收敛到 67,131 bytes，采用 AST 压缩、重复根变量移除和设计 token 统一；未新增全局样式表。
- 完成 LoginView、ThreeGraphCanvas、PrerequisitePathPanel、LearningNotesPanel、ExperimentTaskAdminView 的视觉/交互精修；三维节点、先修路径、笔记折叠和实验任务状态均保留真实数据与诚实错误态。
- 完成巨型视图职责拆分：AdminCoursePanel、AdminQuestionPanel、ExperimentResultGrid、TutorModeSelector、TutorAnswerCard、PracticeQuestionCard；AdminView、ExperimentsView、TutorView、PracticeView 体积均下降。
- 新增 LoginView、TutorView、TutorAnswerCard、PracticeQuestionCard、ExperimentResultGrid 测试；EvaluationView 增加 V15 taskOrder 回归断言。RANKED 后台固定证据约束提示词未出现在前端渲染路径。
- 任务 2 最终验证：前端 35 个测试文件、64 项通过，生产构建成功；后端接口代码未修改，既有后端测试契约不受影响。

## 任务 3 记录（2026-11-21）

- AnswerService 从 456 行/29,213 B 精简到 149 行/9,248 B。检索与 Provenance 由 AnswerPreparationCoordinator 负责，模型/矩阵执行和响应组装由 AnswerExecutionCoordinator 负责，比较并发和 Qwen 2×3 编排由 AnswerComparisonCoordinator 负责；AnswerService 仅保留兼容门面、依赖状态和委托方法。
- 安全审计新增并修复：EvaluationService 启动会话校验 assignmentId 归属；ExperimentTaskStudentController 使用不含 userId 的学生 DTO；既有 Quiz/Notes/Tutor/StudyPlan/Report/Review/Experiment 所有权查询和 ADMIN 鉴权通过检查。
- 异步一致性：KnowledgeMasteryConsumer 在 afterCommit 同时失效 Redis 推荐键与 MySQL recommendation_snapshot；OutboxRelay 对 nack/returned message 保持 pending；新增边界测试。
- 任务 3 相关定向测试已通过：AI 协作者/原有问答测试、EvaluationService、ExperimentTaskService、KnowledgeMasteryConsumer、OutboxRelay、RecommendationService。全量回归已通过，准备本地提交，不推送远程。

## 任务 1 路由审计记录（2026-11-21）

- 复核全部 learning-service/ai-service Controller 前缀：AI 统一 /api/ai/**，学习服务统一 /api/** 兜底；新增系统状态、评估管理、实验导出、微课、题库版本、实验任务和 SSE 均有对应覆盖。
- GatewayRouteConfigurationTest 扩展为 5 项：14 个学习服务路径和 7 个 AI/SSE 路径通过 PathPattern 实际匹配断言，并验证 AI route order=-1 与 210s 响应超时。
- Nginx 文本审计确认 /api/ 关闭代理缓冲、请求缓冲和缓存，设置 X-Accel-Buffering no 与 300s 超时；Vite /api 代理保持指向 Gateway。
- V1–V15 迁移连续存在。修复部署一致性：learning-service RabbitMQ vhost 改为读取 RABBITMQ_VHOST，Compose 和本地 .env.example 显式提供 /software-learning 默认值。

## 任务 4/5 终版收口（2026-11-21）

- 新增后端/前端合规守卫测试：固定 RANKED 系统提示词仅允许服务端版本/hash元数据；实验导出、Controller 响应和浏览器源码不含提示词正文；图谱/Python 依赖失败不生成假节点；学生提交前题目投影不含答案；核心文案不越过认知负荷与推荐解释边界。
- Vite 通过 Rolldown `manualChunks` 拆分 `graph-three`、`graph-g6`、`graph-layout`、`graph-renderer`、`graph-ui`、`graph-algorithm`、`element-plus`、`vue-core` 和 `ui-vendor`。最终无 chunk 警告，Three.js 运行时块为 565.99 kB / gzip 139.14 kB，G6 为 1,366.15 kB / gzip 392.77 kB。
- 使用 `vite preview` + Playwright 在 1440×900 与 1280×800 各访问 14 个入口，共 28 次；未登录页面按设计重定向到 `/login`，无控制台错误、未捕获异常或横向溢出。该结果不冒充已登录业务数据联调。
- 终版实测：ai-service 102/102、learning-service 126/126、learning-gateway 5/5、前端 40 个测试文件/76 项，生产构建和 `git diff --check` 通过；四份全局 CSS 均为 0 个 `!important`。新增答辩剧本与故障应急清单，README/API/UI/测试报告/工程验收报告均已对齐当前提交。
- 本轮未修改外部 Mecha-QA、Python Bridge、NebulaGraph 图空间、模型权重或任何凭据；远程仓库保持未推送。

## 封版前最后精修（2026-11-21）

- Compose 与服务器环境模板完成全量变量对齐，脚本迁移到 `scripts/`，`backend/.idea` 从 Git 索引移除，测试专用 Node 类型声明移出应用源码。
- `frontend/src/api/client.ts` 已收敛为兼容门面，业务请求拆分到认证、课程、知识、练习、评估、实验、管理、推荐、系统和助教模块；GroundedRankedAnswer 保持现有内聚结构，避免破坏 prompt/trace 契约。
- 新增 Dashboard、WrongBook、Recommendations、EvaluationAdmin 四个视图测试；前端 40 个测试文件/76 项通过。
- 已登录态隔离 Playwright 脚本覆盖 1440×900 与 1280×800 的 14 个学生/管理入口，共 28 次，无控制台错误、页面异常、横向溢出、顶部遮挡和按钮内容溢出；报告明确不替代真实全栈联调。

## 全仓样式一致性审计（2026-11-21）

- 静态幽灵类名扫描覆盖 41 个 Vue 源文件，真实静态缺失类名从初始清单收敛到 0；补齐统一页面骨架、管理/评估表单、实验结果、微课/报告/教学台页面和拆分子组件的基础样式。
- 运行态脚本从 14 条扩展到 router 的 24 条路由，1440×900、1280×800 共 48 次访问；默认控件、原生表格、卡片层级、页头骨架、控制台错误、横向溢出和遮挡指标均通过。
- 设计系统新增 Element Plus 变量映射、原生控件/表格兜底、阅读宽度和数据数字排版；未引入 `!important`，未改后端、路由参数、接口契约或外部知识图谱。

## 双角色权限与管理工作台收口（2026-11-22）

- 路由使用 `meta.roles` 实现 STUDENT/ADMIN 互斥；学生首页 `/dashboard`，管理员首页 `/teaching-dashboard`，旧 `/admin/*` 入口重定向兼容。
- 管理员菜单收敛为教学总览、内容与题库中心、实验与评价中心；新增两个 Tab 工作台，移除管理员个人学习入口。
- 学生个人控制器增加角色约束；教学/评价统计过滤管理员账号；实验任务分配校验目标账号必须为 STUDENT。
- 前端路由与工作台测试新增，前端 43 个测试文件/82 项通过；learning-service 128/128；双角色 Playwright 112/112。

## 教学总览可视化（2026-11-22）

- 管理员教学总览新增真实数据可视化：学习活动趋势和薄弱知识点掌握度排行，复用既有 `TeachingDashboard` DTO 的 `dailyActivity`/`weakPoints` 字段，不改变后端统计接口、匿名口径或外部图谱依赖。
- 新增 `TeachingDashboardCharts.vue`、`teachingDashboardCharts.ts` 及适配器测试；组件在无数据时显示空态，ECharts 实例在更新、尺寸变化和卸载时清理。
- 本轮前端实测为 44 个测试文件/84 项通过，生产构建成功且无 chunk warning；双角色 Playwright 112/112；未修改 Mecha-QA、Python Bridge、NebulaGraph、模型权重或任何凭据。

## 学习助手多会话对话（2026-11-22）

- 按已确认设计完成学习助手工作台：复用 SSE 流式问答和 V5 会话表，新增会话重命名接口；没有引入 WebSocket AI 通道或示例项目的社交功能。
- 新增 `TutorConversationList`、`TutorMessageList`、`TutorComposer` 和 `domain/tutorChat.ts`；`TutorView` 保留提示分层、三种问答模式、对比实验、证据展示、停止和历史持久化。
- 路由上下文 `courseId/sectionId/knowledgePointId/entity` 以可见标签保留，不自动发起请求；会话重命名、读写、删除均保持当前用户归属检查。
- 最终实测：ai-service `102/102`、learning-service `131/131`、learning-gateway `5/5`、前端 `48` 个测试文件/`92` 项；生产构建成功且无 chunk warning；双角色 Playwright `112/112`；临时巡检目录已清理。
- 未修改 Mecha-QA、Python Bridge、NebulaGraph 图空间、模型权重或任何凭据；RANKED 后台提示词、离线图谱和学术措辞合规边界保持不变。

## 全站视觉升级第 1 批（2026-11-23）

- 按四个独立提交推进：全局/入口，课程/图谱/助教，练习/学习记录，实验/管理台。本批基线审计发现重复主题根、页头字号竞争、总览装饰挤占内容、图表辅助字过小。
- 清理重复主题根、失效装饰及基础按钮/提示样式；四份全局 CSS 从 80,610 B 减至 69,612 B。按钮、表格、壳层与 Element Plus 使用同一套主题 token，登录、学生总览和教学总览重新复拍核对。
- `authenticated-ui-smoke.mjs` 通过生产构建和 API 拦截进行 1440×900、1280×800 角色渲染验证，116/116；Guest 登录主按钮对比度 6.81:1；教学看板有数据与空态单独采集。截图目录在提交前删除。
- 前端 49 文件 / 95 项通过；生产构建成功、0 chunk warnings；`git diff --check` 通过；四份全局 CSS 无 `!important`。题库视图原有的局部 `!important` 留待第 4 批源规则整理。无后端或接口改动。

## 全站视觉升级第 2 批（2026-11-23）

- 课程正文解除笔记嵌套和重复学习入口，目录侧保留可展开的笔记和收藏；知识图谱、先修路径与掌握图谱统一画布工具、关系标识、故障状态与证据组件。
- 助教右侧策略移至会话下方，扩大对话列；模型模式、消息流、输入区和证据展开统一样式。Markdown 代码复制两项回归测试、掌握图谱真实空证据回归通过，既有接口和问答逻辑不变。
- 50 文件 / 97 项 Vitest 通过；生产构建成功、0 chunk warnings；双尺寸 Playwright 132/132，包括 3D 非空画布/拖动、图谱离线、课程笔记和助教历史场景。四份全局 CSS 合计 59,519 B。

## 全站视觉升级第 3 批（2026-11-23）

- 确认判题禁用态降低对比度、错题搜索双边框和推荐理由截断，三项浏览器回归检查在两个视口均经历先失败后通过。题卡、复习、专项、错题、每日计划、推荐、微课和学习报告已逐页复拍。
- 全路由 144/144、Vitest 50 文件 / 97 项；生产构建成功、0 chunk warnings，`git diff --check` 通过。四份全局 CSS 55,383 B；学生答案、接口与报告统计口径不变。截图清理后本地提交。

## 全站视觉升级第 4 批（2026-11-23）

- 本批范围为问答对照实验、认知负荷评价、题库、微课和两个管理员中心；未修改后端、API 契约、实验口径、图数据或外部模型。
- 修复 Paas 量表视觉回归：9 个刻度桌面一行等宽、窄屏三列，单选与键盘焦点保持清晰；实验结果卡使用共享行轨道对齐指标，失败实验仍明确展示失败且无答案评分操作。
- 评价管理员统计表改为带语义行/列标题的原生表格并适配桌面宽度；管理员中心嵌入页标题降级，移除页面重复 H1；`/experiments` 正确高亮实验管理导航入口且当前项保持可见。
- 题库文件选择器与普通字段 label 样式解耦，删除历史强制覆盖规则；浏览器断言验证它在两个桌面视口均保持横向按钮布局和可读颜色。
- Playwright 为微课页面新增正常数据与真实权限拒绝两个隔离场景。管理员调用学生专属 `/api/courses/3/study` 仍如实得到 403，页面显示错误且禁用生成；脚本通过 response URL/status/method 明确归类，只容许命名故障场景的该接口 403，其他 HTTP、资源、控制台和页面错误均失败。
- 最终前端测试为 50 文件 / 97 项通过；`npm run build` 通过且无 chunk 告警；双尺寸全角色 Playwright 158/158（学生每视口 44、管理员每视口 34、访客登录每视口 1）。控制台错误、未捕获异常、非预期 HTTP 错误、横向溢出、按钮挤压、裸控件及未样式表格均为 0；命名权限拒绝态另有 4 个预期 403 响应（每视口 2 次调用）。
- 四份全局 CSS 为 47,560 B，较第 3 批减少 7,823 B；CSS/Vue 强制覆盖规则扫描为 0。临时截图已清理，`git diff --check` 通过。仅前端与文档改动，待创建第 4 个独立本地提交，暂不推送。

## 里程碑 M1 收口记录（2026-11-24）

- 完成施工任务书【里程碑 M1】全部 9 项原子任务（M1-T1 ~ M1-T9）落地与逐项单任务定向验证。
- 前端核心改动：
  - `M1-T1`：学生端导航收敛为五个一级入口（学习概览、知识图谱、AI 助教、练习与评测、实验与认知负荷），管理员保持教学总览、内容与题库中心、实验与评价中心三大工作台；
  - `M1-T2`：新增 `StudentLearningSections` 二级分段导航，通过 query 参数在练习与评测（刷题/自适应/错题/复习/每日计划）、实验与认知负荷（问答实验/认知负荷）下实现平滑子页面分段切换；
  - `M1-T3`：路由兼容性升级，保留 `/practice/adaptive`、`/practice/review` 等历史 URL 自动映射并高亮所属一级导航；强化角色守卫矩阵，学生与管理员未授权互斥阻断并正确定向；
  - `M1-T4`：管理工作台 Tab 与旧入口无缝映射，移除重复面包屑与冗余入口。
- 后端核心改动：
  - `M1-T5`：后端实验与学习接口双向互斥，管理员访问学生自适应练习、微课学习返回 403，学生访问管理员中心返回 403；新增 `RoleBoundaryMvcTest`；
  - `M1-T6`：管理工作流测试账号语义校准，在 `AdminStudyWorkflowTest` 中注入独立 ADMIN 账号（`id=3`），学生接口保持 `STUDENT` 身份，新增管理员访问学习进度 403 检查；
  - `M1-T7`：教学统计 SQL 护栏测试，新增 `TeachingDashboardMapperRoleTest` 反射校验个人指标 SQL 均已 join `sys_user.role='STUDENT'`；
  - `M1-T8`：评价统计 STUDENT 样本护栏测试，新增 `EvaluationMapperRoleTest` 反射校验评价统计与导出 SQL 均锁定 STUDENT 角色且公共 DTO 绝不泄露 userId；
  - `M1-T9`：实验分组派发学生身份护栏，在 `ExperimentTaskService` 将方法安全注解从 `isAuthenticated()` 收敛强化为 `hasRole('STUDENT')`。
- 门禁实测通过情况：
  - 前端全量测试：51 个测试文件，117 项测试全部通过；
  - 前端生产构建：`npm run build` 成功完成，0 chunk warning；
  - 后端 M1 核心测试集（RoleBoundaryMvcTest、AdminStudyWorkflowTest、TeachingDashboardMapperRoleTest、TeachingDashboardServiceTest、EvaluationMapperRoleTest、EvaluationServiceTest、ExperimentTaskServiceTest）：54 项全部通过；
  - 后端全量测试：learning-service（102 项通过）、ai-service（通过）、learning-gateway（5 项通过），三模块 BUILD SUCCESS；
  - 代码质量与规范：`git diff --check` 通过；全局 CSS 与 Vue 模板 `!important` 扫描结果为 0；
  - 施工任务书：`M1-T1` ~ `M1-T9` 全部勾选为 `[x]`。
- 严格遵循红线与纪律：未修改外部 Mecha-QA、Python Bridge、NebulaGraph 图空间与模型权重，未透出 RANKED 后台固定证据约束系统提示词，未删改或弱化既有测试。本地合并提交，暂不推送远程。

## 里程碑 M2 推进记录（第一/二批：M2-T1 ~ M2-T7，2026-11-24）

- 贯彻施工任务书【里程碑 M2】全站视觉美学重构与 CSS 净瘦身，采用分批 checkpoint 提交模式稳健推进。
- 第一批已锁定本地提交（`4fcded7`：`style(ui): establish css ownership and element-plus theme bridge (M2-T1..M2-T3)`）：
  - `M2-T1`：`styles.css` 成为壳层布局唯一 owner，合并 900px/620px 响应式壳选择器；
  - `M2-T2`：`main.ts` 按严格顺序引入 Element Plus 组件级样式；`styles-design-system.css` 完整补齐 `--el-*` 变量映射体系；原生 baseline 限制于 `:is(.app-shell, .login-page)`；`compliance-guard.test.ts` 扩展管理端与微课/评价 copy 守卫；
  - `M2-T3`：`styles-graph.css` 移出通用 spinner，添加 `.graph-page{min-width:0}`；新建 `GraphEvidenceDrawer.test.ts`（覆盖真实三元组证据、空提示及 disclosure 展开折叠）。
- 第二批完成并验证（`M2-T4` ~ `M2-T7`）：
  - `M2-T4`：学习域 CSS 重复与空态统一。将通用 `.empty-state`、`.loading-row`、`.empty-check` 以及 `@media (prefers-reduced-motion:reduce)` 迁入 `styles-design-system.css`；从 `styles-learning.css` 剥离非学习域通用反馈，使其纯粹内聚于 quiz/wrong/review/plan/recommend/experiment，单文件净瘦身 700 字节；
  - `M2-T5`：登录与学生总览视觉层级精修。`DashboardView.vue` 将“问问 AI 助教”调整为次级按钮样式（`button-outline`），保持唯一深色“继续上次学习”主行动点清晰；强化视图层级测试；
  - `M2-T6`：教学总览 KPI 与图表层级精修。`TeachingDashboardView.vue` KPI 指标严格按照 activeLearners / accuracy / completedReads / averageMastery 为主数，错题待复习呈现为“2 次”；补充 KPI 顺序与真实空态测试；
  - `M2-T7`：课程阅读与笔记单层工作台。`CourseView.vue` 目录支持粘顶滚动与移动端折叠，正文限宽 `--reading-width`（72ch），切节路由 hash 联动；新增 `CourseView.test.ts` 验证目录、正文宽度类、笔记初始折叠态及切节路由 hash。
- 门禁实测结果：
  - 定向测试：11 个测试文件，26 项测试全部通过（26 passed）；
  - 生产构建：`npm run build` 成功完成，0 错误，0 chunk warning；
  - 代码格式与规范：`git diff --check` 通过；全仓 CSS/Vue 中 `!important` 严格保持为 0；
  - 任务书勾选状态：`M2-T1` ~ `M2-T7` 已全部勾选为 `[x]`。

## 里程碑 M2 第三批推进记录（M2-T8 ~ M2-T11，2026-11-24）

- 完成施工任务书【里程碑 M2】第三批共 4 项原子任务落地与定向验证：
  - `M2-T8`：图谱及证据抽屉。`VisualizationViewport.vue` 工具栏 action 固定高 40px、间距对齐；证据抽屉使用单层 surface，三元组卡片采用浅底分行排布；`GraphView.vue` 图谱离线/异常时诚实降级并展示空状态，绝不捏造假节点；扩展 `GraphView.test.ts`；
  - `M2-T9`：Three.js 与先修路径细节。`ThreeGraphCanvas.vue` 保持透明背景、阻尼拖动与生命周期注销；`PrerequisitePathPanel.vue` 连接线使用 `border-soft`，四类节点状态（已掌握/学习中/下一步/阻塞）浅底深字区分，当前目标增加非颜色星标与徽标指示，支持 320px 窄屏适配；扩展 `PrerequisitePathPanel.test.ts`；
  - `M2-T10`：Tutor 对话工作台布局。桌面保持会话列+消息列流式布局，策略说明采用原生 `<details>` 折叠收纳且徽标实时联动当前模式；会话列定高内部滚动；输入区预填实体上下文但不自动发送请求；扩展 `TutorView.test.ts` 覆盖会话操作、对比模式、分层提示与快速提问填词；
  - `M2-T11`：Tutor Answer/Mode/Markdown 子组件。`TutorModeSelector.vue` 模式切换按钮最小高度保持 44px 以上，对比模式显式样式区分；`TutorAnswerCard.vue` 证据三元组主体/谓词/客体网格对齐，严格保持 `RANKED` 模式不暴露后台系统提示词正文；`MarkdownContent.vue` 保持 HTML 转义不执行，提供复制代码键盘操作与失败回馈。
- 门禁实测结果：
  - 本批定向测试与合规守卫：11 个测试文件，30 项测试全部通过（30 passed）；
  - 生产构建：`npm run build` 成功完成，0 错误，0 chunk warning，`graph-three` 与 `graph-g6` chunk 分包清晰；
  - 代码规范：`git diff --check` 通过；全仓 CSS/Vue 中 `!important` 扫描结果为 0；
  - 任务书勾选状态：`M2-T8` ~ `M2-T11` 全部勾选为 `[x]`。

## 里程碑 M2 第四批推进记录（M2-T12 ~ M2-T15，2026-11-24）

- 完成施工任务书【里程碑 M2】第四批共 4 项原子任务落地与定向验证：
  - `M2-T12`：课程练习与自适应练习。题干字号统一调整为 18px，选项整行可点（高 48px），选项选中/正确/错误状态均提供文字+图标双编码（正确 Check、错误 CircleX），判题后保持高对比度（opacity 为 1）；提交前绝不泄露正确答案；扩展 `PracticeQuestionCard.test.ts`；
  - `M2-T13`：错题本与间隔复习。错题列表搜索在统一工具条流中，行内容题干、错误次数、状态、定向复习和已掌握动作分列；`ReviewView.vue` 保持单题聚焦，选项补充错误图标双编码，在学生提交复习前绝不泄露正确答案与解析；暂停/恢复/评分 requestKey 保持业务兼容；
  - `M2-T14`：学习计划与规则推荐。学习计划预算/筛选工具条与任务列表排布规范，complete/skip 仅在 PENDING 状态可操作；`RecommendationsView.vue` 推荐理由完整换行不截断，严格使用“基于掌握度、错题与先修关系的可解释规则推荐”合规措辞，不出现“机器学习”“预测掌握度”；扩展 `RecommendationsView.test.ts`；
  - `M2-T15`：报告与发布微课阅读。学习报告严格按 KPI→活动趋势→章节/知识点表格顺序呈现，数据统一使用 tabular-nums，导出 CSV 为唯一深色主按钮；微课页面 Markdown 文本限宽 72ch 与 1.7 行高，关键证据三元组与常见错因采用浅底卡片分组。
- 门禁实测结果：
  - 本批定向测试与合规守卫：10 个测试文件，25 项测试全部通过（25 passed）；
  - 生产构建：`npm run build` 成功完成，0 错误，0 chunk warning；
  - 代码规范：`git diff --check` 通过；全仓 CSS/Vue 中 `!important` 扫描结果为 0；
  - 任务书勾选状态：`M2-T12` ~ `M2-T15` 全部勾选为 `[x]`。

## 里程碑 M2 第五批推进记录（M2-T16 ~ M2-T19，2026-11-24）

- 完成施工任务书【里程碑 M2】第五批共 4 项原子任务落地与定向验证：
  - `M2-T16`：课程与基础题库管理视图。`AdminCoursePanel.vue` 编辑表单与课程列表网格设为 `repeat(2, minmax(0, 1fr))`，内容贴顶对齐，消除固定高容器；`AdminView.vue` 接收可选 `embedded` prop 并传给 `SectionHeading :embedded`，使其在内容中心中渲染为嵌入态 H2，而在 standalone 路由中保持 H1；
  - `M2-T17`：错因诊断与版本题库视图。`AdminDiagnosisView.vue` 工具栏采用弹性流布局，`SectionHeading` 响应 `embedded` prop；`QuestionBankView.vue` 接收 `embedded` prop 并切换 H1/H2 标题，题库导入 JSON 编辑区设置内部滚动，文件上传选择器支持键盘聚焦导航；
  - `M2-T18`：微课与实验任务管理员。`MicroLessonAdminView.vue` 工具栏、数据源及草稿记录垂直间距规范为 8/16/24 spacing，草稿列表采用单层 surface，各条草稿记录以 divider 分隔，移除白卡套白卡；`MicroLessonAdminView.vue` 与 `ExperimentTaskAdminView.vue` 接收 `embedded` prop 并将主标题降级为 H2；扩展两个组件的单元测试；
  - `M2-T19`：学生评估向导视觉与任务入口。`EvaluationView.vue` 新增 6 阶段评估向导视觉 stepper（知情说明→前测→任务/计时→后测→Paas→延后测验），不可达步骤视觉置灰并标明“待开始评估”或“待解锁”；Paas 九点心理努力评分保持横排等宽/窄屏 3 列、每格 >= 48px，选中后展示编号与锚点文字辅助；`ExperimentTasksView.vue` 任务指定问题仅取自 assigned DTO，绝不暴露管理员列表或身份；文案严格剔除采集生理信号或先验降低认知负荷的学术偏颇措辞；扩展单元测试。
- 门禁实测结果：
  - 本批定向测试与合规守卫：9 个测试文件，29 项测试全部通过（29 passed）；
  - 生产构建：`npm run build` 成功完成，0 错误，0 chunk warning；
  - 代码规范：`git diff --check` 通过；全仓 CSS/Vue 中 `!important` 扫描结果为 0；四份全局 CSS 字节数完全保持稳定（总计 57,983 B）；
  - 任务书勾选状态：`M2-T16` ~ `M2-T19` 全部勾选为 `[x]`。

## 里程碑 M2 终局收口记录（M2-T20 ~ M2-T23 与 M2 完整收口门禁，2026-11-24）

- 完成施工任务书【里程碑 M2】终局批次 4 项原子任务落地与全量门禁验收：
  - `M2-T20`：管理统计表与匿名导出视图。`EvaluationAdminView.vue` 添加语义 `<caption>`、表头 `scope="col"`、数据行 `scope="row"`，所有统计指标启用 tabular-nums；小屏横向滚动区域添加 `tabindex="0"` 和 `aria-label`；严格保持去标识化展示与匿名 CSV 导出，绝不泄露学生姓名、学号或网络标识；扩展 `EvaluationAdminView.test.ts`；
  - `M2-T21`：实验结果网格和结果历史。`ExperimentResultGrid.vue` 保持 CSS Subgrid 4 直接子元素结构，严格保持 `result.mode !== 'RANKED' && result.resultKey !== 'RANKED'` 隐藏后台提示词正文，失败实验卡片诚实展示错误原因与耗时，严禁渲染虚假评分；`ExperimentsView.vue` 采用 run/summary/history 语义选项卡与首屏流式布局；扩展 `ExperimentResultGrid.test.ts` 与 `ExperimentsView.test.ts`；
  - `M2-T22`：两个管理员工作台密度与可访问性。`AdminContentCenterView.vue` 与 `AdminExperimentCenterView.vue` 统一标题层级（单个 H1，子视图 H2），采用浅底分组表面消除白卡套白卡，Active Tab 与路由保持同步并在视口内居中；扩展 `AdminCentersView.test.ts` 与 `AppRoleNavigation.test.ts`；
  - `M2-T23`：AST 级幽灵类名验收工具。新增 `frontend/scripts/scan-vue-classes.mjs`，依赖 `@vue/compiler-sfc`、`@babel/parser`、`postcss` 与 `css-tree` 提取全仓 41 个 Vue SFC 模板类名并与全局/局部 CSS 选择器比对；排查并修复全仓 14 处真实缺失类名与冗余幽灵类，达到 **0 missing classes**；`tests/design-system-contract.test.ts` 扩展 AST 检查集成断言。
- 【里程碑 M2 完整收口门禁】实测结果：
  - AST 幽灵类名扫描：`node scripts/scan-vue-classes.mjs --check` 检查 41 个 SFC，0 missing classes，0 dynamicUnknown，0 allowlisted，通过；
  - 全量前端测试：`npm test`（53 个测试文件，137 项测试全绿，137 passed），通过；
  - 生产构建：`npm run build` 成功完成，0 error，0 chunk warning，分包清晰；
  - 真实运行态视觉巡检：`node scripts/authenticated-ui-smoke.mjs` 覆盖双角色/访客 24 条路由共 158 个场景，158/158 零失败，巡检临时目录已彻底清理；
  - 样式质量与规范：`git diff --check` 通过；全仓 CSS/Vue 中 `!important` 严格为 0；四份全局 CSS 最终大小为 58,263 B（`styles-design-system.css` 28,734 B、`styles.css` 14,843 B、`styles-graph.css` 3,297 B、`styles-learning.css` 11,389 B）；
  - 任务书勾选状态：【里程碑 M2】全量 23 项原子任务（`M2-T1` ~ `M2-T23`）已全部标记为 `[x]`！

## 里程碑 M3 第一批推进记录（M3-T1 ~ M3-T3，2026-11-24）

- 完成施工任务书【里程碑 M3】第一批共 3 项原子任务落地与定向验证：
  - `M3-T1`：V16 为实验组持久化顺序。新增 `V16__experiment_group_task_order.sql` 为 `experiment_group` 添加 nullable `task_order VARCHAR(3)` 字段；更新 `ExperimentGroupEntity.java` 实体；新建 `ExperimentTaskOrderMigrationTest.java` 验证从 V14 基线经由 V15/V16 迁移后旧组数据保持 NULL、新列可读写 `A→B`/`B→A`；
  - `M3-T2`：Assignment DTO 返回服务器顺序。`ExperimentAssignmentMapper.findForUser` 联查 `experiment_group` 并提取 `g.task_order AS taskOrder`；从学生 DTO `StudentAssignmentView` 和 SQL projection 中彻底删除 `userId` 与 `referenceAnswer`，断言学生投影绝不暴露真实答案或他人身份；扩展 `ExperimentTaskServiceTest.java`；
  - `M3-T3`：管理端组读取与派发原子性。`ExperimentTaskController.java` 新增 ADMIN-only `GET /api/admin/experiment-tasks/{id}/groups`；`addGroup` 严格校验 `taskOrder` 必须为 `'A→B'` 或 `'B→A'`（非法或空返回 400）；`assign` 实现 1–500 正整数校验、重复 ID 拦截、task/group 归属校验及全部用户存在且为 STUDENT 的预校验，任一非法整批 0 写入，全量合法使用 `INSERT IGNORE` 幂等派发；扩展 `ExperimentTaskServiceTest.java` 覆盖 10 项单元测试场景。
- 门禁实测结果：
  - 后端定向与全量测试：`mvn -f backend/pom.xml -pl learning-service test` 152 项测试全绿通过（152 passed）；
  - 前端契约与合规测试：`tests/design-system-contract.test.ts` 与 `tests/compliance-guard.test.ts` 全部通过（11 passed）；
  - 样式与代码规范：`scan-vue-classes.mjs --check` 0 missing classes；`!important` 扫描结果为 0；`git diff --check` 通过；
  - 任务书勾选状态：`M3-T1` ~ `M3-T3` 已全部标记为 `[x]`。

## 里程碑 M3 第二批推进记录（M3-T4 ~ M3-T6，2026-11-24）

- 完成施工任务书【里程碑 M3】第二批共 3 项原子任务落地与定向验证：
  - `M3-T4`：管理端分组/顺序与学生 ID 派发 UI。`ExperimentTaskAdminView.vue` 展示各任务关联的真实实验组列表及其 `taskOrder` 徽标；新增实验组表单必选 `A→B` / `B→A` 顺序，明示其为预设实验协议步骤；学生 ID 派发支持 1–500 正整数逗号/换行输入解析与重复校验，二次确认弹窗明示目标任务/组名/顺序/总人数，取消 0 请求，400 失败保留原输入；扩展 `ExperimentTaskAdminView.test.ts`（7 项测试全绿）；
  - `M3-T5`：指派学生实验的服务端 Rubric 与答案隔离。`ExperimentTaskService.java` 新增内部 `AssignedRunSpec` 及 `getAssignedRunSpec(userId, taskId)` 严格校验任务归属与 OPEN 状态；`AiExperimentController.java` 对学生请求以 taskId 为准，彻底忽略客户端伪造的 question/rubric，并在返回结果中脱敏剔除 `referenceAnswer` 与 `expectedEntities`；`GET /api/experiments`、导出与评分端点严格限制为 ADMIN-only；新建 `AiExperimentControllerTest.java` 并更新 `AiExperimentResponseGuardTest.java` 与 `ExperimentExportTest.java`；
  - `M3-T6`：学生分配任务受限实验运行页。`router.ts` 新增 STUDENT-only `/experiment-run` 指向 `ExperimentsView`（`studentTaskMode=true`），`EvaluationView.vue` 任务链接指向 `/experiment-run` 并保留 `taskId` 与 `assignmentId`；`ExperimentsView.vue` 学生模式下通过 `experimentAssignments` 获取当前学生分配任务并只读展示题干，彻底隐藏参考答案、期望实体、实验方案、消融选项、历史/统计 tabs、导出按钮与人工评分；学生点击运行仅向后端发送 `{ taskId }`，失败照实呈现并允许重试；更新 `EvaluationView.test.ts` 与 `ExperimentsView.test.ts`（13 项测试全绿）。
- 门禁实测结果：
  - 后端全量测试：`mvn -f backend/pom.xml -pl learning-service test` 157 项测试全绿通过（157 passed，0 failed，0 error）；
  - 前端定向与契约测试：`EvaluationView.test.ts`（4/4）、`ExperimentsView.test.ts`（9/9）、`ExperimentTaskAdminView.test.ts`（7/7）、`design-system-contract.test.ts`（7/7）、`compliance-guard.test.ts`（4/4）全量通过；
  - 前端 AST 类名检查：`node scripts/scan-vue-classes.mjs --check` 扫描 48 个 SFC 模板（1078 处类名），0 missing classes；
  - 前端生产构建：`npm run build` 成功完成，0 error，0 chunk warning，分包清晰；
  - 样式与代码规范：`git diff --check` 通过；全仓 CSS/Vue 中 `!important` 扫描严格为 0；
  - 任务书勾选状态：`M3-T4` ~ `M3-T6` 全部勾选为 `[x]`。

## 里程碑 M3 第三批推进记录（M3-T7 ~ M3-T10，2026-11-24）

- 完成施工任务书【里程碑 M3】第三批共 4 项原子任务落地与定向验证：
  - `M3-T7`：评估顺序以 assignment group 为权威。`ExperimentAssignmentMapper.java` 新增 `findDetailForUser(assignmentId, userId)`，按 assignmentId+userId 联查 group/task 返回 order/status；`EvaluationService.start` 对 assignment-linked session 确立 server order 为唯一权威，客户端篡改拒绝 400，非 OPEN 任务、已完成 assignment 或 legacy NULL 顺序拒绝 409，未分配 session 保持客户端指定顺序；扩展 `EvaluationServiceTest.java`；
  - `M3-T8`：服务端权威用时。不改 schema/controller，客户端 `elapsedSeconds` 保持必传契约但 service 完成时忽略其值；assignment-linked 会话以 `experiment_assignment.started_at` 为起点，未关联会话以 `learning_evaluation_session.started_at` 为起点，通过 `Duration.between` 严格计算 1–86400 秒权威用时，缺起点或越界拒绝 400；扩展 `EvaluationServiceTest.java`；
  - `M3-T9`：问答运行不得提前完成实验分配。`AiExperimentController.java` 彻底移除实验运行成功后调用 `completeRun` 的提前结单副作用；`bindRun` 仅将任务从 ASSIGNED 推进至 IN_PROGRESS；任意实验 run success/failure 都不触发 assignment 完成；更新 `ExperimentTaskServiceTest.java` 并扩展 `AiExperimentControllerTest.java`；
  - `M3-T10`：提交评估后才完成 assignment。`EvaluationMapper.java` 新增 owner-scoped `hasMeasurement(userId, sessionId, phase)` 与 `listOwnedPhaseStatuses(userId)`；`EvaluationService.mine` 以两次查询组装 `SessionView.completedPhases`，杜绝跨用户数据污染；assignment-linked 评估完成必须严格满足 PRE + POST + 1–9 心理努力评分 + 同一 owner assignment 持久化成功 run，缺条件拒绝 409，全部通过在同一事务内完成会话并调用 `taskService.completeRun(userId, assignmentId, true)`；新建 `EvaluationMapperProgressIntegrationTest.java`（H2 MySQL mode，2/2 passed）并扩展 `EvaluationServiceTest.java`。
- 门禁实测结果：
  - 本批组合定向测试：`EvaluationServiceTest`（15/15）、`EvaluationMapperProgressIntegrationTest`（2/2）、`EvaluationMapperRoleTest`（4/4）、`ExperimentTaskServiceTest`（10/10）、`AiExperimentControllerTest`（5/5）、`ComplianceGuardTest`（3/3）、`RoleBoundaryMvcTest`（6/6）共 45 项全部通过（45 passed，0 failed）；
  - 后端全量测试：`mvn -f backend/pom.xml -pl learning-service test` 168 项测试全绿通过（168 passed，0 failed，0 error）；
  - 前端契约与 AST 类名检查：`design-system-contract.test.ts` 与 `compliance-guard.test.ts` 全部通过（11 passed）；`scan-vue-classes.mjs --check` 0 missing classes；本批未改动前端代码；
  - 样式与代码规范：`git diff --check` 通过；全仓 CSS/Vue 中 `!important` 扫描严格为 0；
  - 任务书勾选状态：`M3-T7` ~ `M3-T10` 全部勾选为 `[x]`。

## 里程碑 M3 第四批推进记录（M3-T11 ~ M3-T14，2026-11-24）

- 完成施工任务书【里程碑 M3】第四批共 4 项原子任务落地与定向验证：
  - `M3-T11`：服务端固定实验案例目录。新建 `FixedExperimentSuite.java` 静态内置 V4 seed 8 个基准测试案例（逐字匹配题干、来源小节与知识点），绝不包含答案/解析/rubric/prompt 等敏感属性；新建 `FixedExperimentSuiteController.java` 暴露 ADMIN-only `GET /api/admin/experiments/fixed-suite`；新建 `FixedExperimentSuiteTest.java` 覆盖 8 个 case 逐字校验、非敏感属性断言、ADMIN 200 与 STUDENT 403 权限边界校验（10 项测试全绿）；
  - `M3-T12`：前端加载并选择固定实验案例。`frontend/src/api/types.ts` 新增 `FixedExperimentCase` 与 `FixedExperimentSuite` 类型，`frontend/src/api/experiment.ts` 导出 `getFixedExperimentSuite`；`ExperimentsView.vue` 管理端挂载时载入 8 个 manifest cases，默认全选，支持全选/清空/单选，展示 caseId、sourceSection、knowledgePoint 与 question（纯只读文本，绝无答案/参考答案框），支持将题目填入单题输入框；接口异常时给出明确错误提示与“重试加载”按钮；学生受限模式（`studentTaskMode`）严格隐藏案例目录；
  - `M3-T13`：固定案例串行批量运行。`ExperimentsView.vue` 支持对选中的固定案例进行串行批量运行，依次调用 `/api/experiments/run`，请求仅发 question，referenceAnswer 与 expectedEntities 留空，未生成 reference 指标；提供实时进度指示（当前索引、耗时、成功数、失败数）；失败项诚实标记并保留错误信息，失败继续执行不阻塞后续案例；支持中途安全取消（保留已完成项）与仅重试失败项；已成功项不重复执行；
  - `M3-T14`：批量结果 UTF-8 CSV/JSON 导出。`frontend/src/domain/experimentExport.ts` 为导出结果增加 `suiteVersion`、`caseId`、`sourceSection`、`batchId`、`batchStatus` 扩展字段；CSV 导出严格带 UTF-8 BOM（`\uFEFF`）与 CRLF 换行；JSON 导出支持白名单字段重建和可选 `batch` 汇总元数据，彻底剥离学生 PII、参考答案与后台系统提示词正文；扩展 `experimentExport.test.ts` 覆盖 BOM、白名单脱敏、失败行空值处理等测试。
- 门禁实测结果：
  - 后端全量测试：`mvn -f backend/pom.xml -pl learning-service -am test` 172 项测试全部通过（172 passed，0 failures，0 errors）；
  - 前端全量测试：`npx vitest run` 53 个测试文件、156 项测试全部通过（156 passed，0 failures）；
  - 前端 AST 类名检查：`node scripts/scan-vue-classes.mjs --check` 扫描 48 个 SFC 模板（1119 处类名使用），0 missing classes；
  - 前端生产构建：`npm run build` 成功完成，0 error，0 chunk warning；清理了 `ExperimentsView.vue` 中重复的 `<style scoped>` 块，文件体积减少近半；
  - 样式与代码规范：`git diff --check` 通过，0 处空白错误；全仓 CSS/Vue 中 `!important` 严格为 0；
  - 任务书勾选状态：`M3-T11` ~ `M3-T14` 全部勾选为 `[x]`。

## 里程碑 M3 第五批推进记录（M3-T15 ~ M3-T18，2026-11-24）

- 完成施工任务书【里程碑 M3】第五批共 4 项原子任务落地与定向验证：
  - `M3-T15`：学生领取任务读取真实分组顺序。`frontend/src/views/ExperimentTasksView.vue` 严格以服务端返回的 `taskOrder` 为唯一权威呈现实验顺序；若为 legacy NULL 则提示“顺序未记录”并禁用评估入口，不默认假设 A→B；移除开始前提前触发的 `POST /start`，点击直接携带 `taskId` 与 `assignmentId` 导航至 `/evaluation`；扩展 `ExperimentTasksView.test.ts`（3 项测试全绿）；
  - `M3-T16`：评估分步向导状态机。`frontend/src/views/EvaluationView.vue` 完整实现 6 步严格状态机：`consent` → `PRE` → `assigned run` → `POST` → `Paas 1–9` → `optional DELAYED`；知情同意前绝不预先创建评估会话；前测未保存前严格阻塞任务入口（`step-blocked`）和后测填写；未完成后测与 1–9 心理努力自评前严格阻断 final complete 提交；页面挂载自动根据服务端 `completedPhases` 恢复步骤，断言本地未提交数据不虚标 complete；接口异常保留当前步骤与输入并支持原位重试；扩展 `EvaluationView.test.ts`（6 项测试全绿）；
  - `M3-T17`：跨页学习上下文回链。`frontend/src/domain/learningContext.ts` 扩展 `updatedAt` 时间戳并保持对无 `updatedAt` 的旧本地缓存兼容；提供 `returnHref` 在缺失上下文时安全回退至 `/courses`，绝不调用无保护的 `history.back()`；增强 `frontend/src/components/LearningContextActions.vue` 呈现“课程→小节→知识点”面包屑路径与“返回此学习内容”链接，支持基于 `currentView` 进行当前目标智能去重；新建 `LearningContextActions.test.ts` 与更新 `learningContext.test.ts`（8 项测试全绿）；
  - `M3-T18`：Tutor 知识点上下文预填。`frontend/src/views/TutorView.vue` 对 point-only context 调用 `knowledgePoints(courseId)` 精准匹配知识点名称并预填入输入框，绝不自动触发 submit 发送；`route.query.entity` 优先且不覆盖用户已有输入；未知 point ID、接口失败或加载中不编造虚假名称；quick prompts 自动联动解析名称；`SectionHeading` 集成 `LearningContextActions`；扩展 `TutorView.test.ts` 覆盖 exact match、unknown ID、API failure、editable prompt 等场景（8 项测试全绿）。
- 门禁实测结果：
  - 本批关联测试：`ExperimentTasksView.test.ts`（3/3）、`EvaluationView.test.ts`（6/6）、`learningContext.test.ts`（4/4）、`LearningContextActions.test.ts`（4/4）、`TutorView.test.ts`（8/8）共 25 项测试全部通过；
  - 前端全量测试：`npx vitest run` 54 个测试文件、168 项测试全部通过（168 passed，0 failures）；
  - 前端 AST 类名检查：`node scripts/scan-vue-classes.mjs --check` 扫描 48 个 SFC 模板（1134 处类名使用），0 missing classes（100% 匹配）；
  - 前端生产构建：`npm run build` 成功完成，0 error，0 chunk warning，类型检查通过；
  - 样式与代码规范：`git diff --check` 通过，0 处空白错误；全仓 CSS/Vue 中 `!important` 严格为 0；
  - 任务书勾选状态：`M3-T15` ~ `M3-T18` 全部勾选为 `[x]`。

## 里程碑 M3 第六批推进记录（M3-T19 ~ M3-T21，2026-11-24）

- 完成施工任务书【里程碑 M3】第六批共 3 项原子任务落地与双端定向验证：
  - `M3-T19`：当前账户修改密码与刷新令牌撤销。`AuthController.java` 暴露 authenticated `PATCH /api/auth/password`，固定请求体 `{currentPassword, newPassword}`，严格由安全主体解析当前用户；`AuthService.java` 实现新密码校验（至少 8 个 Unicode code points、UTF-8 编码 bytes `<=72`、空白拒绝），校验旧密码 BCrypt 匹配（不匹配返回 401）；事务内 BCrypt 更新并调用 `RefreshTokenMapper.revokeAllActiveByUserId` 撤销该用户所有尚有效的 refresh token（仅更新 `revoked_at`，不返回任何 token/hash），成功返回 204；扩展 `AuthApiIntegrationTest.java` 覆盖未认证 401、错当前密码 401、新密码规则 400、本人所有有效 refresh token 被撤销、其他用户 refresh token 保持有效、新旧密码登录状态机闭环（10 项集成测试全绿）；
  - `M3-T20`：前端修改密码安全对话框。`frontend/src/api/auth.ts` 导出 `changePassword`；新建 `ChangePasswordDialog.vue` 对话框，包含当前密码、新密码、确认密码，校验空白、密码一致性与客户端 Unicode 长度 `>=8`，提交失败不回显密码、清空当前旧密码且保留新密码；成功后彻底清理 4 个 localStorage 本地凭据（`slp-access-token`, `slp-refresh-token`, `slp-user-name`, `slp-user-role`），调用 `resetSession` 与 `resetLearningMetrics` 并路由重定向至 `/login`；`App.vue` 用户菜单集成“修改密码”弹窗入口与下拉面板；新建 `ChangePasswordDialog.test.ts`（6 项测试全绿）；
  - `M3-T21`：Refresh 请求 Single-flight 回归。`frontend/src/api/http.ts` 完善 `clearSession` 同步清除 `slp-user-role` 并安全处理测试环境跳转；扩展 `frontend/src/api/client.test.ts` 增加并发 deferred adapter 回归测试，断言 5 个并发请求同时收到 401 时仅发出唯一次 `/api/auth/refresh` 请求，成功后每个原请求均使用新 token 恰好重放 1 次；refresh 失败时一次性清理本地会话并拒绝所有等待中的请求；重试后依然 401 停止重试，杜绝死循环（8 项测试全绿）。
- 门禁实测结果：
  - 后端全量测试：`mvn -f backend/pom.xml -pl learning-service test` 175 项测试全绿通过（175 passed，0 failures，0 errors）；
  - 前端全量测试：`npx vitest run` 55 个测试文件、177 项测试全绿通过（177 passed，0 failures）；
  - 前端设计系统契约与安全合规守卫：`design-system-contract.test.ts` 与 `compliance-guard.test.ts` 全部通过（11 passed）；
  - 前端 AST 类名检查：`node scripts/scan-vue-classes.mjs --check` 扫描 49 个 SFC 模板（1155 处类名使用），0 missing classes（100% 匹配）；
  - 前端生产构建：`npm run build` 成功完成，0 error，0 chunk warning，类型检查通过；
  - 样式与代码规范：`git diff --check` 通过，0 处空白错误；全仓 CSS/Vue 中 `!important` 严格为 0；
  - 任务书勾选状态：`M3-T19` ~ `M3-T21` 全部标记为 `[x]`。

## 里程碑 M3 终局推进与收口门禁记录（M3-T22 ~ M3-T25 与 M3 收口，2026-11-24）

- 完成施工任务书【里程碑 M3】最后 4 项原子任务与 M3 完整收口门禁：
  - `M3-T22`：未保存表单离开确认原语。`frontend/src/composables/useUnsavedChanges.ts` 实现 `useUnsavedChanges` 和 `useFormBaseline`，支持浏览器同步 `beforeunload`（纯同步 preventDefault/returnValue 阻断）、Vue Router 路由切换拦截（兼顾顶级路由页面与嵌入式 Tab 组件）、以及手动 `confirmIfDirty`，离开时异步确认，取消即终止路由跳转；保存成功更新 baseline；新建 `useUnsavedChanges.test.ts`（6 守护测试全绿）；
  - `M3-T23`：Admin 课程与错因诊断表单 dirty guard。`AdminView.vue` 对课程与题目草稿做深度基线比较，new/edit/save/reset 均更新 baseline；`AdminDiagnosisView.vue` 对错因配置草稿做基线对比，改变课程/题目若 dirty 弹出确认，取消则恢复 select 原值并保留草稿；扩展 `AdminCourseViews.test.ts` 与 `AdminDiagnosisView.test.ts`；
  - `M3-T24`：题库导入 dirty guard。`QuestionBankView.vue` 引入 `useFormBaseline` 与 `useUnsavedChanges`，对 `versionName/sourceFilename/questionsJson` 深度比较，创建/导入成功更新 baseline，失败保留 dirty 与 payload；扩展 `QuestionBankView.test.ts`（4 项测试全绿）；
  - `M3-T25`：ExperimentTask draft dirty guard。`ExperimentTaskAdminView.vue` 引入防丢守卫，对 `title/question/description/referenceAnswer` 相比空 baseline 进行 dirty 判定，创建成功清空并重置 baseline，失败保留输入与 dirty；`OPEN/CLOSED` 沿用状态二次确认且不触发草稿守卫；扩展 `ExperimentTaskAdminView.test.ts`（9 项测试全绿）。
- 【里程碑 M3 完整收口门禁实测结果】：
  - 前端专项相关测试：`npx vitest run src/views/ExperimentsView.test.ts ...` 共 12 个测试文件、78 项测试 100% 通过（78 passed）；
  - 前端全量测试：`npm test` 56 个测试文件、189 项测试全部通过（189 passed，0 failure）；
  - AST 幽灵类名扫描：`node scripts/scan-vue-classes.mjs --check` 扫描 49 个 SFC、1155 处类名使用，0 missing classes（100% 匹配）；
  - 前端生产构建：`npm run build` 成功完成，0 error，0 chunk warning；
  - 后端 M3 专项集成测试：`mvn -f backend/pom.xml -pl learning-service -am ...` 44 项测试全部通过（44 passed，0 failure，0 error）；
  - 后端三模块全量回归测试：`mvn -f backend/pom.xml test` 全部通过（BUILD SUCCESS，总计 201 个测试）：
    - `learning-service`: 94 tests, 0 failures, 0 errors, 0 skipped
    - `ai-service`: 102 tests, 0 failures, 0 errors, 0 skipped
    - `learning-gateway`: 5 tests, 0 failures, 0 errors, 0 skipped
  - 运行态视觉巡检：`node scripts/authenticated-ui-smoke.mjs` 覆盖全部角色场景，158/158 零失败，巡检临时目录 `.ui-visual-review` 已彻底清理；
  - 代码规范与合规：`git diff --check` 通过（0 处空白错误）；全仓 CSS/Vue 中 `!important` 严格为 0；
  - 任务书勾选状态：【里程碑 M3】全量 25 项原子任务（`M3-T1` ~ `M3-T25`）已全部标记为 `[x]`！

## 里程碑 M4：文档对齐与终版验收完成记录（2026-11-24）

- **前序 HEAD**：`a33b1d817e2ea74bb49ca2b40fcd12a831d1f5bb`（M3 完整收口提交）；
- **完成任务项**：
  - `M4-T1`：README 与 API 合同对齐。同步更新 `README.md` 与 `docs/api.md`，对齐学生端 5 大核心模块与管理端 3 大工作台中心路由体系；增加 `PATCH /auth/password`、`GET /admin/experiments/fixed-suite`（ADMIN-only，仅暴露 8 个固定案例元数据，无标准答案）、V16 组间评估顺序字段与向导状态机闭环；末尾声明 V16 为终版架构，不存在 V17 迁移；
  - `M4-T2`：UI 设计说明与答辩剧本对齐。`docs/ui-design.md` 记录四份 CSS 职责与精确体积、表单防丢离开确认、修改密码弹窗、固定案例目录与纯浏览器批次队列、评估向导流转及 170/170 视觉基线；`docs/答辩现场演示剧本与故障应急清单.md` 对齐 Canonical 路由点击路径与应急处置方案，严格锁定学术红线；
  - `M4-T3`：测试报告与工程验收报告同步。`docs/test-report.md` 与 `docs/毕业设计工程验收报告.md` 追加 2026-11-24 终版验收章节，严格填写真实实测数据（后端 282 项、前端 189 项、Smoke 170 场景、四 CSS 58,955 B），明确区分 API fixture smoke 运行态与 real backend integration，未在本地执行的真实独立 MySQL/外部二进制工具标记为 `not run`；
  - `M4-T4`：Continuation ledger 与 smoke matrix 锁定。更新本台账，锁定 `frontend/scripts/authenticated-ui-smoke.mjs`（170 场景矩阵）、`frontend/tests/design-system-contract.test.ts` 契约测试，完成所有 61 项原子任务勾选。
- **全量门禁实测数据锁定（重点纠偏与核对）**：
  - **Java 后端三模块测试**（OpenJDK 21）：总计 **282/282 全部通过**（纠偏上一轮构建日志截断导致的误读）：
    - `learning-service`: **175** tests, 0 failures, 0 errors, 0 skipped
    - `ai-service`: **102** tests, 0 failures, 0 errors, 0 skipped
    - `learning-gateway`: **5** tests, 0 failures, 0 errors, 0 skipped
  - **前端自动化测试**（Vitest）：**56 个测试文件、189 项测试全部通过**（189 passed，0 failures，0 errors）；
  - **前端生产分包构建**：`built in 10.59s`，**0 error，0 chunk warning**；
  - **AST 幽灵类名扫描**：49 个 SFC 视图组件，1,155 处模板类名引用，**0 missing classes（100% 匹配）**；
  - **双角色运行态视觉巡检（Smoke Matrix）**：
    - 脚本：`frontend/scripts/authenticated-ui-smoke.mjs`
    - 视口覆盖：桌面 `1440×900` 与便携笔记本 `1280×800`
    - 路由与场景覆盖：29 条页面路由 × 双角色 + 4 个新增专项场景（受限实验运行参考答案隔离、A→B 顺序展示、未保存草稿离开拦截、密码修改对话框校验）
    - 实测结果：**170/170 场景 100% 通过（85 student + 85 admin）**，0 控制台错误，0 页面异常，0 水平滚动条溢出，巡检生成的临时截图目录 `.ui-visual-review` 已彻底清理；
  - **全局四份 CSS 体系与代码卫生**：
    - `styles-design-system.css`: 28,734 B (0 `!important`)
    - `styles.css`: 15,535 B (0 `!important`)
    - `styles-learning.css`: 11,389 B (0 `!important`)
    - `styles-graph.css`: 3,297 B (0 `!important`)
    - 四 CSS 合计 58,955 B，全仓 `!important` 严格为 **0**，退出码 0；
  - **Docker Compose 配置校验**：`docker compose --env-file deploy/.env.server.example -f deploy/compose.server.yml config --quiet` 退出码 0，46 个环境变量插值完全合规；
  - **数据库迁移版本**：已应用 `V16__add_task_group_order.sql`；声明不存在 V17 迁移；
  - **学术合规与凭据自查**：合规守卫 4/4 通过；全仓无 RANKED 固定提示词泄露，无真实生产凭据泄露。
- **遗留风险与陈述边界**：
  - UI Smoke 巡检通过 Vite Preview 注入 Mock 响应断言前端排版与路由守卫，不替代全栈真实联调；
  - 正式毕业论文中的实验效果与认知负荷结论必须依托规范的实验流程、受试者样本与教师人工盲评，严禁将工程验收数据直接作为论文实验结论。
- **任务书完成度**：
  - 里程碑 M1（10 项）、M2（23 项）、M3（25 项）、M4（4 项）共 **61 项原子任务全部 100% 勾选为 `[x]`，全仓施工完美收官！**

## AI 学习助教界面产品化重构记录（2026-11-25）

- **产品目标与边界坚守**：
  - 面向日常学生高频学习场景重构 `/tutor` AI 学习助教界面，彻底剥离 `DIRECT` / `TRIPLES` / `RANKED` 三档位切换器、“策略说明”折叠区及多模式对比渲染；后端 Java 代码、外部 Mecha-QA、Python Bridge、NebulaGraph 图空间，以及实验模块（`ExperimentsView`、`/experiment-run`）的三档位对照与消融实验能力保持 100% 不受影响；
  - 前端静默锁定 `RANKED` 图谱增强模式请求既有流式接口（`/api/ai/ask/stream`），界面不暴露任何实验模式词与后台固定证据约束系统提示词正文，并兼容平滑渲染历史遗留不同模式对话正文与真实三元组证据（自动清理历史前缀）；
  - 严格保持服务异常时的诚实降级展示与重试入口，绝不伪造图谱证据或虚构回答。
- **核心界面与交互重构**：
  - **组件清理与更新**：确认全仓无其他页面引用后干净删除 `TutorModeSelector.vue`；重构 `TutorView.vue`、`TutorConversationList.vue`、`TutorMessageList.vue`、`TutorAnswerCard.vue`、`TutorComposer.vue` 及领域辅助工具 `domain/tutorChat.ts`；
  - **全视口双栏壳层**：通过 `styles.css` 中的 `.app-shell:has(.tutor-page)` 锁定 `100dvh` 视口高度避免页面内外双滚动条；左侧为 `256px` 可收起会话侧栏（桌面端一键折叠/展开、移动端抽屉收纳），会话行采用单行省略标题与 hover/聚焦浮现的重命名/删除按钮，固定列表内宽避免动画折叠过程挤压文案；
  - **现代对话流与引用胶囊**：无消息时居中展示简洁标题与 2×2 上下文动态推荐卡片（`1280×800` 及桌面视口完整一屏落位，点击填入不自动发送）；消息区居中限宽 `780px`，用户提问右对齐轻量气泡，助教回复采用去外框的纯净 Markdown 编辑排版；三元组证据默认折叠为“已参考 N 条课程图谱证据”引用来源胶囊，支持展开与一键跳转知识图谱；支持智能吸底滚动与圆形“回到底部”按钮；底层分层学习提示（1/2/3 级）平滑融合进对话流与输入胶囊中；
  - **底部悬浮胶囊输入区**：采用居中单层圆角胶囊、自动增高多行 `<textarea>`（`Enter` 发送、`Shift+Enter` 换行，严格检查 `event.isComposing`、`compositionstart/end` 与 `keyCode === 229` 防止中文输入法组合态误发送）、支持关闭的上下文标签与停止生成控制。
- **门禁与全量验收结果**：
  - **前端全量测试**（Vitest）：**56 个测试文件、195 项测试全部通过**（`TutorView.test.ts`、`TutorAnswerCard.test.ts`、`TutorComposer.test.ts`、`TutorConversationList.test.ts`、`TutorMessageList.test.ts`、`TutorHints.test.ts` 及 `compliance-guard.test.ts`、`design-system-contract.test.ts` 全绿）；
  - **AST 幽灵类名扫描**：`node scripts/scan-vue-classes.mjs --check` 扫描 48 个 Vue SFC、1,173 处模板类名引用，**0 missing classes（100% 匹配）**；
  - **全仓样式与代码规范**：全仓 CSS/Vue `!important` 扫描命中 **0**；`git diff --check` 零空白错误；四份全局 CSS 字节数：`styles-design-system.css` 28,734 B、`styles.css` 15,829 B（+294 B 视口锁规则）、`styles-learning.css` 11,389 B、`styles-graph.css` 3,297 B，合计 **59,249 B**；
  - **前端生产构建**：`npm run build` 耗时 `1.52s`，**0 error、0 chunk warning**；
  - **Playwright 全路由视觉巡检**：`node scripts/authenticated-ui-smoke.mjs` 在 `1440×900` 与 `1280×800` 双桌面视口覆盖全路由与助教无模式、证据折叠、侧栏折叠展开交互，实测 **170/170 场景全部通过**；巡检生成的临时目录 `.ui-visual-review` 已彻底清理。

