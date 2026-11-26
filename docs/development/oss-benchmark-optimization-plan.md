# 开源技术对标调研与合规优化方案矩阵

> 更新日期：2026-11-26  
> 目标工程：`D:/develop/PythonProject/pythonallwork/毕设/software-learning-platform`  
> 当前模式：**模式 A（调研 + 方案审批制，本阶段仅输出调研与方案矩阵，未修改任何业务代码）**

---

## 一、前置盘点：当前仓库真实基线核验

发起外部检索前已通读 `README.md`、`docs/api.md`、`docs/ui-design.md`、`docs/test-report.md`、`docs/development/continuation-ledger.md`、前端路由/视图/组件/域工具以及后端 `learning-service`（含 `db/migration`）与 `ai-service` 源码。

### 1.1 实际代码现状与文档快照差异

- **Git 当前 HEAD**：`45398b4`（`master` 分支工作区干净）。
- **数据库迁移现状**：Flyway 已连续演进至 **`V18__flashcard_review_state.sql`**（而非 `README.md` / `docs/api.md` 尚停留在 V17 的旧记录）。
- **代码规模现状**：
  - 前端共 **30 个视图**（`frontend/src/views/`）、**30 个组件**（`frontend/src/components/`）、**17 个领域模块**（`frontend/src/domain/`，含各自单元测试），AST 类名扫描覆盖 **61 个 Vue 文件**、**2014 处**模板类用法（`0 missing classes`，`0` `!important`）；
  - 前端自动化基线：**72 个 Vitest 测试文件 / 283 条测试通过**；Playwright 双视口 UI 巡检 **198/198 通过**；
  - 后端服务与契约：`learning-service`（31 个 Controller，V1–V18）+ `ai-service`（`AnswerService` 门面 + 三大 Coordinator + `TutorHintService` + `FeynmanEvaluationService` + `KnowledgeRetrievalEnhancer`）+ `learning-gateway`。

### 1.2 已落地能力排除清单（本次调研严禁重复提议）

经源码与提交历史核验，以下高频优化点**已在本仓库落地**，后续方案矩阵全部予以排除：

| 模块 | 仓库已实现功能与核心文件 |
| --- | --- |
| 全局壳层与导航 | 暗黑护眼模式（`useTheme.ts` + `MermaidDiagram.vue` 暗色同步）、全局 `Ctrl/Cmd+K` 命令面板（`CommandPalette.vue`）、学生 5 模块与管理 3 分组统一两级侧边栏 + 折叠悬停悬浮窗 + 三级可点击面包屑（`App.vue`、`domain/navigation.ts`） |
| 课程阅读与笔记 | 顶部阅读滚动进度条、章节段落 ScrollSpy 目录、划词浮窗（摘录到笔记 / 问 AI 助教 / 费曼试讲）、顶/底部显式“收藏本节”和“记笔记”按钮（`CourseView.vue`）、居中 Teleport 笔记弹窗（`LearningNotesPanel.vue`、`LearningNotesBody.vue`）、系统标签 + 当前知识点快捷标签胶囊（`domain/noteTags.ts`）、笔记与错题讲义 Markdown 导出与打印（`domain/studyExport.ts`） |
| 知识图谱与掌握地图 | 2D G6 + 3D Three.js 沉浸全屏画布（`VisualizationViewport.vue`）、1–2 跳累加探索、关系筛选器与双实体最短桥接路径查找（`domain/graphAlgorithms.ts`、`GraphView.vue`）、拓扑学习路线图模式与 V17 双值时间衰减（`HALF_LIFE_DAYS = 14`，`MasteryMapView.vue`） |
| AI 学习助教 | 静默锁定 `RANKED` 证据增强模式、全视口双栏会话管理、IME 中文防误发送、1–3 级分步提示与结构化表格（`HintEvidenceTable.vue`）、句子级 `[k]` 图谱引用对齐与宽屏引用抽屉（`domain/citationAlignment.ts`、`TutorAnswerCard.vue`）、Mermaid 图表按需懒加载、费曼“以教促学”自检（`FeynmanTeachBackPanel.vue`） |
| 练习、闪卡与计划 | 基于 V18 的主动回忆概念闪卡工作流（`FlashcardsView.vue`、`FlashcardController.java`，含 `Space` 翻面、`←/→` 切换、`1–4` 快捷键评级与固定间隔回顾）、间隔复习 7 日到期预测图（`ReviewView.vue`）、ECharts 活动热力图与六维雷达图（`LearningAnalyticsCharts.vue`）、番茄专注计时器（`PomodoroTimer.vue`） |
| 实验评估与管理台 | 基线 `+Δ%` 对比图与检索/生成耗时拆解（`ExperimentComparisonCharts.vue`）、管理员用户管理与白名单学情画像抽屉（`AdminUserManagementView.vue`、`AdminUserProfileDrawer.vue`）、版本题库导入与防丢脏表单守卫（`useUnsavedChanges.ts`）、固定 8 案例纯内存批次队列、V16 组间评估顺序与 6 步认知负荷向导 |

