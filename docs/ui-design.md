# 前端视觉与可视化设计说明

更新日期：2026-11-20

## 组件策略

- 使用项目已有的 Element Plus 2.14.8，不新增 UI 框架依赖。
- Element Plus 负责按钮、标签、提示和全局中文配置；Lucide 负责业务图标；现有页面业务组件继续保持 Vue 3 原生实现，避免大规模重写造成行为回归。
- 主题 token 集中在 `frontend/src/styles-design-system.css`，包括颜色、边框、阴影、间距和响应式布局。

## 可视化预留

`frontend/src/components/VisualizationViewport.vue` 是知识图谱的统一画布容器，当前承载 G6 2D 图谱，已经预留：

- 2D/3D 模式切换入口，3D 按钮当前为 disabled 占位，不伪装为已实现功能。
- 稳定的画布高度、背景网格、工具栏、图例、加载、错误、空数据和重试状态。
- 重置视图、全屏视图和底部说明插槽。
- `default` 插槽用于放置 G6 或未来 Three.js canvas，不改变后端图谱 API。

后续接入 Three.js 时，只需在该容器中增加 3D renderer 和相同的数据适配层，不需要重做页面布局或学习业务逻辑。

## 当前改造页面

- App shell：侧边栏、顶部栏、用户状态、可折叠角色导航和移动端底部导航。
- `/knowledge`：课程知识图谱。
- `/mastery-map`：个性化掌握图谱。
- 其他课程、练习、报告和管理页面共享同一套视觉 token，并保留原有接口与交互契约。

侧边栏的主导航按“学习闭环、练习与巩固、研究与评估、管理工作台”分组，可折叠并自动展开当前路由所在组；帮助区和版本信息固定在独立底部区域，不再把科研和管理入口堆在左下角。移动端只保留核心学习入口为底部导航，顶部依赖状态压缩为图标按钮，390px 视口经过 Playwright 检查无横向溢出。

顶部依赖状态入口由 `/api/system/status` 和 `/api/ai/dependencies` 驱动，展示真实状态和受影响功能。它不把 AI、图谱异常伪装成成功，也不会阻断已加载的课程、题库和错题页面。

## Wave 2 学习闭环改造

- 新增 `learningContext.ts`，统一保存/读取最近学习小节，并生成课程阅读、图谱、练习、助教之间的上下文查询参数；路由增加 hash 平滑滚动，课程目录、知识点和练习入口不会丢失 `courseId/sectionId/knowledgePointId/entity`。
- `/` 总览增加“继续上次学习、今日待复习、推荐下一步”三个直达入口；`/courses` 增加章节锚点、阅读完成度、课程级快捷入口和本节三项上下文操作。
- `VisualizationViewport` 统一提供 2D/3D、重置、居中、全屏和节点搜索工具；`GraphEvidenceDrawer` 只展示当前真实接口返回的三元组证据，图谱离线时保留诚实的不可用状态，不生成静态替代数据。
- `MasteryMapView` 复用 2D/3D 视口和上下文操作，节点选择后可直接进入课程阅读、专项练习、图谱或助教；个人掌握数据仍只来自当前账号的 MySQL 学习记录。
- `MarkdownContent` 和安全的 `renderSimpleMarkdown` 支持助教回答/流式回答的分段、列表和代码块展示；RANKED 模式不在前端展示服务端附加系统提示词，证据列表可折叠。
- `FeedbackState` 统一加载、错误、空数据和重试/行动入口；自适应专项练习、每日计划和推荐页已接入，其他页面保留已有业务专用反馈但复用同一 token。
- `styles.css`、`styles-graph.css`、`styles-learning.css` 增加统一 token bridge，页面容器、按钮、图谱、题卡、错题和推荐卡片复用 `styles-design-system.css` 的颜色、边框、圆角、阴影和响应式约束；390px 课程阅读和 1440px 图谱页已完成浏览器视觉核验。

## Wave 3/4 收口

- Wave 3 已完成问答对照状态、服务端 CSV/JSON 导出、RANKED 提示词隐藏、认知负荷知情流程、Paas 语义锚点、延后测验和匿名统计页面。
- Wave 4 已将管理员课程/题目启停、题库发布、微课发布接入二次确认；题库 JSON 导入限制为 2MB；管理台、错因配置、微课、教学看板和学习报告统一使用 FeedbackState 与设计 token。
- 管理台表格/列表在窄屏下允许内容换行而不撑破页面；所有空数据、依赖失败和发布状态均显示真实反馈。Three.js 图谱 chunk 仍记录为后续性能优化项。
- 问答指标、人工评分、Paas 和任务用时保持分栏展示；界面不把文本相似度或单次主观评分解释为事实正确性或已经降低认知负荷。

## 任务 2 深度精修（2026-11-21）

- 四份全局 CSS 经过 AST 压缩、重复根变量清理和 token 收敛，保留单规则换行格式便于维护；旧总字节数 75,586，新总字节数 67,131。
- LoginView 改为学术/智能学习平台双栏门面，明确展示“理解课程—连接知识—验证掌握”学习路线，不包含账号、密码、API key 或演示凭据。
- ThreeGraphCanvas 增强选中节点发光、关联边高亮和操作提示；PrerequisitePathPanel 增加目标节点边框、层级连接线和状态颜色；LearningNotesPanel 支持折叠、编辑、删除确认；ExperimentTaskAdminView 增加加载态和开放/关闭确认。
- TutorView 抽取 TutorModeSelector、TutorAnswerCard，保留停止生成、流式状态、证据折叠和 RANKED 后台提示词隔离；PracticeView 抽取 PracticeQuestionCard，保留进度、判题、错因证据和补救跳转；AdminView 抽取 AdminCoursePanel/AdminQuestionPanel；ExperimentsView 抽取 ExperimentResultGrid。
- EvaluationView 继续使用 V15 taskOrder/assignmentId、PRE/POST/DELAYED 测量和 Paas 1–9 语义锚点；页面不采集生理信号，不使用已降低认知负荷等结论性措辞。
