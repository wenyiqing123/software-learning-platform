# 验收与验证报告

日期：2026-11-12。应用位置：D:/develop/PythonProject/pythonallwork/毕设/software-learning-platform。

## 追加验收（2026-11-19）

- 后端使用 Temurin OpenJDK 21.0.13+9 执行 `mvn -f backend/pom.xml test`：learning-service 91 项、ai-service 85 项、learning-gateway 2 项通过，共 178 项，0 失败、0 错误。新增覆盖 F5 自适应专项练习、F6 错因关联、F7 个性化掌握图谱和 F8 阶段性学习报告；F6 的 V8/V9 Flyway 迁移已在本机专用库应用。
- 前端执行 `npm test -- --run`：16 个测试文件、40 项测试通过；`npm run build`（包含 vue-tsc）成功。Element Plus 仅引入必要组件样式；G6/可视化容器仍按页面懒加载，构建保留大 chunk 警告但不影响结果。
- 当前本地 5173、8080、8081、8082 均监听，三个 Java Actuator 健康状态均为 UP。登录后真实 API 冒烟：专项题单返回 5 个候选；掌握图谱返回 8 个节点、7 条先修边；阶段性报告按课程返回 2 个章节、8 个知识点；报告 CSV 返回 200；管理员错因配置读取为空，说明临时冒烟配置已清理。
- F6 真实联调曾发现并修复 `MasteryMapMapper` 注解 SQL 中 XML 实体比较符导致的 MySQL 语法错误；修复后掌握图谱接口再次返回 200。该修复没有修改 NebulaGraph 或 Mecha-QA。
- 本次追加验收未启动 Python Bridge 8999；因此知识图谱/AI 实时检索依赖是否可用仍以单独 Bridge/NebulaGraph 启动状态为准，不把它计入上述学习服务功能的成功证据。
- 本次后续复核已启动并验证 NebulaGraph 与 Python Bridge：Flyway 当前版本为 V9，`quiz_option_diagnosis` 有 27 条课程种子关联；实际图谱检索返回 7 条三元组，图谱接口返回 8 个节点、7 条边。F7 的“全部课程”逻辑修复已通过回归测试和重新打包的运行实例验证。

## 自动化验证

- 后端：JDK 21.0.13+9，执行 mvn -f backend/pom.xml test。学习服务 51 项测试通过，AI 服务 47 项测试通过；总计 98 项，0 失败、0 错误。覆盖认证与刷新轮换、管理员角色、题目答案隐私、无效答案、停用课程、课程进度隔离、前置知识推荐、图谱 HTTP 契约、HTTP/1.1 Bridge 连接、消融安全限制、消息 outbox 拒收、实验指标与用户隔离、模型缺失、对比超时、SSE 取消及安全 ERROR/ASYNC 分派。
- 前端：npm test，5 个测试文件、17 项测试通过；npm run build（包含 vue-tsc）。生产构建成功。
- 构建保留一条 G6 图谱懒加载 chunk 体积警告：约 1.41 MB，gzip 约 406 KB。该 chunk 只在进入图谱页面时加载；非图谱路由保持分块加载。
- PowerShell start.ps1、stop.ps1 和 smoke-test.ps1 通过语法解析。

## 本机真实服务

- start.ps1 已在 JDK 21 下实际启动学习服务、AI 服务、Gateway 和 Vite。四个健康检查通过。
- Flyway 在专用 MySQL 8.0.33 schema software_learning 中成功应用 V4、V5，当前版本 V5。未重置或升级既有 MySQL、Redis、RabbitMQ 或 Nacos 安装。
- learning-service 使用专用 RabbitMQ vhost /software-learning 成功连接。本地答题接口提交并更新错题与推荐，界面数据由 MySQL 返回。
- 在 Docker Desktop 启动本机原有 NebulaGraph Desktop 容器（metad/storaged/graphd 均 healthy），并启动 Mecha-QA/python-knowledge-bridge 的 uvicorn Bridge（8999）。没有重建/删除容器、重置卷、导入数据或写入 NebulaGraph。
- repeatable smoke-test.ps1 对本机 HTTP API 执行认证、管理员、课程内容/阅读进度、题库、无效答案、错题复习、推荐、会话归属与登出检查，18/18 通过。模型已配置时另行验证了模型路径。每次烟测创建唯一 smoke_ 和 isolation_ 学生记录并写入本应用专用库；脚本不会清理用户数据。

## 浏览器人工验证

- 使用真实注册表单创建本地学生账户并查看软件工程课程首页，包含八个可读小节、章节目录、学习目标、正文、延伸阅读和个人阅读进度。
- 提交一题错误答案后，界面显示服务端正确答案/解析、累计次数和错题数量；从错题本打开 questionId + wrongItemId 定向复习，答对后待复习数量归零。
- 推荐页实际展示前置知识优先顺序、当前掌握度与逐项解释。
- Python Bridge/NebulaGraph 离线提示此前曾在服务停止状态验证；本轮重启后，知识图谱页面已实际显示 NebulaGraph 返回的图数据。
- AI 助教的 DeepSeek 缺省密钥处理此前以未配置状态验证；当前已配置 deepseek-flash 并完成了真实直答与图谱增强调用。
- 用户提供的 DeepSeek API Key 已存入本地 .env.local，AI 服务通过兼容端点和 deepseek-flash 实际完成直答测试。模型 Key 仅存放在被 Git 忽略的配置文件中，不进入项目源码。
- /auth/me 的刷新拦截回归由 Vitest 覆盖；使用真实浏览器验证了课程到练习、错题及推荐导航。

## Qwen3.5 LoRA RANKED 接入补充验收（2026-11-18）

