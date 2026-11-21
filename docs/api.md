# 接口摘要

统一入口为 http://127.0.0.1:8080/api。除注册、登录、刷新、退出和健康检查外，使用 Authorization: Bearer <accessToken>。管理员接口要求当前签名令牌角色为 ADMIN，后端执行角色校验。

| 方法 | 路径（省略 /api） | 请求或说明 |
| --- | --- | --- |
| POST | /auth/register | username、password（8–72字符）、displayName；返回201及访问/刷新令牌 |
| POST | /auth/login | username、password |
| POST | /auth/refresh | refreshToken；一次使用后立即轮换 |
| POST | /auth/logout | refreshToken；返回204 |
| GET | /auth/me | 当前用户ID、显示名称和角色 |
| GET | /system/status | 当前账号可见的 MySQL、Redis、RabbitMQ、Python Bridge/知识图谱和 AI 服务状态；只返回状态、检查时间和功能影响，不返回地址、密钥或异常栈 |
| GET | /courses | 启用的课程 |
| GET | /courses/{courseId}/study | 章节、小节正文、目标、资源和本人完成状态 |
| PUT | /courses/{courseId}/study/sections/{sectionId}/progress | completed；身份来自令牌 |
| GET | /knowledge/points | 可选 courseId；个人掌握度 |
| GET | /knowledge/graph?entity=... | nodes、edges、evidence、available；2D G6 与 3D Three.js 共用该只读响应，离线503 |
| GET | /knowledge/learning-path?targetId=... | 当前用户的传递先修路径；节点含 mastery/status/prerequisiteIds，循环关系有界返回并标记 cycleDetected |
| GET | /knowledge/mastery-map?courseId=... | 当前用户的知识点掌握图谱；返回节点掌握度、答题统计、到期复习数量、先修状态和边；不写入图空间 |
| GET | /quizzes | 可选 knowledgePointId；不返回答案 |
| POST | /quizzes/{id}/submit | answer: 字符串数组；返回 correct、correctAnswer、explanation |
| POST | /quizzes/{id}/diagnosis | selected: 本次选择的选项键；只返回管理员已配置且与所选干扰项匹配的错因、课程小节和类似题，不根据一次错误答案臆测原因 |
| GET | /wrong-book | 本人尚未掌握的错题 |
| POST | /wrong-book/{id}/review | answer；对原错题进行判分 |
| POST | /wrong-book/{id}/mastered | 手动标记已掌握，204 |
| GET | /spaced-reviews/due | 本人到期复习队列；可选 wrongQuestionId 定点打开；返回题目、客观作答和间隔 |
| GET | /spaced-reviews/paused | 本人暂停中的复习项 |
| POST | /spaced-reviews/{wrongQuestionId}/review | answer、recallRating(0–3)、requestKey(UUID)；同请求幂等；错答缩短至10分钟 |
| PUT | /spaced-reviews/{wrongQuestionId}/state | ACTIVE、PAUSED、MASTERED |
| GET | /study-plans/today | 读取或生成本人今日计划 |
| POST | /study-plans | budgetMinutes(5–240)，生成/重新生成本人今日计划 |
| PUT | /study-plans/tasks/{taskId} | action=COMPLETE 或 SKIP；完成需有课程阅读/正确答题/正确复习记录 |
| GET | /dashboard/overview | 掌握度、总答题、正确率、未掌握错题、最近七日答题次数 |
| GET | /recommendations | 知识点、优先级、推荐类型、解释；五分钟缓存、MySQL快照 |
| GET | /reports/learning?days=7&courseId=... | 当前用户阶段性学习报告；days 为1–30，按课程返回阅读、练习、复习、计划、每日活动、章节和知识点数据 |
| GET | /reports/learning/export?days=7&courseId=... | 同口径 UTF-8 CSV 导出；仅导出当前用户可见汇总 |
| GET | /learning/notes?sectionId=... | 当前用户的课程小节笔记；可选知识点筛选，结果按最近更新时间排序 |
| POST | /learning/notes | 创建本人笔记；sectionId 与 knowledgePointId 必须二选一，支持标题、正文和最多10个标签 |
| PUT / DELETE | /learning/notes/{id} | 更新或删除本人笔记；不能操作其他用户记录 |
| GET / PUT | /learning/bookmarks | 查询或设置本人课程小节/知识点收藏状态 |
| GET / POST | /tutor/sessions | 查询个人会话 / 创建会话 title |
| GET / POST | /tutor/sessions/{id}/messages | 查询 / 保存 role、content、mode、metadata；每会话最多200条 |
| DELETE | /tutor/sessions/{id} | 仅删除本人会话，204 |
| POST | /ai/ask | question、mode；可带有界 historyMessages/historyEntities |
| POST | /ai/micro-lesson | 内部微课生成接口；使用直接输出 system prompt 和 2048 token 专用上限，避免推理型模型耗尽默认 1024 token 后正文为空 |
| POST | /ai/tutor/hint | question、level(1–3)、可选历史；一级思考方向、二级课程证据、三级完整解析；无证据时安全响应 |
| POST | /ai/ask/stream | 相同输入，SSE状态、证据、文本片段与完成结果 |
| POST | /ai/compare | question、includeAblations；共享检索证据，保持生成配置一致 |
| GET | /ai/dependencies | 默认模型与Python依赖状态，以及 RANKED 专用模型的开关、配置状态和模型ID；不返回 Key、地址或凭据。`rankedModelStatus=CONFIGURED` 仅表示配置完整，不代表远端推理服务健康 |
| GET | /experiments | 本人最近100次实验 |
| POST | /experiments/run | question；includeAblations、referenceAnswer、expectedEntities可选；后端调用AI并保存实际结果 |
| PUT | /experiments/{id}/ratings | ratings对象，键为本次成功返回的模式，值1–5 |
| GET | /experiments/export | 当前用户实验的服务端 CSV 导出；包含问题、实验类型、条件、证据返回顺序、耗时、模型/生成参数快照、提示词版本/hash、分项指标、人工评分和错误状态；不包含任何提示词正文 |
| GET | /experiments/export/json | 与 CSV 同口径的 JSON 导出，schemaVersion=`experiment-export-v2`；RANKED 只保留版本/hash元数据，不返回后台固定系统提示词 |
| GET | /experiment-tasks | 当前用户被分配的实验任务 |
| POST | /experiment-tasks/{assignmentId}/start | 开始当前用户的实验分配；任务必须处于 OPEN |
| GET / POST | /admin/experiment-tasks | 管理员查询 / 创建实验任务 |
| POST | /admin/experiment-tasks/{id}/groups | 新增实验组；conditionKey 用于记录实验条件 |
| POST | /admin/experiment-tasks/{id}/assign | 将用户ID列表分配到指定实验组，重复分配幂等 |
| PUT | /admin/experiment-tasks/{id}/status | DRAFT、OPEN、CLOSED |
| GET | /admin/experiment-tasks/export?taskId=... | 导出任务、实验组、参与者、状态和真实运行次数 CSV |
| GET | /evaluations/sessions | 当前用户评估会话；包含条件、任务顺序、任务编号、用时和 Paas 评分状态 |
| POST | /evaluations/sessions | conditionKey、taskKey、taskOrder、assignmentId、consentVersion、consentAccepted；创建带知情同意的评估会话 |
| POST | /evaluations/sessions/{id}/measurements | phase=PRE、POST、DELAYED 或 EQUIVALENT，score(0–100)；延后测验独立保存 |
| POST | /evaluations/sessions/{id}/complete | elapsedSeconds、psychologicalEffort(1–9)、feedback；结束单任务计时并保存 Paas/反馈 |
| GET | /admin/evaluations/summary | 按条件返回 n、均值、中位数、标准差、四分位距、增益、用时和反馈数；仅管理员 |
| GET | /admin/evaluations/export | 去标识化 CSV；参与者真实 userId 转为稳定匿名编号，保留分组、任务顺序、条件、用时、Paas、前/后/延后测验，不导出身份字段 |

