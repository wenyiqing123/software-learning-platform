# 第二轮 GitHub 开源项目调研与增量优化落地方案

> **调研基准**：`master` 分支 `c5099dd`（Flyway 终版架构 `V18`，第一轮第 1/2 批已全部并入且工作区干净）。  
> **执行模式**：**模式 A（独立调研 + 审批后编码）**——本文档仅做基线盘点、开源对标、协议核验、红线筛查与实施排期，暂不修改应用代码、不新建开发分支、不新增数据库迁移。  
> **许可红线**：仅允许直接借鉴 **MIT / Apache-2.0 / BSD-3-Clause / ISC** 等宽松开源许可项目的交互设计、架构思路与轻量算法；任何 **GPL / AGPL / 商业双许可受限** 项目在许可核验阶段即明确标注并排除源码级借鉴。

---

## 一、基线盘点与严格排除清单（严禁重复造轮子）

在发起本轮外部检索前，已完成仓库核心文档与前后端源码核验（`README.md`、`docs/api.md`、`docs/ui-design.md`、`docs/test-report.md`、`docs/development/continuation-ledger.md`、`docs/development/oss-benchmark-optimization-plan.md` 及对应 `.vue` / `.ts` 文件）。

### 1.1 当前已落地能力清单（绝对避开重复提议）

1. **界面体系与全局体验**：浅色/暗黑护眼主题及 Mermaid 暗色同步、统一两级侧栏与面包屑、全局 `Ctrl/Cmd+K` 命令面板（`CommandPalette.vue`）、`?` 全局快捷键速查弹窗与 `<KbdBadge>` 键帽（含输入态屏蔽，`UI-2`）、`ContentSkeleton` 内容骨架屏（`UI-1`）、番茄专注计时器（`PomodoroTimer.vue`）、未保存表单 dirty guard。
2. **课程与阅读笔记**：课程目录 ScrollSpy、阅读进度条与滚动完成回写、划词浮窗（记笔记/问助教/去费曼）、讲义 Markdown 导出、居中记笔记弹窗与标签/收藏、`NotesAndBookmarksView.vue` 的关键词检索与标签胶囊过滤、按需懒加载 `MermaidDiagram.vue` 交互图表。
3. **知识图谱与学习路径**：2D AntV G6 + 3D Three.js 双视图、1–2 跳累加探索、双实体最短桥接路径（`findEntityBridgePath`）、`GraphEvidenceDrawer.vue` 证据抽屉、先修学习路径、拓扑掌握图谱、**双概念图谱对比分析矩阵（`ConceptComparisonPanel.vue`，`NF-1`）**、离线 503 诚实降级、V17 双值时间衰减掌握度与 14 天可解释遗忘预警（`masteryRetention.ts`）。
4. **AI 助教与主动学习**：现代全视口双栏工作台（后台静默锁定 `RANKED`、绝无实验三档位暴露）、IME 防误触、句子级 `[k]` 图谱内联引用与悬停即时证据气泡（`CitationInlinePopover.vue`，`FN-1`）、分步提示（`HintEvidenceTable.vue`）、费曼“以教促学”图谱对照自检（`FeynmanTeachBackPanel.vue`，当前已展示 `missingTriples` 并支持点击跳转 `/knowledge`）。
5. **练习诊断与报告统计**：错题本（`WrongBookView.vue`）按知识点聚合微统计分布条与一键带题干问助教（`FN-2`）、自适应练习、间隔复习、学习计划、规则推荐、证据微课（`MicroLessonView.vue`）、学习报告 CSV 导出及 ECharts 热力图/雷达图（`LearningAnalyticsCharts.vue`）。
6. **实验台与管理端**：实验单次/8 案例批量对照 + `ExperimentComparisonCharts.vue` 柱状图与 CSV/JSON 导出、管理端教学总览图表（`TeachingDashboardCharts.vue`）、版本题库、用户画像抽屉、**管理员知识覆盖与内容健康度体检台（`AdminCoverageAuditView.vue` + `/api/admin/content/coverage-audit`，`NF-2`）**、密码修改级联撤销 Refresh Token、学生/管理员严格隔离。

### 1.2 代码级实测确认的真实缺口（本轮聚焦靶点）