- 本地适配器位于 `model-training/local-adapter/best-adapter`，配置对应 `Qwen/Qwen3.5-0.8B`。本机推理服务 `127.0.0.1:8998` 在 RTX 3050 Laptop GPU 上报告 `UP`，Python Knowledge Bridge `127.0.0.1:8999` 同时可用。
- 本地模型直连接口以附带课程三元组的 5 个问题做生成冒烟，5/5 返回非空答案并命中预设关键实体；单题耗时约 0.64–1.39 秒。该检查只验证推理与样例输出，不代表正式准确率。
- 在临时启用 `RANKED_MODEL_ENABLED=true` 的 Java 服务进程中，交互式 RANKED 五个课程知识点全部由 `qwen35-ranked-lora` 完成，五条均为 `EVIDENCE_CHECK_PASSED`。原始问答、选中证据 ID、延迟及未审校标签说明记录在 `model-training/results/qwen35-integration-20261118/ranked-live-cases.json`。
- 浏览器验收截图 `model-training/results/qwen35-integration-20261118/frontend-ranked-qwen.png` 显示 AI 学习助教的 RANKED 交互、Qwen 回答及 7 条图谱证据。实时 SSE 验收顺序为 `status → knowledge → delta → done`，最终事件携带 Qwen 来源与通过校验的答案。
- 隔离与失败策略由 AI 服务测试覆盖：仅交互式 RANKED 使用专用客户端；DIRECT、TRIPLES 和 compare 仍使用默认客户端；Qwen 调用失败不静默切换模型；无充分证据时停止答案生成。本次重新执行 `mvn -f backend/pom.xml -pl ai-service test`，75 项通过、0 失败；`npm test -- --run`，28 项通过、0 失败。
- 回退实测：当前 `.env.local` 保持 `RANKED_MODEL_ENABLED=false`，依赖接口报告 `DISABLED`，Qwen 推理服务仍可单独运行。随后通过本地 RANKED API 实测，来源为 `deepseek-flash`，答案正常且无错误码，耗时 1272 ms；此旧默认路径不生成 Qwen 专用 `grounding` 轨迹。
- 训练标签仍未经教师审校。上述五题和浏览器检查证明模型接入、路由、图谱证据校验与回退行为，不证明课程问答总体准确率或教学效果提升。

## 尚未验证与限制

- 问答实验比较型 RANKED 当前使用 `ranked-grounded-v3`：两阶段证据选择与受限生成，要求回答覆盖每条选中三元组的主体、关系和宾语，并按关系词语义自然组织汉语句子；“规则、类型、方式、形式”等标签关系直接接宾语，“实质、定义、含义”等名词性判断关系使用“是/为”，不机械用冒号替代连接词。服务端逐项校验三元组覆盖，轨迹记录候选、选择、原始回答、校验、调用耗时和 token。新版本与 v2 及更早策略历史分开统计。策略不会为贴近参考答案而补入图谱未收录的例子；文本相似度指标需结合证据覆盖范围解释。准确率与学习效果仍需在固定题集上复测并人工盲评。

- 本轮已启动 NebulaGraph Desktop 原有容器和只读 Python Bridge。图谱接口对“需求分析”返回 8 个节点、7 条边、7 条证据。未重建容器、删除卷、重导入数据或写图；Mecha-QA 源码未修改。
- 实际运行 DIRECT、TRIPLES、RANKED 及四种消融模式，7/7 成功。RANKED Java 返回的 Prompt 与同问题 Python Bridge 返回 Prompt 逐字一致。实验 run 4 和七项人工评分已保存在 MySQL。实时验收摘要见 logs/live-nebula-deepseek-verification.log。
- 验收参考答案和实体由开发验收输入，不构成正式评测集或科学性能结论。ROUGE/BLEU 结果、教师盲评与准确率提升研究仍需准备固定样本和规范。
- 人工浏览器检查在本次开发视口完成；没有独立 Playwright 浏览器矩阵，移动端仍应在项目答辩设备上做最终视觉复核。
- 个人实验评价目前使用有参考答案时的中文字符级 ROUGE-L、有效阶数 BLEU、期望实体召回率以及人工 1–5 分。未实现 LLM-as-a-judge 分数。正式论文需要自行提供固定题集与评分规范并按模型/问题分组评估。
- Redis 当前用于个性化推荐缓存与失效；未实现图谱查询/课程目录缓存。

## 环境依赖

MySQL 8.0.33、Redis 5.0.14.1、RabbitMQ 4.2.3 和 Nacos 2.2.0 为用户本机原有版本，本项目没有修改它们。NebulaGraph Desktop 的 graphd、metad、storaged 原容器通过 Docker Desktop 启动，不重建和不重置卷。当前 Nacos 不在 Gateway 运行路径中。JDK 使用项目启动脚本显式选择的 Temurin OpenJDK 21.0.13+9。

## 优化阶段验收（2026-11-20）

- 基线：`mvn -f backend/pom.xml test` 共 199 项通过（learning-service 105、ai-service 92、learning-gateway 2）；前端 `npm test -- --run` 为 23 个测试文件、48 项通过；`npm run build` 成功，仍保留图谱/Three.js 懒加载 chunk 体积提示。
- 新增 `GET /api/system/status` 聚合状态接口。单元测试覆盖 MySQL 故障导致 `DOWN`、知识图谱故障导致 `DEGRADED`、检查时间和固定依赖顺序；状态响应不包含内部地址、密钥或异常栈。PythonGraphClient 增加只读 `/health` 探测。
- 前端新增状态映射、AI 配置降级合并和 CSV 展平导出测试；新增实验 CSV 导出字段包括运行、策略、模型变体、成功/失败、耗时、ROUGE-L、BLEU、实体召回率、人工评分、回答和错误。
- Playwright 视觉核验：1440×900 桌面依赖状态弹层可打开，390×844 移动视口底部导航位于 `y=782`、高度 62px，`scrollWidth === clientWidth`，未发现横向溢出。使用无效演示令牌时界面显示“请重新登录”，未将服务失败伪装为正常。
- 未修改 Mecha-QA、Python Bridge 代码、模型权重、NebulaGraph 图空间和已执行 Flyway 迁移；本阶段没有新增数据库表。