### 1.3 必须严守的毕设合规红线

1. **外部只读边界**：绝不修改外部 `Mecha-QA`、`python-knowledge-bridge` 源码、评分模型权重、图数据及 NebulaGraph `air_ticket` 图空间（保持只读 `spaceId = 1`）。
2. **学术诚信与规则可解释性**：
   - 推荐、掌握度、衰减与复习规则只能使用透明可解释的确定性公式，绝不引入或宣称“机器学习/深度学习知识追踪（BKT/DKT）”“FSRS 神经参数拟合预测掌握度”；
   - 认知负荷评价模块严禁引入生理信号采集或“已显著降低认知负荷”等先验结论；
   - 图谱/模型离线或异常时必须诚实降级并提示，绝不生成伪造节点、静态假证据或虚假指标；
   - 练习与受限实验任务在学生提交前绝不泄露标准答案或评分 Rubric。
3. **保密与角色隔离**：
   - `RANKED` 后台固定证据约束系统提示词正文绝不可透出到前端源码、API 响应或导出文件；
   - 日常 `/tutor` 禁止恢复 `DIRECT` / `TRIPLES` / `RANKED` 三档位切换器；
   - `STUDENT` 与 `ADMIN` 严格互斥（越权返回 403，管理员数据绝不混入学生统计与白名单外笔记/对话原文）。
4. **视觉与样式纪律**：复用 `frontend/src/styles-design-system.css` 令牌体系，保持浅色/暗色克制学术工作台美学，**严禁紫蓝渐变、严禁霓虹发光、严禁白卡套白卡、全仓 `!important` 严格为 0**，AST 类名扫描保持 **0 missing classes**。

---

## 二、阶段 1：GitHub 开源项目检索与核验记录

检索范围限定为近 2–3 年活跃、架构清晰或高 Star 的真实 GitHub 仓库，所有借鉴均**只提炼设计范式与交互思路并用本仓库原生代码重写，不搬运任何 GPL/AGPL 或社区限制协议源码**：

### 2.1 前端页面样式与交互体验（UI/UX Benchmark）

| GitHub 仓库 | 真实链接 | 协议与量级 | 核心借鉴机制与合规评估 |
| --- | --- | --- | --- |
| **`unovue/shadcn-vue`** | https://github.com/unovue/shadcn-vue | MIT · ~10.8k+ Stars | 1) 贴合内容结构的 `<Skeleton>` 骨架屏闪烁占位原语，消除数据加载时的布局抖动（CLS）；2) 语义化的 `<Kbd>` / `<KbdGroup>` 键帽视觉规范，提升快捷键可发现性。（协议完全兼容） |
| **`slidevjs/slidev`** & **`vuejs/vitepress`** | https://github.com/slidevjs/slidev <br> https://github.com/vuejs/vitepress | MIT · ~49.6k+ / ~18.5k+ Stars | 1) Vue 3 SPA 内容阅读工作台的 `?` 全局快捷键帮助浮层与单键翻页/切换提示；2) 聚焦阅读人体工学（字号/行距调节与无干扰专注模式切换，屏蔽多余边框噪音）。（协议完全兼容） |
| **`antvis/G6`** | https://github.com/antvis/G6 | MIT · ~12.4k+ Stars | 官方内置 `minimap` 鹰眼缩略导航插件与 `edge-filter-lens` / 邻域聚焦模式，针对多跳展开后图谱画布偏大、边缘节点定位困难的问题提供低侵入空间锚定。（仓库已依赖 `@antv/g6`） |
| *对比排除记录* | `frappe/lms`（AGPL-3.0，~3.5k+ Stars）、`lobehub/lobe-chat`（LobeHub Community License，~70k+ Stars） | 非 MIT/Apache | 核验协议存在传染或商用受限条款，仅观察公开产品布局趋势（多栏工作台节制留白），**严格不参照其源码结构或拷贝组件**。 |