- **`CourseView.vue` & `MicroLessonView.vue`**：尚未提供阅读字号调节（标准/舒适/大号）、黄金阅读行宽专注开关及 `[`/`]` 小节键盘快切（上一轮 `UI-3` 尚未实施）。
- **`LearningAnalyticsCharts.vue` / `ExperimentComparisonCharts.vue` / `TeachingDashboardCharts.vue`**：均初始化了 ECharts 实例，但均**未配置 `toolbox.feature.saveAsImage`（论文级 2x PNG 导出）与 `aria.decal` 无障碍纹理区分**；同时 `LearningReportView.vue` 缺少可打印周度复盘摘要卡。
- **`GraphView.vue` & `VisualizationViewport.vue`**：已有居中/全屏/搜索/3D 切换，但多跳扩散节点较多时缺少 2D G6 缩略图导航（Minimap）与视口缩放状态提示；且缺少围绕真实子图三元组的轻量交互自测功能。
- **`PracticeView.vue`**：单题与批量练习当前均为纯线性 `index` 推进，**缺少题号网格导航器、未交卷“标记存疑”开关以及普通练习后的全卷逐题复盘清单**。
- **`FeynmanTeachBackPanel.vue`**：`missingTriples` 目前仅支持跳转到 `/knowledge?entity=...` 查看图谱，**尚未打通向对应知识点 `/practice`、`/micro-lesson` 或 `/flashcards` 的闭环补强跳转**。
- **`NotesAndBookmarksView.vue`**：已有标签筛选与关键词过滤，但**缺少按知识点聚类的视角以及将手写笔记 `==重点词==` 转为页内挖空遮罩自测的能力**（对应上一轮遗留未做的 `NF-3` 缺口）。

---

## 二、第一阶段：GitHub 同类开源项目真实调研台账

以下所有仓库地址、Star 量级（截至本次在线检索可核验数量级）与开源许可协议均已通过实时检索核实：

### 2.1 三大方向入选参考仓库（全宽松许可）