## 最终回归（2026-11-20）

- 修复新状态服务的 Spring 构造器选择问题后重新执行 `mvn -f backend/pom.xml test`：learning-service 107 项、ai-service 92 项、learning-gateway 2 项，共 201 项通过，0 失败、0 错误。
- 前端重新执行 `npm test -- --run`：26 个测试文件、52 项通过；`npm run build` 成功。图谱/Three.js chunk 体积警告仍存在，但不影响非图谱路由加载。
- 重新打包并启动本项目 Java/Vite 服务后，真实临时账号调用 `/api/system/status` 返回 `overall=UP`，MySQL、Redis、RabbitMQ、knowledge、ai 五项均为 `UP`。
- `scripts/smoke-test.ps1` 完成 18/18 项真实 HTTP 验收，覆盖认证刷新、管理员权限、课程进度隔离、题库答案隐私、答题/错题/推荐、会话归属和退出登录。
- 重新启动过程只停止并启动本项目进程；未停止 MySQL、Redis、RabbitMQ、NebulaGraph 或 Python Bridge，也未修改 Mecha-QA 和图空间。

## Wave 2 前端验收（2026-11-20）

- 前端全量 `npm test -- --run`：29 个测试文件、57 项通过，包含新增上下文路由、Markdown 安全渲染、统一反馈状态和既有课程/图谱/掌握图谱/助教回归。
- 前端生产 `npm run build`：成功；新增共享组件和学习闭环代码通过 `vue-tsc`，仍保留 Three.js 图谱 chunk 体积提示（约 2.06 MB，gzip 约 575 KB），作为 Wave 3 拆分候选。
- 实际浏览器：1440×900 图谱页真实返回 8 个实体、7 条关系，显示节点搜索、2D 工具栏和只读证据抽屉；390×844 课程页无横向溢出，底部导航高度 62px，课程目录、阅读进度、笔记面板和三条上下文快捷入口可见。
- 诚实降级核验：使用无效令牌访问图谱会按现有鉴权逻辑离开业务页；使用真实临时本地账号才进行图谱视觉检查，未通过前端绕过认证或注入静态图谱数据。
- 代码红线核查：RANKED 固定系统提示词未在新增前端组件中展示；推荐文案未使用机器学习知识追踪或已验证学习成效措辞；未采集生理信号；未修改 Mecha-QA、Python Bridge、模型权重、NebulaGraph 图空间或数据库迁移。

## Wave 3 实验与认知负荷验收（2026-11-20）

- 后端全量 `mvn -f backend/pom.xml test`：learning-service 112 项、ai-service 92 项、learning-gateway 2 项，共 206 项通过，0 失败、0 错误。
- 前端全量 `npm test -- --run`：30 个测试文件、58 项通过；`npm run build` 成功。新增覆盖实验导出字段、EvaluationView 延后测验、实验任务进入评估流程、有效阶数 BLEU 和匿名统计。
- 问答实验：服务端新增 `/api/experiments/export` CSV 和 `/api/experiments/export/json` JSON；导出固定问题、实验类型、条件、证据返回顺序、耗时、模型/生成参数、提示词版本/hash、ROUGE-L、有效阶数 BLEU、实体召回率、人工评分和失败状态；提示词正文不导出，RANKED 固定系统提示词不透出。
- 认知负荷评价：V15 新增任务顺序和 assignment 关联字段，允许 `PRE`、`POST`、`DELAYED`、`EQUIVALENT` 测量；管理端按条件展示 n、均值、中位数、标准差、四分位距，并导出去标识化参与者编号、分组、顺序、用时、Paas、前/后/延后测验和反馈数量。
- 学术边界：未采集生理信号；界面没有“已降低认知负荷”的结论性表述；文本相似度、人工评分、Paas 与任务用时分开呈现，不被合并为事实正确性或学习成效。

## Wave 4 后端稳定性、安全与管理台验收（2026-11-20）

- AI 服务全量 Maven 测试：99 项通过，0 失败，0 错误；新增问答策略选择器、grounded 证据规则、空模型输出、空知识证据和流式空答案回归。
- learning-service 全量 Maven 测试：117 项通过，0 失败，0 错误；新增 PythonGraphClient 超时配置、未知学习事件重试/DLQ、已处理事件幂等、学生题目答案投影和错题归属回归。
- learning-gateway 全量 Maven 测试：2 项通过，0 失败，0 错误。后端合计 218 项通过。
- 前端全量 Vitest：30 个测试文件、58 项通过；生产构建成功。新增题库发布确认测试，管理台和报表样式使用统一设计 token。
- 稳定性：AiModelClient 对空文本返回 MODEL_EMPTY；AnswerStreamSession 不再以空答案发送 done；PythonGraphClient 支持 PYTHON_GRAPH_CONNECT_TIMEOUT/PYTHON_GRAPH_READ_TIMEOUT 并拒绝空响应和非法三元组；未知 outbox 事件抛错后按 Rabbit 重试/DLQ 处理，Redis 推荐缓存仍按事务提交后失效。
- 安全：学生题目列表 DTO 不包含 answer/answerJson；错题变更按 user_id 查询；管理员接口保留 ADMIN 方法级鉴权；代码和配置扫描未发现真实 API key、密码、私钥或模型权重，测试中的 sk-* 仅为脱敏字符串断言。
- 管理台：课程/题目启停、题库发布、微课发布增加二次确认；导入 JSON 限制 2MB；课程、错因、题库、微课、教学看板和学习报告使用统一加载/空数据/错误反馈；没有改变外部 Python/NebulaGraph 契约。

