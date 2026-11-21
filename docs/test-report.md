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