问答实验导出中的 `promptVersion`、`promptSha256`、`systemPromptSha256` 只用于复现和版本核对；服务端不会导出提示词正文，尤其不会导出 RANKED 后台固定证据约束系统提示词。自动指标与人工评分分栏保存；失败条件保留错误状态但不进入成功答案统计。
| GET / POST | /admin/courses | 查询全部课程 / 创建课程 |
| PUT | /admin/courses/{id} | code、name、term、description；图空间固定1 |
| PUT | /admin/courses/{id}/enabled | enabled |
| GET | /admin/knowledge-points?courseId=... | 同课程知识点映射 |
| GET | /admin/courses/{courseId}/prerequisite-quality | 只读先修关系质量报告；检查无效 JSON、坏 ID、重复/缺失/跨课程/自引用与循环；仅管理员 |
| GET / POST | /admin/questions | 查询（可选courseId） / 创建题目 |
| PUT | /admin/questions/{id} | 更新题型、内容、选项、答案、解析、难度及知识点 |
| PUT | /admin/questions/{id}/enabled | enabled |
| GET | /admin/question-bank/versions?courseId=... | 查询管理员可见的题库版本及题目数量 |
| POST | /admin/question-bank/versions | 创建 DRAFT 版本；courseId、versionName、sourceFilename |
| POST | /admin/question-bank/versions/{id}/import | 导入 questions 数组；逐行返回 totalRows、successCount、errorCount 和错误行，不合格题目不会写入 |
| PUT | /admin/question-bank/versions/{id}/publish | 发布非空草稿；启用当前版本题目并归档同课程旧版本 |
| GET | /admin/micro-lessons?courseId=... | 查询管理员可见的微课草稿、发布状态和证据摘要 |
| POST | /admin/micro-lessons/generate | courseId、sectionId；从真实课程小节、图谱三元组、聚合错题、自测题和先修知识生成独立草稿 |
| PUT | /admin/micro-lessons/{id}/publish | 发布微课草稿；同一小节旧发布版本归档 |
| GET | /courses/{courseId}/study/sections/{sectionId}/micro-lesson | 学生读取已发布微课；未发布返回404，不返回草稿 |
| GET | /admin/questions/{id}/diagnostics | 查询该题已配置的干扰项错因关联；仅管理员 |
| PUT | /admin/questions/{id}/diagnostics | entries: optionKey、knowledgePointId、rationale 数组；仅允许同课程启用知识点和非正确答案干扰项 |