### 2.2 现有核心功能体验优化（Functional Polish）

| GitHub 仓库 | 真实链接 | 协议与量级 | 核心借鉴机制与合规评估 |
| --- | --- | --- | --- |
| **`infiniflow/ragflow`** | https://github.com/infiniflow/ragflow | Apache-2.0 · ~73k+ Stars | “可溯源引用悬浮预览（Grounded Citation Inspection）”交互：当用户悬停/聚焦回答中的 `[k]` 内联标注时即时浮现对应原始证据片段预览，无需每次展开全部列表即可完成语义核验。（仅借鉴客户端对齐展示 UI，不改后端 LLM） |
| **`run-llama/chat-llamaindex`** | https://github.com/run-llama/chat-llamaindex | MIT | 轻量结构化引用微交互与错误消息上下文带出（把错误题干/知识点直接透传进问答输入区草稿，打通查错闭环）。 |
| **`CAHLR/OATutor`** | https://github.com/CAHLR/OATutor | MIT · ~284 Stars | 教学错因分类映射（Misconception Taxonomy）展示模式：将学生错题按“知识点 × 错误频次”进行轻量统计聚合并给出针对性练习跳转。（仅做前端聚合或只读 MySQL 统计，不宣称 BKT） |
| **`open-spaced-repetition/ts-fsrs`** | https://github.com/open-spaced-repetition/ts-fsrs | MIT · ~894 Stars | 复习历史回溯日志与待复习队列分布视图理念；我们严格摒弃其参数优化拟合并**沿用系统既有 `SpacedReviewPolicy` 确定性固定间隔**，仅借鉴其复习会话完成摘要统计面板设计。 |

### 2.3 契合毕设主题的高价值新功能（High-Value Features）

| GitHub 仓库 | 真实链接 | 协议与量级 | 核心借鉴机制与合规评估 |
| --- | --- | --- | --- |
| **`eudoxia0/hashcards`** & **`Crystalmislip/card-craft`** | https://github.com/eudoxia0/hashcards <br> https://github.com/Crystalmislip/card-craft | Apache-2.0 / MIT | Markdown 行内主动回忆 **挖空自测（Cloze Deletion）** 语法与“学习笔记/摘录 → 自测挖空卡”一键转化流程，强化软件工程概念术语记忆闭环。（可基于已有 `learning_note` 文本规则挖空实现，零 AI 臆测） |
| **`microsoft/graphrag`** & **`stanford-oval/storm`** | https://github.com/microsoft/graphrag <br> https://github.com/stanford-oval/storm | MIT · ~36k+ / ~31k+ Stars | 面向图谱的结构化多视角概念对比与证据归因视图：针对软工易混概念（如“结构化分析 vs 面向对象分析”），基于 NebulaGraph 只读返回的真实三元组构建**双概念并排属性/关系对比卡**（共有边 vs 独有边），零幻觉辅助辨析。 |

---

## 三、阶段 2：红线过滤结果与候选方案矩阵

### 3.1 红线一票否决淘汰项清单

以下想法在调研初筛中直接淘汰，不列入候选矩阵：
1. **引入 `ts-fsrs` 动态权重拟合或 BKT/DKT 掌握概率模型**：违反学术红线“规则必须透明可解释，绝不宣称机器学习知识追踪 / 预测掌握度”。
2. **基于 LLM 自动从课程正文无依据批量生成“模拟真题与预测考点”**：在无教师审核与确定性证据校验下易产生幻觉答案，违背题目标准答案严审机制。
3. **在课程评估/专注计时中添加摄像头疲劳监测或心理负载自动推导结论**：违反“严禁采集生理信号或预先断言认知负荷降低”。
4. **直接在前端暴露 `RANKED` 提示词调试微调框或写回 NebulaGraph 节点编辑器**：违反外部只读边界与 `RANKED` 系统提示词绝对保密原则。

---

### 3.2 结构化优化与新功能方案矩阵

#### 类别 A：前端样式与微交互优化（UI/UX Polish）