| 调研方向 | 仓库路径（GitHub） | Star 量级 | 开源协议 | 可安全借鉴的核心设计/工程机制 |
| :--- | :--- | :---: | :---: | :--- |
| **A. 前端样式与交互** | [`kamranahmedse/driver.js`](https://github.com/kamranahmedse/driver.js) | ~27k+ | **MIT** | 极轻量（~5KB）聚光灯（Spotlight）分步聚焦遮罩与卡片引导交互；支持键盘方向键/Esc 退出与用户主动触发，避免强占首屏 |
| **A. 前端样式与交互** | [`vuejs/vitepress`](https://github.com/vuejs/vitepress) | ~18.5k+ | **MIT** | 专注阅读排版体系（`70ch–78ch` 黄金行宽、分层字号尺度、阅读态侧栏收起与键盘节拍） |
| **A. 前端样式与交互** | [`apache/echarts`](https://github.com/apache/echarts) | ~65.7k+ | **Apache-2.0** | `toolbox.feature.saveAsImage` 高清 2x PNG 导出（显式命名规避警告）与 `aria.decal` 参数化网纹无障碍图案 |
| **A. 前端样式与交互** | [`antvis/G6`](https://github.com/antvis/G6) | ~12.4k+ | **MIT** | 内置 `minimap` 缩略图视口联动与画布控制插件规范，提升多跳子图空间定位感 |
| **B. 核心功能闭环** | [`freeCodeCamp/freeCodeCamp`](https://github.com/freeCodeCamp/freeCodeCamp) | ~459k+ | **BSD-3-Clause** | 机考级测验流程交互：题号导航器（已答/未答/存疑）、提交前不泄密、交卷后集中复盘清单 |
| **B. 核心功能闭环** | [`freeCodeCamp/exam-platform`](https://github.com/freeCodeCamp/exam-platform) | ~35（官方考务子仓） | **BSD-3-Clause** | 与主仓配套的考场答题状态管理与题号快速定位组件模式 |
| **B. 核心功能闭环** | [`oppia/oppia`](https://github.com/oppia/oppia) | ~7k+ | **Apache-2.0** | 交互式学习反馈闭环：在识别学习盲区与常见错误后，提供定向补强微卡片与回流练习入口 |
| **B. 核心功能闭环** | [`eudoxia0/hashcards`](https://github.com/eudoxia0/hashcards) | ~1.2k | **Apache-2.0** | 基于纯文本/Markdown 的 `Cloze` 挖空语法解析与轻量本地自测交互，无需复杂重型编辑器 |
| **B. 核心功能闭环** | [`foambubble/foam`](https://github.com/foambubble/foam) | ~17.5k+ | **MIT** | 个人知识笔记双向概念聚簇视图与按概念主题过滤检索体验 |
| **C. 课程/图谱新功能** | [`kamranahmedse/developer-roadmap`](https://github.com/kamranahmedse/developer-roadmap) | ~300k+ | **MIT** | 按课程主题组织的交互式备考检查表（Guided Checklist）与知识点速查手卡（Cheat Sheet）结构 |
| **C. 课程/图谱新功能** | [`robert-mcdermott/ai-knowledge-graph`](https://github.com/robert-mcdermott/ai-knowledge-graph) | ~3.1k+ | **Apache-2.0** | 以标准 `Subject — Predicate — Object` 三元组为核心结构驱动问答与知识缺口检验的范式 |
| **C. 课程/图谱新功能** | [`mermaid-js/mermaid-live-editor`](https://github.com/mermaid-js/mermaid-live-editor) / [`mermaid-js/mermaid`](https://github.com/mermaid-js/mermaid) | ~6.9k+ / ~91k+ | **MIT** | 所见即所得 Mermaid 模板预览与一键导出 SVG / Markdown，适配《软件工程》架构/时序/类图教学 |
| **C. 课程/图谱新功能** | [`excalidraw/excalidraw`](https://github.com/excalidraw/excalidraw) | ~136k+ | **MIT** | 纯前端轻量画布工具栏设计、网格控制与极简导出浮窗交互体验（仅作前端排版/导出参考） |

### 2.2 许可协议与质量红线排除记录（不采纳源码借鉴）

- **Copyleft 强传染协议排除（GPL / AGPL / 双许可商业限制）**：
  - `shipshapecode/shepherd`（~13k Stars）：**AGPL-3.0 / 商业双许可** -> 排除直接引入，改用 `driver.js` MIT 范式并由本仓纯 Vue 原生实现聚光灯引导；
  - `moodle/moodle`（~7.6k–7.7k Stars，**GPL-3.0**）、`instructure/canvas-lms`（~6.9k Stars，**AGPL-3.0**）、`frappe/lms`（~3.2k–3.5k Stars，**AGPL-3.0**）-> 全部排除代码借鉴；
  - `ankidroid/Anki-Android`（~12.3k+ Stars，**GPL-3.0**）、`logseq/logseq`（~45.7k+ Stars，**AGPL-3.0**）、`siyuan-note/siyuan`（~47k+ Stars，**AGPL-3.0**）-> 全部排除，仅保留 `hashcards`（Apache-2.0）与 `foam`（MIT）作为合法轻量参照。
- **低 Star 玩具仓排除**：检索过程中发现如 `but-pixelated/flashcard-generator`（0 Stars）、`ogallagher/quizcard-generator`（1 Star）等仓 Star 数过低且缺少工程规范，均已直接过滤剔除。

---

## 三、第二阶段：毕设学术与工程红线一票否决清单

以下借鉴方向在进入设计前即被**一票否决**，不列入候选列表：

1. **禁止 LLM 动态出题并自动写入题库或图谱**：`ai-knowledge-graph` 等项目使用大模型从文本现场抽取 SPO 并动态生成题库；本平台必须严守 `Mecha-QA` / Python Bridge / NebulaGraph（`spaceId=1` / `air_ticket`）只读边界与教学题库版本审核机制，**不得用 LLM 现场伪造三元组或绕过审核入库**。
2. **禁止在未交卷前泄露答案或提示词**：借鉴 `freeCodeCamp` 答题导航时，严禁将正确答案解析或任何提示词透传至提交前的题目列表响应，也不允许暴露 `RANKED` 后台系统提示词。
3. **禁止新增复杂神经网络/机器学习拟合指标**：不得因借鉴间隔复习项目而宣称“基于 FSRS/BKT/DKT 预测遗忘率”，所有分布、预警与复习排序继续严格限定为**确定性计数、集合匹配或既有 V17 可解释衰减规则**。
4. **禁止强迫式首屏引导弹窗与破坏既有布局**：引导导览不得自动弹层遮挡登录态/核心功能演示；所有 CSS 严格复用 `styles-design-system.css` Token，保持 **0 `!important`**、**0 紫蓝渐变与霓虹光效**。
5. **禁止不必要的新增 Flyway 迁移**：本轮高价值候选全部优先以**零后端迁移（保持数据库终版 `V18`）**的方案完成，降低回归与集成风险。

---

## 四、第三阶段：第二轮候选优化与新功能矩阵

> **统一图例**：🟢 纯前端零迁移（零后端风险）｜ 🟡 前后端轻量只读协同（零新表）｜ 复杂度与建议顺序均已标注。

### A. 前端页面样式与交互体验精修

| 编号 | 优化名称 | 开源灵感来源 | 具体改动页面 / 组件 | 迁移与接口影响 | 学术/体验价值 | 实施风险 & 优先级 |
| :---: | :--- | :--- | :--- | :---: | :--- | :---: |
| **`R2-UI-1`** | **课程与微课专注阅读排版工坊**（延续落地上轮未做 `UI-3`） | `vuejs/vitepress` (MIT) | `CourseView.vue`、`MicroLessonView.vue`、新建 `domain/readingPrefs.ts`（字号 `sm/md/lg`、宽屏/黄金 `72ch` 行宽切换、`focusMode` 隐藏次要边栏、`[/]` 键盘切换小节且屏蔽输入态、`localStorage` 记忆） | 🟢 **0 迁移、0 后端改动** | 显著提升长讲义与证据微课阅读舒适度与键盘操作一致性 | 低风险 · **高推荐** |
| **`R2-UI-2`** | **ECharts 论文级图表 2x PNG 导出与无障碍纹理增强** | `apache/echarts` (Apache-2.0) | `LearningAnalyticsCharts.vue`、`ExperimentComparisonCharts.vue`、`TeachingDashboardCharts.vue` 及对应 `domain/*Charts.ts`（开启 `aria.enabled` + `aria.decal` 纹理、`toolbox.feature.saveAsImage` `pixelRatio: 2` 且显式命名、暗色背景自适应、完整保留 Canvas 降级守卫） | 🟢 **0 迁移、0 后端改动** | 直接解决答辩 PPT 与毕业论文撰写中“高清无背景畸变实验图/学习图导出”的痛点，并体现无障碍可视化规范 | 低风险 · **必选推荐** |
| **`R2-UI-3`** | **轻量主动触发式“聚光灯”功能导览** | `kamranahmedse/driver.js` (MIT) | 新建轻量原生 Vue 组件 `GuidedTourOverlay.vue` + `domain/guidedTour.ts`；在顶栏 `?` 快捷键按钮旁增设“功能导览”显式入口（绝不自动强占首屏），按学生/管理员角色分别提供 4–5 步聚光灯高亮与焦点跳转卡片，支持 `←/→/Esc` 翻页关闭 | 🟢 **0 迁移、0 后端改动** | 答辩现场 1 分钟快速走查全平台闭环极佳，零三方大包体积膨胀（避免 AGPL Shepherd） | 低风险 · **按需可选** |
| **`R2-UI-4`** | **2D 知识图谱 Minimap 缩略导航与比例尺胶囊** | `antvis/G6` (MIT) | `GraphView.vue`、`VisualizationViewport.vue`（为 G6 实例配置原生 `minimap` 插件挂点，随亮/暗主题重绘并兼容无 Canvas 单测守卫；补充当前画布缩放百分比只读指示与一键 `100%` 复位微调） | 🟢 **0 迁移、0 后端改动** | 与现有 `ConceptComparisonPanel` / 桥接路径互补，多跳探索 30+ 节点时不再迷路 | 中低风险（需留意 G6 5.x 容器自适应与测试 mock） · **按需可选** |

---

### B. 现有核心功能闭环优化

| 编号 | 优化名称 | 开源灵感来源 | 具体改动页面 / 组件 | 迁移与接口影响 | 学术/体验价值 | 实施风险 & 优先级 |
| :---: | :--- | :--- | :--- | :---: | :--- | :---: |
| **`R2-FN-1`** | **练习答题卡题号导航、存疑标记与交卷逐题复盘清单** | `freeCodeCamp/freeCodeCamp` & `exam-platform` (BSD-3-Clause) | `PracticeView.vue`、新建 `domain/practiceSession.ts`（题号状态网格：未作答 / 已选 / 已判对 / 已判错 / **标记存疑**；支持单题模式跳转与批量模式下只看错题/存疑题集中复盘，**提交前绝不泄露标准答案**） | 🟢 **0 迁移、0 后端改动**（纯前端会话态管理，完全复用现有题库加载与判题接口） | 补齐“做题—标疑—交卷复盘”标准化在线测验 UX，与错题本形成完整做题体验对比 | 低风险 · **必选推荐** |
| **`R2-FN-2`** | **费曼“以教促学”遗漏三元组定向补强闭环** | `oppia/oppia` (Apache-2.0) | `FeynmanTeachBackPanel.vue`、新建 `domain/feynmanRemediation.ts`（加载/传入课程知识点清单，对后端返回的 `missingTriples` 与薄弱实体做纯确定性子串/规范化名称匹配；每条遗漏概念除“去图谱”外新增**“做对应练习”**`/practice`、**“看证据微课”**`/micro-lesson`、**“加入复习/看讲义”**`/courses` 直达操作） | 🟢 **0 迁移、0 后端改动**（复用已有只读知识点清单接口） | 把费曼复述评估的终点变成下一轮精准补救的起点，学术叙事与交互闭环极强 | 低风险 · **必选推荐** |
| **`R2-FN-3`** | **笔记按知识点聚类视图与 `==重点==` 挖空遮罩自测**（落地上轮未做 `NF-3`） | `eudoxia0/hashcards` (Apache-2.0) & `foambubble/foam` (MIT) | `NotesAndBookmarksView.vue`、`domain/markdown.ts` / 新建 `domain/clozeNotes.ts`（新增“按标签 / 按章节知识点”双维度分组折叠视图；新增“自测遮罩模式”，将笔记正文中学生手写的 `==关键词==` 确定性渲染为点击揭示的遮罩胶囊，并显示本页自测揭示进度） | 🟢 **0 迁移、0 后端改动**（不修改数据库存储格式，`==关键词==` 为标准纯文本 Markdown 标记） | 让“个人笔记”从被动存档升级为可交互主动回忆卡片，完全无幻觉与算法伪装 | 低风险 · **高推荐** |

---

### C. 课程与知识图谱强相关轻量高价值新功能

| 编号 | 新功能名称 | 开源灵感来源 | 核心能力与实施边界 | 迁移与接口影响 | 毕设亮点与答辩价值 | 实施风险 & 优先级 |
| :---: | :--- | :--- | :--- | :---: | :--- | :---: |
| **`R2-NF-1`** | **基于真实图谱三元组的“概念关系辨析快测（KG Triple Cloze）”** | `robert-mcdermott/ai-knowledge-graph` (Apache-2.0) | 新建 `domain/kgTripleQuiz.ts`（配完整单测）及轻量组件 `GraphTripleQuizPanel.vue`，嵌入 `GraphView.vue` 或 `MasteryMapView.vue` 面板。**100% 确定性算法**：直接消费 `/knowledge/graph` 真实返回的 `evidence` 三元组（当有效三元组 `>= 4` 条时），轮流遮蔽关系 `predicate` 或客体 `object`，**仅从同一真实子图的其他合法边抽取互斥干扰项**；作答即时给出正确/错误并高亮对应三元组证据；**图谱离线 503 或三元组不足时诚实提示“当前子图可用三元组不足或服务离线，暂不可开启辨析”**，不写库、不使用 LLM 编题 | 🟢 **0 迁移、0 后端改动** | 直接回应“知识图谱如何反哺主动检验”的核心命题，证据链可 100% 溯源到 NebulaGraph 真值三元组，零幻觉且鲁棒性极高 | 低风险 · **强烈推荐** |
| **`R2-NF-2`** | **课程“备考速查手卡”一键汇总生成器（Exam Prep Cheat-Sheet）** | `kamranahmedse/developer-roadmap` (MIT) | 新建 `domain/examCheatSheet.ts` 与弹窗/子抽屉组件（挂在 `CourseView.vue` 或 `NotesAndBookmarksView.vue` 顶部）：按课程勾选章节，自动确定性汇总① 课程章节学习目标清单、② 学生个人笔记与收藏摘要、③ 对应小节真实图谱三元组，形成单页结构化“考前速查手册”，支持页内完成勾选与一键导出 Markdown / 打印 PDF | 🟢 **0 迁移、0 后端改动**（复用 `/courses/{id}/study` + `/learning/notes` + 可选图谱只读证据） | 极度贴合高校本科学习与考前复习场景，把课程结构、个人沉淀和知识图谱三者真正拼成一张高实用度交付物 | 低风险 · **高推荐** |
| **`R2-NF-3`** | **《软件工程》Mermaid 随堂建模演练沙盒** | `mermaid-js/mermaid-live-editor` (MIT) | 复用本仓现有按需懒加载 `MermaidDiagram.vue`，在课程右侧轻量抽屉或工具入口增加“架构与建模演练沙盒”：内置《软件工程》高频课程模板（用例图、类图、时序图、分层架构图、状态转换图），支持左右分栏实时编辑预览、错误边界优雅提示、导出 SVG 与一键插入当前小节课堂笔记 | 🟢 **0 迁移、0 后端改动** | 强绑定《软件工程》示范课程专业特色（架构与建模是软工核心痛点），且几乎零新增依赖体积 | 中低风险（注意 Mermaid 报错容器隔离与内存释放） · **按需可选** |
| **`R2-NF-4`** | **可打印学习周度复盘摘要卡（Printable Weekly Retrospective）** | `freeCodeCamp` & `apache/echarts` | 在 `LearningReportView.vue` 增加“生成周期复盘卡”按钮及对应 `@media print` 专属打印样式：从既有 `/learning/report` 与 V17 遗忘预警数据确定性提炼活跃趋势、高频错因/薄弱知识点 Top-N、已解锁概念数与下周可点击行动清单，支持浏览器原生“打印 / 另存为 PDF” | 🟢 **0 迁移、0 后端改动** | 完善了学习闭环的“阶段输出物”环节，可直接作为学生成长档案打印展示 | 极低风险 · **高推荐** |

---

## 五、第四阶段：Top 3 推荐落地组合（请确认批复）

为确保现阶段代码质量与终版架构稳定性，本轮全部精选组合均保持 **0 Flyway 迁移（严守 V18）**、**0 外部图谱/AI 写修改**，每批完成后均执行全量单测（Vitest）、生产构建校验（`0 missing classes`、`0 !important`、`0 chunk warning`）与真实运行/端口回归核验。

### 推荐组合一：答辩亮点与闭环强化首选包（纯前端 4 项，推荐优先级最高）
- **包含条目**：`R2-UI-2`（ECharts 2x PNG 导出与无障碍纹理）+ `R2-FN-1`（练习答题卡、存疑标记与交卷复盘清单）+ `R2-FN-2`（费曼遗漏三元组定向补强跳转）+ `R2-NF-1`（基于真实子图三元组的 KG 关系辨析快测）
- **组合优势**：
  1. 精准补齐本轮盘点出的 4 处核心“临门一脚”短板：**图谱主动检验**、**费曼盲区闭环**、**标准答题考场体验**、**论文图表高质量导出**；
  2. 全纯前端实现 + 完备 domain 单测覆盖，不触动后端接口与 V18 数据库，稳定性 100% 可控；
  3. 极易在论文正文（算法章节增补“基于真实三元组集合的确定性辨析生成算法”、设计章节增补“费曼-练习-图谱闭环”）与答辩演示中出彩。

### 推荐组合二：沉浸学习、笔记复习与考前输出包（纯前端 4 项）
- **包含条目**：`R2-UI-1`（课程/微课专注阅读排版工坊）+ `R2-FN-3`（笔记按知识点聚类与 `==重点==` 挖空自测）+ `R2-NF-2`（课程备考速查手卡生成器）+ `R2-NF-4`（可打印学习周度复盘摘要卡）
- **组合优势**：聚焦学生从“看课 -> 划词记笔记 -> 挖空回忆 -> 考前汇总打印 -> 周度复盘”的日常高频体感升级，把零散工具串成高黏性个人学习空间。

### 推荐组合三：交互展示与学科特色全能包（分 2 小批稳步推进，共 5 项）
- **第 1 批（交互与可视化）**：`R2-UI-2`（图表 PNG 导出与纹理）+ `R2-UI-3`（轻量聚光灯导览）+ `R2-UI-4`（2D 图谱 Minimap 缩略导航）
- **第 2 批（课程与图谱双引擎特色）**：`R2-NF-1`（图谱真实三元组辨析快测）+ `R2-NF-3`（《软件工程》Mermaid 随堂建模沙盒）
- **组合优势**：在答辩 3–5 分钟演示场景下视觉冲击力与学科契合度最强，通过主动触发聚光灯引导串联全站亮点。

---

### 下一步请确认

请批复您决定在本轮落地的**组合编号或自由勾选条目 ID**（例如：“按推荐组合一落地，按顺序分批提交”或“只做 `R2-UI-2` + `R2-NF-1` + `R2-FN-1`”），以及是否**启用模式 B**自动推进后续开发与验收提交；收到您的批复后，我将基于干净 `master` 切出新特性分支开始实施。