题目管理输入：courseId、knowledgePointId、type（SINGLE/MULTIPLE/TRUE_FALSE）、stem、options（key/text数组）、answer（key数组）、explanation、difficulty（1–5）。判断题选项键为 TRUE 与 FALSE，选择题为 A–H。知识点必须属于题目的同一启用课程。学生提交无效选项返回400，不保存错误学习事件。

错误状态含义：400输入错误，401身份无效，403无权限，404资源不存在或不属于当前用户，409冲突，503依赖暂不可用。业务错误包含 code、message 和 timestamp；AI错误详见 ai-contract.md。前端只在实际身份失效时处理登录续期，不能因503清除登录。

Wave 4 稳定性错误码：AI 普通调用返回空文本时使用 MODEL_EMPTY；AI 流式请求在没有任何文本片段时发送 error 事件并使用同一错误码；Python 知识检索不可用使用 KNOWLEDGE_UNAVAILABLE；比较总时限耗尽使用 COMPARISON_TIMEOUT。这些错误不会生成空答案、静态图谱或成功实验指标。

## 只读 Python 契约

POST /api/knowledge/retrieve 输入含 question、entities、historyEntities、historyMessages、relation、spaceId=1、topK。返回 entities、triples（subject/predicate/object/score）、prompt、hasEvidence、promptTemplate。

浏览图谱时 topK 限制1–200。消融研究使用 topK=0 保留已有Python的原始检索顺序，并额外执行响应大小与三元组数量上限；超过上限返回明确失败，不伪造顺序。不存在图谱写接口。

图谱 Java 客户端使用 PYTHON_GRAPH_CONNECT_TIMEOUT（默认3s）和 PYTHON_GRAPH_READ_TIMEOUT（默认20s）；超时、HTTP错误、空响应或非法三元组统一映射为知识图谱依赖失败。AI Python 检索客户端继续使用 python.knowledge.connect-timeout、read-timeout、响应字节上限和原始三元组数量上限。

## 评测口径