| 编号与方案 | 来源开源项目（GitHub URL 与借鉴机制） | 改造范围 | 毕设演示与论文加分点 | 风险与具体改动文件清单 |
| --- | --- | --- | --- | --- |
| **UI-1：内容形状骨架屏占位体系（Skeleton Shimmer）** | `unovue/shadcn-vue`<br>https://github.com/unovue/shadcn-vue <br>（MIT · `<Skeleton>` 占位组件设计） | **纯前端**（0 后端、0 迁移） | 消除学生/教学总览、错题本、笔记中心、用户表加载时的白屏与布局抖动（CLS），体现工程质感 | **风险：低**<br>- 新增：`frontend/src/components/ContentSkeleton.vue`<br>- 修改：`frontend/src/components/FeedbackState.vue`、`DashboardView.vue`、`WrongBookView.vue`、`TeachingDashboardView.vue`、`AdminUserManagementView.vue`、`styles-design-system.css`、`tests/design-system-contract.test.ts`、`authenticated-ui-smoke.mjs` |
| **UI-2：全局快捷键帮助浮层（`?`）与规范键帽组件 `<KbdBadge>`** | `unovue/shadcn-vue` (`<Kbd>`) & `slidevjs/slidev` (`?` Shortcut Modal)<br>https://github.com/slidevjs/slidev | **纯前端**（0 后端、0 迁移） | 汇总展示已有 `Ctrl+K`、`Space`/`1-4` 闪卡、`Esc` 及新增阅读快捷键（如 `[`/`]` 切节），答辩演示操作流畅度高 | **风险：低**<br>- 新增：`frontend/src/components/KeyboardShortcutsModal.vue`、`KeyboardShortcutsModal.test.ts`<br>- 修改：`frontend/src/App.vue`、`AppRoleNavigation.test.ts`、`CourseView.vue`、`authenticated-ui-smoke.mjs` |
| **UI-3：课程沉浸专注阅读态与阅读排版舒适度调节** | `vuejs/vitepress` & `slidevjs/slidev`<br>https://github.com/vuejs/vitepress <br>（MIT · 专注阅读区人体工学） | **纯前端**（`localStorage` 记忆偏好） | 支持阅读区字号（标准/适中/大号）、行宽/无干扰隐藏侧栏切换与 `[`/`]` 键盘切节，直接提升长文+Mermaid 图表阅读可访问性 | **风险：低**<br>- 修改：`frontend/src/views/CourseView.vue`、`CourseView.test.ts`、`authenticated-ui-smoke.mjs` |
| **UI-4：2D 知识图谱 Minimap 鹰眼小地图与图例紧凑化** | `antvis/G6`<br>https://github.com/antvis/G6 <br>（MIT · 官方 `minimap` 插件机制） | **纯前端**（复用现有 `@antv/g6` 依赖） | 2 跳扩展节点较多或全屏浏览时，右下角提供实时缩略定位窗，与现有 `VisualizationViewport` 点阵网格浑然一体 | **风险：中**（需验证暗黑模式与全屏视口坐标及 Playwright 截图非阻塞）<br>- 修改：`frontend/src/views/GraphView.vue`、`MasteryMapView.vue`、`GraphView.test.ts`、`styles-graph.css` |

#### 类别 B：现有核心功能体验优化（Functional Polish）