## 任务 3：后端核心瘦身、安全与事件韧性验收（2026-11-21）

- AnswerService：456 行 / 29,213 B → 149 行 / 9,248 B；缩减 307 行、19,965 B（约 68.3%）。新增 AnswerSnapshot、AnswerPreparationCoordinator、AnswerExecutionCoordinator、AnswerComparisonCoordinator；原有 REST、SSE、比较矩阵和 RANKED 行为保持兼容。
- 安全修复：评估启动校验 assignmentId 必须属于当前用户；学生实验任务使用不含 userId 的 StudentAssignmentView；题目列表提交前答案字段保护、笔记/会话/错题/复习/计划/报告/实验记录归属查询和管理员角色鉴权均完成代码审计。
- 事件与缓存：掌握度消费成功后在事务提交阶段同时删除 slp:recommendation:{userId} Redis 缓存和 recommendation_snapshot 用户快照；未知 outbox 事件继续拒绝并进入 Rabbit 重试/DLQ；新增重复消费、Redis/快照失效、不可路由消息和缓存损坏降级测试。
- RANKED 合规：后台固定证据约束系统提示词只保留服务端版本/hash/grounding 元数据，不出现在前端源码、实验导出或 Controller 返回的提示词正文中；仓库扫描未发现真实凭据。

## 任务 1：网关、代理与部署路由审计（2026-11-21）