问答实验页面把 `expectedEntities` 作为逐项填写的字符串数组提交，最多 40 项；空项忽略，每个输入框整体作为一个目标字符串，不按逗号、顿号等标点拆分。它用于答案召回率计算，不是三元组主语/关系/宾语，也不会作为提示词发送给模型；多个目标应分别填写在不同输入框。本题既可只填核心词 `动宾短语`，也可把带标点的完整标准答案作为一个目标短语填写。默认参考答案保留评测者给出的完整表述（包括示例）；当前图谱证据只支持“动宾短语描述处理逻辑”，没有提供“校验权限、生成报表”示例。受证据约束的回答不会擅自补入这两个例子，因此 ROUGE-L/BLEU 可能低于包含这些例子的回答；这反映参考答案与图谱证据覆盖范围差异，不应通过改短参考答案掩盖。实验比较中的 `RANKED` 使用 `ranked-grounded-v3`：保留 Python 原始提示词作为 user 消息，先选择直接回答问题的候选证据，再要求回答逐条包含选中三元组的主体、关系和宾语，并按关系语义自然组织汉语句子；“规则、类型、方式、形式”等标签关系直接接宾语，“实质、定义、含义”等名词性判断关系使用“是/为”，不机械用冒号替代连接词。校验缺项则失败；普通 `/ai/ask` 与 `/ai/ask/stream` 的 `RANKED` 行为不变。`result.prompt` 与 `promptSha256` 对应原始 user 提示词，隐藏的服务端系统提示词正文不返回客户端；`provenance` 记录 `promptSource=PYTHON_ORIGINAL_PLUS_SYSTEM_PROMPT`、版本与 `systemPromptSha256`，`grounding` 记录候选、选择、原始输出、校验结果、耗时和 token。多事实证据不足、证据编号无效或回答未覆盖选中事实任一三元组要素时返回失败，不进入答案指标或人工评分。新旧 RANKED 结果不能混合解读；旧结果没有系统提示词版本标记。

results 为后端 AI 服务的实际响应；evaluation.source=SERVER_AI_SERVICE。V2遗留浏览器导入记录标注 LEGACY_BROWSER_IMPORT。evaluation.metricsByMode 仅包含成功生成结果。参考答案为空时 ROUGE-L/BLEU 为 null，期望实体为空时实体召回率为 null；失败模式不允许人工评分。

问答实验页面另外提供客户端 CSV 展平导出和原始 JSON 导出；导出字段包含运行时间、实验类型、策略、模型变体、成功/失败、耗时、分项指标、人工评分、回答和错误信息。失败运行保留在报告中但不进入成功答案指标或人工评分。

ROUGE-L 使用小写 Unicode 字母/数字字符序列的最长公共子序列F1；BLEU 使用同一分词，最大4阶并对短文本采用有效阶数，不做平滑；实体召回率按去重的期望实体是否出现在回答中计算。它们不等同于完整事实正确性。个人跨次统计包含不同问题与配置，正式论文分析应导出后按评测集和配置分组。

## 最终合规守卫

- `backend/ai-service` 的守卫测试确认证据不足时返回结构化失败且不调用答案生成；grounding 轨迹只保留策略版本和提示词 hash，不返回固定系统提示词正文。
- `AiExperimentController` 在保存和返回实验结果前清洗 RANKED 的 `prompt`、`systemPrompt*` 正文，只保留 `systemPromptVersion`、`systemPromptSha256` 等可复现元数据；DIRECT/TRIPLES 的原始提示兼容保留。
- `backend/learning-service` 的守卫测试确认实验导出不含 `prompt` 正文，`/knowledge/graph` 的依赖失败直接返回 503/结构化故障，不生成静态节点；学生题目投影在提交前不含 `answer`、`answerJson` 或 `correctAnswer`。
- 前端守卫测试扫描助教、实验、总览、推荐、评价和登录视图，禁止 RANKED 后台提示词标识及“机器学习知识追踪”“采集生理信号”“已证明降低认知负荷”等越界表述；图谱离线分支必须显示真实错误/等待连接状态。
- 前端 `/api` 仍只代理到 Gateway；SSE 仍由 Gateway/Nginx 关闭缓冲并使用 300 秒超时。最终 Vite 供应商分包和 28 项 Playwright 路由级巡检记录见验收报告。