| 编号与方案 | 来源开源项目（GitHub URL 与借鉴机制） | 改造范围 | 毕设演示与论文加分点 | 风险与具体改动文件清单 |
| --- | --- | --- | --- | --- |
| **FN-1：助教 `[k]` 引用悬停即时证据气泡（Citation Popover）** | `infiniflow/ragflow`（Apache-2.0）<br>https://github.com/infiniflow/ragflow <br>（悬停直显 Chunk 证据，降低页面跳转干扰） | **纯前端**（复用既有 `[k]` 对齐引擎） | 鼠标悬停/键盘聚焦 `[k]` 脚标即时弹出该句对应的 `(Subject, Predicate, Object)` 卡片与“在图谱中查看”入口；无多余网络请求，且严格只显示服务端返回的真实三元组 | **风险：低**<br>- 修改：`frontend/src/components/MarkdownContent.vue`、`TutorAnswerCard.vue`、`domain/citationAlignment.ts`、`TutorAnswerCard.test.ts` |
| **FN-2：错题本按知识点错因归因微分布条 + 一键带题干问助教** | `CAHLR/OATutor`（MIT）<br>https://github.com/CAHLR/OATutor <br>& `run-llama/chat-llamaindex`（MIT） | **纯前端**（基于现有 `wrongBook` 数据聚合） | 顶部新增可点选的“薄弱知识点错题占比条”（点击即筛选对应知识点错题），并在单条错题操作增加“带此题干问助教”快捷预填跳转，不自动发送且不泄露未提交答案 | **风险：低**<br>- 修改：`frontend/src/views/WrongBookView.vue`、`WrongBookView.test.ts`、`TutorView.vue` |
| **FN-3：概念闪卡与复习会话完成小结卡（Session Recap & Streak Pill）** | `open-spaced-repetition/ts-fsrs`（MIT）<br>https://github.com/open-spaced-repetition/ts-fsrs <br>（队列统计与四档评分分布反馈） | **纯前端**（`FlashcardsView` & `ReviewView` 本地会话统计） | 每完成一轮“全部到期卡片”后展示清晰结业卡（本次复习数、4 档评价分布、下一步跳转专项练习/学习报告），增强学习成就闭环且不虚构长期模型预测 | **风险：低**<br>- 修改：`frontend/src/views/FlashcardsView.vue`、`ReviewView.vue`、`FlashcardsView.test.ts` |
| **FN-4：管理员高频治理表格排序筛选增强与题库版本 JSON 模板下载** | `unovue/shadcn-vue` 数据表交互模式<br>https://github.com/unovue/shadcn-vue | **纯前端**（基于现有列表数据增强） | 题库导入页增加“下载标准题库 JSON 样例模板”按钮；用户管理表支持按掌握度/错题数排序与一键重置筛选，显著提升管理员演示与日常维护效率 | **风险：低**<br>- 修改：`frontend/src/views/QuestionBankView.vue`、`AdminUserManagementView.vue`、`QuestionBankView.test.ts`、`AdminUserManagementView.test.ts` |

#### 类别 C：契合毕设主题的高价值新功能（High-Value Features）

| 编号与方案 | 来源开源项目（GitHub URL 与借鉴机制） | 改造范围 | 毕设演示与论文加分点 | 风险与具体改动文件清单 |
| --- | --- | --- | --- | --- |
| **NF-1：双概念图谱对比分析矩阵（Dual-Concept Compare Drawer）** | `microsoft/graphrag`（MIT）& `stanford-oval/storm`<br>https://github.com/microsoft/graphrag <br>（图谱多视角实体结构化对比） | **纯前端**（复用 `/knowledge/graph?entity=...` 两次只读调用与 `graphAlgorithms.ts` 集合运算） | 针对软工极易混淆成对概念（如 `需求分析` vs `结构化设计`、`黑盒测试` vs `白盒测试`），在 `/knowledge` 对比抽屉并排计算并呈现 **共同关联节点 / 实体 A 独有规则 / 实体 B 独有规则**，全量基于 NebulaGraph 真实返回三元组，离线诚实报错，极适合答辩亮点展示与论文案例分析 | **风险：中**（依赖双实体并发请求状态与图谱离线诚实降级检查）<br>- 新增：`frontend/src/components/ConceptComparePanel.vue`、`ConceptComparePanel.test.ts`<br>- 修改：`frontend/src/domain/graphAlgorithms.ts`、`graphAlgorithms.test.ts`、`GraphView.vue`、`GraphView.test.ts`、`authenticated-ui-smoke.mjs` |
| **NF-2：管理端“课程-知识点-题目-错因-微课”资产覆盖度体检面板** | `CAHLR/OATutor`（MIT）<br>https://github.com/CAHLR/OATutor <br>（课程知识组件覆盖诊断理念） | **前后端联动**（新增 **只读 SQL 聚合接口**，0 Flyway 变更） | 在管理端一键排查当前课程下各知识点的 **阅读小节数 / 启用题目数 / 干扰项错因覆盖题数 / 已发布微课数**，精准标出“无题知识点”“缺错因知识点”“缺微课小节”并提供直达修复按钮。100% 确定性 MySQL 统计，无 AI 臆测，极大丰富论文“教研闭环”章节 | **风险：中**（需新增 ADMIN-only Controller/Mapper + RoleBoundary 测试 + 隐私零学生数据检查）<br>- 后端：`AdminController.java`、`AdminService.java`、`AdminCoverageMapper.java`（或现有 Mapper 扩展）、`RoleBoundaryMvcTest.java`、`AdminCoverageServiceTest.java`<br>- 前端：`frontend/src/api/admin.ts`、`AdminView.vue`（或 `TeachingDashboardView.vue`）、`AdminCourseViews.test.ts` |
| **NF-3：个人笔记行内挖空自测卡转化（Markdown Cloze Note Recall）** | `eudoxia0/hashcards`（Apache-2.0）& `Crystalmislip/card-craft`（MIT）<br>https://github.com/eudoxia0/hashcards | **前后端或纯前端轻量版**（纯前端可零迁移即时解析 `==关键术语==` / `{{关键术语}}` 笔记做就地挖空测验；若需独立持久化调度则可预留 V19 迁移） | 打通“课程阅读划词/笔记记录 → 挖空主动回忆检测”闭环：在 `NotesAndBookmarksView` 与笔记弹窗支持点击“挖空自测”把用户自己记下的 `==要点==` / `{{要点}}` 自动遮蔽，提示回想后点击揭晓并与原小节联动。**纯规则文本解析，绝不偷跑无根据 AI 出题** | **风险：低（纯前端规则自测版）/ 中（若加 V19 持久化表）**<br>- 新增：`frontend/src/domain/clozeNotes.ts`、`clozeNotes.test.ts`、`ClozePracticeDrawer.vue`<br>- 修改：`frontend/src/components/LearningNotesBody.vue`、`NotesAndBookmarksView.vue`、`NotesAndBookmarksView.test.ts` |