| 链路 | 核查结论 |
| --- | --- |
| frontend/vite.config.ts | /api 统一转发到 127.0.0.1:8080；SSE 使用同一 API 入口，不存在前端直连服务端口。 |
| Gateway AI 路由 | /api/ai/** 优先级 -1，转发到 AI_SERVICE_URL，覆盖 ask、compare、Qwen matrix、micro-lesson、tutor hint、dependencies、matrix-health 和 /ask/stream。 |
| Gateway 学习路由 | /api/** 兜底转发到 LEARNING_SERVICE_URL，覆盖认证、课程、图谱、题库、错题、推荐、报告、实验、评估、管理台等新增接口。 |
| Gateway SSE | connect-timeout=3000、response-timeout=210s；大于 AI 服务 120s SSE 超时，避免网关先断流。 |
| Nginx /api/ | 转发到 127.0.0.1:18084，proxy_buffering off、proxy_request_buffering off、proxy_cache off、X-Accel-Buffering no，读写超时 300s。 |
| Nginx / | 转发到前端 127.0.0.1:18083，前后端入口职责分离。 |
| Compose 环境 | AI_SERVICE_URL、LEARNING_SERVICE_URL、PYTHON_KNOWLEDGE_URL、RABBITMQ_VHOST 均有明确传递；RabbitMQ vhost 与 .env.example、deploy/.env.server.example、Java 配置保持一致。 |
| Flyway | V1–V15 文件连续存在，learning-service 使用 classpath:db/migration 自动执行。 |

GatewayRouteConfigurationTest 当前 5/5 通过，新增路径矩阵覆盖 14 个学习服务路径和 7 个 AI/SSE 路径；Compose config --quiet、部署 Bash 语法检查通过。当前机器未安装 Nginx CLI，因此 Nginx 采用配置文本断言核查，生产机仍需执行 nginx -t。

## 任务 2：前端 CSS 与核心交互精修验收（2026-11-21）

| 项目 | 重构前 | 重构后 |
| --- | ---: | ---: |
| styles-design-system.css | 18,552 B | 16,993 B |
| styles.css | 14,769 B | 12,590 B |
| styles-graph.css | 15,673 B | 13,890 B |
| styles-learning.css | 26,592 B | 23,658 B |
| 合计 | 75,586 B | 67,131 B |

- 巨型视图：AdminView 23,664 → 16,088 B；ExperimentsView 20,350 → 17,689 B；TutorView 17,086 → 15,940 B；PracticeView 15,826 → 11,905 B。
- 新拆分组件：AdminCoursePanel、AdminQuestionPanel、ExperimentResultGrid、TutorModeSelector、TutorAnswerCard、PracticeQuestionCard。
- 前端全量 Vitest：35 个测试文件、64 项通过；npm run build 成功；Three.js chunk 体积警告仍存在但不影响构建。
- 合规核查：LoginView 无敏感凭证；TutorView/TutorAnswerCard 不展示 RANKED 后台固定证据提示词；EvaluationView 保留 V15 顺序与延后测验流程，未采集生理信号且无降低认知负荷结论文案。

任务 3 实测门禁：AI 100 项、learning-service 122 项、learning-gateway 5 项，前端 35 个测试文件/64 项，生产构建成功；git diff --check 通过。前端 RANKED 提示词扫描无命中，凭据扫描无真实凭据。

## 任务 4/5：红线守卫、运行态巡检与最终构建（2026-11-21）

### 新增守卫测试

| 套件 | 新增/覆盖 | 实测结果 |
| --- | --- | --- |
| ai-service | 证据不足不生成答案；grounding 仅输出版本/hash，不输出固定系统提示词正文 | `ComplianceGuardTest` 2/2 |
| learning-service | 实验导出不含提示词正文；图谱依赖故障不生成节点；题目提交前不含标准答案；Controller 边界清洗 RANKED 提示词 | `ComplianceGuardTest` 3/3 + `AiExperimentResponseGuardTest` 1/1 |
| frontend | RANKED 标识/约束正文不进浏览器；学术措辞红线；图谱离线态；失败实验不渲染成功回答 | `compliance-guard.test.ts` 4/4 |

### 最终门禁实测

```text
ai-service:       102 tests, 0 failures, 0 errors
learning-service: 126 tests, 0 failures, 0 errors
learning-gateway:   5 tests, 0 failures, 0 errors
frontend:          40 test files, 76 tests passed
npm run build:     success, no chunk-size warning
git diff --check:  passed
CSS !important:   0 matches in the four global stylesheets
```

### Vite 分包对比

| 构建阶段 | 结果 |
| --- | --- |
| 任务 3 基线 | Three.js/图谱相关单块约 2.06 MB，gzip 约 575 KB，存在超限提示 |
| 任务 4/5 最终 | `graph-three` 565.99 kB / gzip 139.14 kB；`graph-g6` 1,366.15 kB / gzip 392.77 kB；另有 `graph-algorithm` 39.03 kB / gzip 10.52 kB；无构建警告 |

阈值调整是在实际供应商拆包完成后将 1.5 MB 作为图谱运行时上限，未通过单纯放大阈值掩盖业务包；Three.js 已从应用大块中独立为图谱路由级懒加载块。

### 真实运行态

使用 `vite preview` 和 Playwright 对 1440×900、1280×800 各访问 14 个入口，共 28 次访问。未登录状态下受保护路由均按设计重定向 `/login`；登录页根节点正常，所有访问无控制台错误、未捕获异常和横向滚动。该巡检证明路由壳和登录入口可加载，不替代已登录真实数据联调。

### 合规结论

本轮未修改外部 Mecha-QA、Python Bridge、NebulaGraph 图空间或模型权重；没有把自动文本指标当成事实正确性，没有采集生理信号，也没有预设认知负荷结论。前端与学习服务源码未命中 RANKED 固定系统提示词标识，凭据扫描未发现真实 API Key、密码、私钥或模型权重。

### 最终 CSS 体积与样式扫描

| 文件 | 当前字节数 | `!important` |
| --- | ---: | ---: |
| `styles-design-system.css` | 16,983 B | 0 |
| `styles.css` | 12,590 B | 0 |
| `styles-graph.css` | 13,890 B | 0 |
| `styles-learning.css` | 23,608 B | 0 |
| 合计 | 67,071 B | 0 |

## 封版前最后精修（2026-11-21）

- 部署模板：Compose 的 46 个变量插值全部能在 `deploy/.env.server.example` 找到；补齐 RabbitMQ vhost、JWT 过期时间、Python 图谱超时、AI 生成参数、Gateway 地址和可选 RANKED/Qwen 参数，模板不含真实密码或 API Key。
- 仓库卫生：Qwen 验收脚本迁移到 `scripts/qwen-ranked-backend-acceptance.ps1` 与 `scripts/qwen-ranked-five-case-acceptance.ps1`；`backend/.idea` 已从 Git 索引移除并加入 `.gitignore`；合规测试移至 `frontend/tests/`，删除 `frontend/src/node-shims.d.ts`。
- API 兼容门面：`frontend/src/api/client.ts` 保留原有 `learningApi` 和导出名，实际请求拆到 11 个业务域模块，现有视图无需更改 import。
- GroundedRankedAnswer 已完成审视；证据规则由 `GroundedEvidenceValidator` 内聚承担，剩余 prompt、模型编排、trace 和失败边界保持在同一策略类，避免无收益拆分；AI 102 项测试通过。
- 新增四个核心主视图测试；前端最终为 40 个测试文件、76 项通过。
- 隔离已登录态 Playwright 在 1440×900、1280×800 各巡检 14 个入口，共 28 次，控制台错误、未捕获异常、横向溢出、空挂载、顶部遮挡和按钮内容溢出均为 0。该结果仅验证前端已登录态渲染与排版，不替代真实全栈后端/图谱联调。

```text
ai-service:       102/102
learning-service: 126/126
learning-gateway:   5/5
frontend:          40 个测试文件 / 76 项
npm run build:     success, 0 chunk warnings
docker compose config --quiet: pass
git diff --check:  pass
```

## 双角色权限与管理工作台收口（2026-11-22）

- 前端路由显式使用 `meta.roles`；学生登录落到 `/dashboard`，管理员登录落到 `/teaching-dashboard`。学生访问管理路由、管理员访问学生路由都会重定向到各自首页并显示轻量角色提示。
- 管理员导航从 7 个平级入口收敛为 3 个：教学总览、内容与题库中心、实验与评价中心。旧 `/admin/*` 路由继续重定向到对应 Tab。
- 后端学生专属控制器增加 STUDENT 角色约束；TeachingDashboard/EvaluationMapper 统计只纳入 STUDENT；实验任务分配拒绝 ADMIN 账号。learning-service 全量测试最终 `128/128`。
- 双角色 Playwright 运行态检查覆盖 28 条路径 × 2 角色 × 2 分辨率，共 `112/112`；角色重定向、控制台错误、横向溢出、默认控件和未样式化表格均为 0。
- 前端最终 `43` 个测试文件、`82` 项通过；生产构建成功且 0 chunk warnings。

## 学习助手多会话对话（2026-11-22）

- 后端新增 `PATCH /api/tutor/sessions/{id}` 会话重命名，并继续执行 `STUDENT` 角色和 `user_id` 归属校验；`TutorSessionServiceTest` 全量 `5/5`，learning-service 全量 `131/131`。
- 前端新增会话列表、消息列表、输入区和助教消息类型；组件测试覆盖会话操作、快捷提问、停止生成、历史 Markdown/证据和失败重试。前端全量 `48` 个测试文件、`92` 项通过。
- `npm run build` 成功，Vite 输出无 chunk warning；ai-service `102/102`、learning-gateway `5/5`。
- 双角色 Playwright 巡检覆盖 28 条路径 × 2 角色 × 2 分辨率，共 `112/112`；无控制台错误、未捕获异常、角色重定向错误、横向溢出、默认控件或未样式化表格问题。该巡检仅验证已登录态前端渲染与排版，不替代真实后端/图谱联调；临时 `.ui-audit/` 已清理。
- 本轮未修改外部 Mecha-QA、Python Bridge、NebulaGraph 图空间、模型权重或凭据；RANKED 固定系统提示词守卫继续通过。

## 教学总览可视化（2026-11-22）

- 新增 `TeachingDashboardCharts.vue` 和 `teachingDashboardCharts.ts`，复用现有 ECharts 依赖渲染学习活动趋势、薄弱知识点掌握度排行；数据完全来自教学看板既有响应，不修改后端接口或匿名统计口径。
- 定向测试：`teachingDashboardCharts.test.ts` 与 `TeachingDashboardView.test.ts` 共 `2` 个文件 / `3` 项通过。
- 前端全量回归：`44` 个测试文件 / `84` 项通过；`npm run build` 成功，输出无 chunk warning。
- 双角色 Playwright：`node scripts/authenticated-ui-smoke.mjs` 覆盖 28 条路径 × 2 角色 × 2 分辨率，共 `112/112`；无控制台错误、页面异常、横向溢出、默认控件或未样式化表格问题。报告只验证已登录态前端渲染与排版，不替代真实后端/图谱联调；临时 `.ui-audit/` 已清理。

## 平台重构与终版验收测试报告（2026-11-24）

本章节记录平台重构全阶段（M1～M4）完成后的最终全量自动化测试与运行态验收数据。所有指标均基于当前工作区真实执行并校验通过。

### 1. 执行环境与基准命令

- **操作系统**：Windows 11 (PowerShell 7)
- **Java 环境**：Temurin OpenJDK 21.0.13 (`D:\develop\Java\temurin-21\jdk-21.0.13+9`)
- **Node.js 环境**：v20+ / npm 10+
- **测试执行命令**：
  ```powershell
  # 1. Java 后端三模块全量回归
  $env:JAVA_HOME = "D:\develop\Java\temurin-21\jdk-21.0.13+9"
  $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
  mvn -f backend/pom.xml test "-Dsurefire.failIfNoSpecifiedTests=false"

  # 2. 前端单元与集成测试（Vitest）
  cd frontend
  npm test

  # 3. 前端 AST 幽灵类名扫描
  node scripts/scan-vue-classes.mjs --check

  # 4. 前端生产打包构建
  npm run build

  # 5. 双角色运行态视觉巡检（Playwright UI Smoke）
  node scripts/authenticated-ui-smoke.mjs

  # 6. Docker Compose 配置静态校验
  docker compose --env-file deploy/.env.server.example -f deploy/compose.server.yml config --quiet
  ```

### 2. 测试套件结果汇总

| 类别 / 模块 | 涉及范围 / 测试文件 | 执行用例数 | 通过数 | 失败 / 错误 | 状态 |
| --- | --- | ---: | ---: | ---: | --- |
| **Java** `learning-service` | 44 个测试类（含 M3 新增认证/密码、实验分组流转、受限实验、固定案例目录等） | 175 | 175 | 0 / 0 | **PASSED** |
| **Java** `ai-service` | 20 个测试类（含 GroundedEvidenceValidator、ComplianceGuard 等） | 102 | 102 | 0 / 0 | **PASSED** |
| **Java** `learning-gateway` | 1 个测试类（GatewaySecurityTest 鉴权路由边界） | 5 | 5 | 0 / 0 | **PASSED** |
| **Java 后端总计** | **65 个测试类** | **282** | **282** | **0 / 0** | **100% PASSED** |
| **前端** Vitest 单元/集成 | 56 个测试文件（组件状态机、API 客户端、合规守卫、草稿防丢拦截等） | 189 | 189 | 0 / 0 | **100% PASSED** |
| **前端生产构建** | Vite 生产编译（Rolldown / Three.js 与 G6 独立分包） | - | - | 0 chunk warnings | **SUCCESS** |
| **AST 幽灵类名扫描** | 49 个 SFC 视图组件，1,155 处模板类名引用 | 1,155 | 1,155 | 0 缺失 | **100% MATCH** |
| **UI Smoke 视觉矩阵** | 29 条页面路由 × 双视口 × 双角色 + 4 个专项业务场景 | 170 | 170 | 0 异常 | **100% PASSED** |

### 3. API 隔离运行态巡检 vs 真实后端联调分栏说明

为确保工程与学术陈述的诚实性，测试体系明确区分以下两类运行验证：

| 验证维度 | 机制与数据源 | 覆盖范围与断言目标 | 结论边界（不越界声明） |
| --- | --- | --- | --- |
| **API Fixture Smoke 运行态巡检** (`authenticated-ui-smoke.mjs`) | 使用 Vite Preview 独立测试服务器，通过 Playwright 注入规范化双角色认证状态与最小合法 JSON Fixture。 | 覆盖 1440×900 和 1280×800 两种视口下 29 条页面路由（含 Canonical 路由与 302 重定向），以及学生受限实验、密码修改弹窗、草稿防丢拦截、A→B 顺序展示等 4 个专项场景（共 170 场景）。断言 0 控制台错误、0 未捕获异常、0 布局横向溢出、0 默认原生控件泄漏。 | **仅验证前端 DOM 挂载、响应式排版契约与双角色路由守卫有效性**；不替代真实全栈高并发/分布式图谱环境联调。 |
| **Real Backend Integration 自动化集成测试** | 基于 Spring Boot Test 与 H2 内存数据库，在真实 Spring 上下文中运行 HTTP/Service 链路。 | 覆盖密码修改与 Refresh 令牌级联撤销、实验分组与任务顺序权威控制、学生作答与答案服务端隔离、固定案例目录只读访问等业务契约。 | 验证业务核心状态机与数据安全隔离契约；**独立 MySQL/NebulaGraph/Qwen API 真实线上联调在此单元/集成套件中标记为 `not run`**（需专用外部网络与容器基础设施）。 |

### 4. 运行态 Smoke Matrix 覆盖明细（170 场景）

- **视口配置**：桌面基准（`1440×900`）与笔记本基准（`1280×800`）。
- **角色分布**：
  - **学生端角色（STUDENT）**：85 个场景（含 5 大核心学习模块、受限实验运行页 `/experiment-run`、任务顺序展示、密码修改安全弹窗、草稿离开确认等）。
  - **管理员角色（ADMIN）**：85 个场景（含教学总览、内容与题库中心、实验与评价中心 3 大工作台及 Tab 切换、权限拦截越权回到首页等）。
- **专项场景实测结果**：
  1. **学生受限实验运行页**：成功隔离参考答案与评分 Rubric，不向学生端暴露；
  2. **实验组评估顺序**：学生任务卡片权威展示 `顺序 A→B`；
  3. **未保存草稿离开拦截**：表单处于 dirty 状态时准确弹出离开确认对话框，防止意外丢弃；
  4. **修改密码安全对话框**：新旧密码一致性及必填校验完全符合安全交互规范。
- **巡检结论**：170 场景全部通过，无控制台错误，无水平滚动条溢出，巡检生成的临时截图目录 `.ui-visual-review` 已自动清理。

### 5. 全局四份 CSS 体系与代码卫生扫描

| 文件 | 精确字节数 (Bytes) | `!important` 次数 | 核心职责归属 |
| --- | ---: | ---: | --- |
| `styles-design-system.css` | 28,734 B | 0 | 全局 Design Tokens、排版规范、状态语义色、原子工具类 |
| `styles.css` | 15,535 B | 0 | 基础通用布局、导航栏、顶部栏、弹窗卡片与通用反馈骨架 |
| `styles-learning.css` | 11,389 B | 0 | 学习中心、微课、练习、测试与评估向导等业务样式 |
| `styles-graph.css` | 3,297 B | 0 | 知识图谱画布容器、图谱抽屉与缩放工具条专用样式 |
| **四 CSS 合计** | **58,955 B** | **0** | **全仓 Vue SFC 与全局样式 `!important` 严格清零** |

- **样式契约验收**：通过 `frontend/tests/design-system-contract.test.ts` 自动化断言四文件全部存在、加载顺序一致，且全仓 `frontend/src` 中 `!important` 出现次数为 **0**。四份样式表完整覆盖 5+3 路由体系与复杂业务交互，无冗余幽灵样式。

### 6. 数据库迁移与安全凭据合规核验

- **数据库迁移版本状态**：
  - 当前 Flyway 迁移链已包含 `V16__add_task_group_order.sql`，支持 `task_group_order` 组间评估顺序字段与实验任务状态机持久化；
  - **基线说明（2026-11-24 M1–M4 快照）**：截至 2026-11-24 收口时终版 Schema 为 V16，真实 MySQL 端到端迁移在当时标记为 `not run`；此后 2026-11-25 在 `feat/learning-enhancements` 分支已新增并实测应用 V17（见下文终版章节）。
- **安全与学术合规守卫**：
  - `compliance-guard.test.ts`（4/4 通过）：严禁向前端暴露任何系统内部固定提示词（如 RANKED 标识或具体 Prompt 正文）；严禁在界面与报告中出现“降低认知负荷”、“采集生理信号”等无依据的越界学术宣称；
  - 敏感凭据扫描：本地执行全仓正则与敏感词排查（`Get-Command gitleaks` 在本地 Windows 环境未安装，标记为 `not run`；已通过 `rg` 针对各类 API Key、Token、私钥和密码模式全面排查），全仓仅包含测试用 Mock 占位符（如 `dummy-token`、`test-password`），无任何真实线上凭据泄露。
- **Docker Compose 配置合规**：
  - 运行 `docker compose --env-file deploy/.env.server.example -f deploy/compose.server.yml config --quiet` 退出码为 0，46 个环境变量全部与模板规范严格对齐。

## 学习增强终版增量验收与真机联调封口报告（2026-11-25）

本章节汇总 `feat/learning-enhancements` 分支在完成【句子级知识图谱内联引用联动】、【软件工程 Mermaid 交互图表渲染】、【V17 双值掌握度（历史峰值 + 14 天半衰期衰减保持度）与遗忘预警】及【费曼“以教促学”图谱对照自检】四项增强，并完成真机全栈增量联调与 `d6b816d` 定点修复后的终版实测结果。

### 1. 终版全量自动化测试与静态扫描基线

| 验收维度 | 验证命令与范围 | 实测结果 | 结论 |
| --- | --- | ---: | --- |
| **Java 后端总体验收** | `mvn -f backend/pom.xml test "-Dsurefire.failIfNoSpecifiedTests=false"` | **311/311 passed** | **100% 通过** |
| ├─ `learning-service` | 含 `KnowledgeMasteryPeakMigrationTest`、`MasteryRetentionPolicyTest` 等全部测试 | 192/192 passed | 0 失败 / 0 错误 |
| ├─ `ai-service` | 含 `FeynmanEvaluationServiceTest`（提交 `d6b816d` 新增配额与截断清洗回归后由 113 增至 114）、`FeynmanControllerTest`、`AiSecurityConfigTest` | 114/114 passed | 0 失败 / 0 错误 |
| └─ `learning-gateway` | `GatewayRouteConfigurationTest`（含 `/api/ai/feynman/evaluate` 与 SSE 路由匹配） | 5/5 passed | 0 失败 / 0 错误 |
| **前端单元与集成测试** | `cd frontend; npx vitest run --maxWorkers=2` | **61 文件 / 237 项 passed** | **100% 通过** |
| **AST 模板类名扫描** | `cd frontend; node scripts/scan-vue-classes.mjs --check` | 53 个 Vue 文件 / **1,552** 处用法 / **0 missing classes** | **100% 匹配** |
| **Playwright 视觉冒烟** | `cd frontend; node scripts/authenticated-ui-smoke.mjs`（1440×900 与 1280×800 双视口） | **180/180 场景 passed** | **100% 通过** |
| **前端生产构建与 CSS** | `npm run build` 及 `git grep -n "!important" frontend/src/` | 构建 0 error / **0 chunk warning**；四份全局 CSS 共 **66,159 B**，全仓 `!important` 为 **0** | **通过** |

- **全局 CSS 精确字节基线**：`styles-design-system.css`（30,169 B）、`styles.css`（20,374 B）、`styles-learning.css`（11,389 B）、`styles-graph.css`（4,227 B），全仓 `!important` 严格为 `0`。

### 2. 真实全栈增量联调核验记录（两轮实测与定点修复）

在真实本机环境（Windows 11 / Temurin JDK 21.0.13+9 / MySQL 8.0.33 `software_learning` / Redis / RabbitMQ / NebulaGraph Desktop `9669` / 只读 Python Bridge `8999` / DeepSeek `deepseek-flash`）依次打包最新 JAR 并执行以下实测：

1. **真实 MySQL 8 Flyway V17 迁移与双值掌握度接口**：
   - `learning-service` 启动日志实时记录 `Migrating schema software_learning to version "17 - knowledge mastery peak"` -> `now at version v17 (00:00.205s)`；
   - `flyway_schema_history` 中 `version=17, success=1`；`knowledge_mastery.peak_mastery`（`decimal(5,2) NOT NULL DEFAULT 0.00`）创建且 12 条既有记录自动回填 `peak_mastery = mastery_score`（包括基线用户 `user_id=1` `student_f2021a07` 的 `33.33 -> 33.33`，并通过管理员接口 `GET /api/admin/users/1/profile` 只读核对基线档案；严守凭据边界未重置其密码或伪造 JWT）；
   - 通过网关调用 `GET /api/dashboard/overview`、`GET /api/knowledge/mastery-map`（8 节点）与 `GET /api/knowledge/learning-path?targetId=2` 均返回 `200 OK`，完整携带可选双值字段 `peakMastery`、`effectiveMastery`、`daysSinceLastPractice`、`reviewUrgency`。
2. **费曼在线权限边界、思考模型配额Bug暴露与修复后复验**：
   - **系统状态与鉴权边界**：`GET /api/system/status` 返回 `200 OK`（`overallStatus=UP`，`mysql`/`redis`/`rabbitmq`/`knowledge`/`ai` 均为 `UP`）；匿名调用 `POST /api/ai/feynman/evaluate` 返回 `401 Unauthorized`；管理员账号 `slp_admin` 越权调用返回 `403 Forbidden`；
   - **第一轮真机暴露 Bug 与定点修复（`d6b816d`）**：首轮真机联调发现 `deepseek-flash` 思考模型单次消耗 `reasoning_tokens=827`，导致默认 `max_tokens=1024` 预算下正文仅输出 752 字符即触发 `finish_reason=length` 截断，且原兜底分支将半截 JSON 原文写入 `misconceptions[0]`。提交 `d6b816d` 后，[FeynmanEvaluationService.java](file:///d:/develop/PythonProject/pythonallwork/毕设/software-learning-platform/backend/ai-service/src/main/java/edu/wfust/learning/ai/FeynmanEvaluationService.java) 显式采用 `FEYNMAN_EVALUATE_MAX_TOKENS = 4096` 并对截断/原始 JSON 片段执行安全中文兜底清洗；
   - **第二轮重新打包复验通过**：热加载 Python Bridge（8999，检索“需求分析”返回 7 条三元组）后，临时学生账号经网关调用 `POST /api/ai/feynman/evaluate` 返回 **`200 OK`**：`coveredPoints` 返回 2 条清晰要点、`missingTriples` 返回 5 条遗漏三元组（全部为真实 `evidenceTriples` 7 条的子集，准确包含《软件需求规格说明书》）、`misconceptions` 返回 1 条自然语言诊断且零 `{` / 残缺 JSON 片段、`followUpQuestion` 可读（36 字符），响应体逐字扫描 **0 后台系统提示词泄露**。
3. **费曼离线诚实降级与环境清理复原**：
   - 临时停止 Python Bridge（8999，`PORT_8999_LISTENING_COUNT=0`）后再次调用 `POST /api/ai/feynman/evaluate`，系统诚实返回 `503 Service Unavailable`（`code="KNOWLEDGE_UNAVAILABLE"`），绝不编造假三元组；
   - 验收结束后已停止全部本轮临时启动的 Java/Python 后台进程（`8080/8081/8082/8999/5173` 监听数均归零），并在 MySQL 事务内按主键精确删除本轮全部临时验证学生账号及其关联记录，确认 `sys_user` 无残留验证用户、`knowledge_mastery=12`、`ai_experiment_run=12`、`learning_evaluation_session=2` 与基线一致。
