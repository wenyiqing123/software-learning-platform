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