---

## 四、Top 推荐落地组合与实施顺序（待审批勾选）

考虑毕设答辩展示冲击力、开发风险可控性及前序全量门禁要求，建议按 **“低风险高感知体验组合 → 差异化图谱/教学闭环新功能”** 分两步推进（全部落在新特性分支 `feat/oss-benchmark-opt`）：

### 推荐优先组合（综合性价比 Top 4）

1. **第 1 批（低风险 · 纯前端高感知体验组合）**
   - **推荐项 `UI-1 + FN-1`**：`ContentSkeleton` 内容骨架屏 + 助教 `[k]` 引用悬停证据气泡（Citation Popover）。
   - **原因**：零后端接口和数据库变更，直接解决“数据加载视觉跳动”与“助教引用查看需要频繁跳转页面”两个高频痛点，契合毕设学术工作台定位。
   - **可选伴随项 `UI-2 + FN-2`**：`?` 全局快捷键速查弹窗 + 错题本知识点归因条与“带题干问助教”。

2. **第 2 批（论文核心亮点 · 知识图谱与教学治理深化）**
   - **推荐项 `NF-1`（双概念图谱对比分析矩阵）**：完全基于现有 NebulaGraph 只读双实体三元组检索完成纯计算并排对比（共有/差异关系），直观展示“知识图谱不只是静态画布”，答辩视觉与学术说服力极强。
   - **推荐项 `NF-2`（管理端课程知识资产覆盖度体检面板）**：仅新增只读 `ADMIN`-only 聚合查询接口（无需改动 Flyway 或既有数据表），补充后台治理可量化数据闭环，强化 `RoleBoundaryMvcTest` 权限防线。

### 审批确认引导

请你在以下选项中回复需要实施的条目编号（或选择推荐批次），确认后我将切出 `feat/oss-benchmark-opt` 独立分支并按每项独立提交 + 全量门禁的方式安全落地：
- `UI-1`（内容骨架屏占位）
- `UI-2`（`?` 全局快捷键速查浮层）
- `UI-3`（课程专注阅读与排版调节）
- `UI-4`（2D 知识图谱 Minimap 缩略定位）
- `FN-1`（助教 `[k]` 引用悬停证据气泡）
- `FN-2`（错题按知识点归因分布条 + 带题干问助教）
- `FN-3`（闪卡/复习会话结业摘要卡）
- `FN-4`（管理端题库 JSON 模板下载与用户表排序）
- `NF-1`（双概念图谱结构化对比分析面板）
- `NF-2`（管理端“课程-知识点-题目-错因-微课”覆盖度体检）
- `NF-3`（笔记 `==关键词==` 挖空主动回忆自测模式）
