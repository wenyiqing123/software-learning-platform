# 软件工程课程学习平台设计规格

## 目标

在 D:\develop\PythonProject\pythonallwork\毕设\software-learning-platform 中构建可运行、可答辩的软件工程课程学习平台。平台通过 HTTP 调用现有 Python Knowledge Bridge，读取当前 air_ticket NebulaGraph 图空间；图谱和 Python 项目均只读、不修改。

## 用户与范围

- 学生：登录、浏览课程和知识图谱、AI 问答、练习题目、管理错题、查看学习推荐。
- 管理员：维护题库和课程映射，查看问答模式对比及评价指标。
- 首发课程：软件工程，示例知识包括机票预订系统、软件生命周期、需求分析、DFD、UML、数据存储和软件维护。
- 首发问答模式：直接大模型、图谱三元组加大模型、相关性排序与评分三元组加原始提示词的大模型。
- 评测模式额外支持排序/评分消融组合，确保与参考论文实验设计相对应。

## 架构

- learning-gateway：Spring Cloud Gateway，统一对外端口和转发规则。
- learning-service：Spring Boot 模块化业务服务，负责认证、课程、图谱展示代理、题库、答题、错题、学习事件及推荐。
- ai-service：Spring Boot + Spring AI，负责三种问答策略、Python Knowledge Bridge 客户端、流式输出和实验记录。
- MySQL 保存课程、答题、错题、推荐快照、学习事件、课程阅读进度、助教会话与实验评测；Redis 使用 slp: 前缀缓存推荐结果；RabbitMQ 异步处理答题事件并更新掌握度及相关缓存。
- NebulaGraph 仅由已有 Python Bridge 访问。Java 不直连 NebulaGraph、不写入图空间。
- Nacos 不作为本地必需组件：已安装实例是 2.2.0，与新一代 Spring Cloud Alibaba/Nacos 组合不作未经验证的混用。第一版网关使用项目内配置的显式服务地址；服务发现作为可选后续扩展。

## 兼容性基线

- JDK 21 LTS；下载包必须经过 SHA-256 校验，项目脚本显式指定项目外的 JDK 安装目录。
- Spring Boot 4.x、Spring Cloud 2025.1.x、Spring AI 2.x、MyBatis-Plus Boot 4 starter；采用同一稳定兼容矩阵中的 GA 版本，不使用 milestone、RC 或 snapshot。
- Vue 3 + TypeScript + Vite + Pinia + Element Plus；图谱使用 G6，统计图使用 ECharts。
- 本机现有 MySQL 8.0.33、Redis 5.0.14.1、RabbitMQ 4.2.3 已可提供本项目所需能力。应用只创建专用 schema、专用 Redis key 前缀和 RabbitMQ vhost/queue，不升级、清理或改写已有业务数据。
- API Key 通过环境变量注入；不复用仓库中已有脚本的硬编码密钥。

## 服务契约

Python Bridge：

- GET /health
- POST /api/knowledge/retrieve
- 请求固定带 spaceId=1；对实体浏览传入实体名称和 topK；Java 对网络超时、HTTP 503、无证据结果做明确处理。
- 模式 3 必须原样使用 Python 返回的 prompt；模式 2 只将 Python 返回的三元组放入提示词，不注入相关性分数。

Java Gateway API：

- /api/auth/**：注册、登录、当前用户。
- /api/courses/**、/api/knowledge/**：课程、知识点和图谱数据。
- /api/quizzes/**、/api/wrong-book/**：练习、答题、错题和复习。
- /api/recommendations/**、/api/dashboard/**：推荐和学习概览。
- /api/ai/**：问答、SSE 和模式对比。
- /api/admin/**：题库维护和实验查询。

## 核心数据

MySQL 表：sys_user、auth_refresh_token、course、course_chapter、knowledge_point、quiz_question（选项和答案 JSON 列）、quiz_attempt、wrong_question、learning_event、knowledge_mastery、recommendation_snapshot、course_study_section、course_study_progress、ai_session、ai_message、ai_experiment_run。V1–V5 Flyway 迁移已应用到专用 software_learning 库。

Redis：推荐查询缓存使用 slp:recommendation:{userId} 和五分钟 TTL。JWT 为无状态访问令牌，刷新令牌哈希保存在 MySQL；当前未实现课程树或图谱结果缓存。

RabbitMQ：专用 slp.learning.events exchange；答题和学习行为消息带唯一事件 ID，消费者幂等，失败消息进入 retry/dead-letter 队列。

## 推荐算法

采用可解释的规则推荐，不训练新模型。对每个知识点结合错误频次、掌握度、前置知识关系、距最近练习时间、练习次数与课程顺序计算优先级；前置知识未掌握时显式给出先修建议。推荐结果写 MySQL 快照并缓存 Redis。答题事件由 RabbitMQ 异步更新掌握度；推荐读取时若无缓存则同步计算。尚未建模主观兴趣行为。

## 问答对比与评价

- 统一问题、模型、temperature、token 上限、topK 和证据集，切换策略变量。
- 模式 A：不访问 Python，直接大模型。
- 模式 B：检索三元组，不向 Prompt 注入评分。
- 模式 C：使用原 Python 排序评分结果和原始 Prompt。
- 评测附加 without-sort、without-score、without-sort-and-score 消融模式。
- 保存模式、识别实体、三元组、Prompt、答案、耗时、token 用量（供应商可提供时）和人工评价。
- 当前实现保存参考答案驱动的 Unicode 字符级 ROUGE-L、有效阶数 BLEU（最多四阶）、期望实体召回率、人工 1–5 评分、平均/P95 延迟和错误率。没有参考答案或期望实体时，不生成相应文本指标。
- 当前未实现 LLM-as-a-judge 的 LLM-Score，也不宣称自动答案完全正确；论文需自行准备评测集、给出人工评分指南，并按统一问题与模型配置分组分析。

## 可靠性与安全

- JWT + BCrypt；所有业务记录按用户隔离；所有外部输入使用 Bean Validation。
- Python、LLM、MySQL、Redis、RabbitMQ 分别设置连接超时与可读错误信息。
- 题目提交先事务持久化，再在提交后发布 RabbitMQ 事件；事件重复消费不得重复增加答题数。
- 不在日志中输出 API Key、密码、完整 JWT 或敏感用户字段。
- Python Bridge 或 LLM 不可用时，课程、题库和错题等非 AI 功能仍可正常工作。

## 验收

- Windows 本机按 README 可启动；MySQL/Flyway 仅作用于专用 software_learning 数据库。
- 用户可完成登录、图谱浏览、问答模式对比、做题、错题复习和推荐闭环。
- Python 调用的 spaceId 恒为 1；不存在任何 Java 图数据写接口。
- 单元、接口、组件和端到端测试通过；外部服务缺失的场景有可重复的模拟测试。
- Mecha-QA 与 agent-chat-studio 工作树保持原样。

## 实施与验证状态（2026-11-12）

- 本地 Java、Vue、MySQL 专用 schema 与 RabbitMQ vhost 已实际启动验证；课程/练习/错题/推荐/会话接口通过真实服务烟测。
- 本轮启动本机既有 NebulaGraph Desktop 容器和只读 Python Bridge。对 spaceId=1 的软件工程图谱读取成功，需求分析可视化返回 8 节点、7 条边、7 条证据。
- DeepSeek deepseek-flash 已配置在 ignored .env.local。DIRECT、TRIPLES、RANKED 及四种消融模式真实运行全部成功；RANKED 提示词与同问题 Python Bridge 返回值完全一致。实验结果和七项评分已保存到 MySQL。
- Java Bridge 客户端明确使用 HTTP/1.1，与现有 Uvicorn Bridge 兼容；未修改 Mecha-QA Python 源码、导入/写入图空间、重建容器或重置卷。
- 详细测试数量、验收边界及性能/学术指标限制见 docs/test-report.md 和 logs/live-nebula-deepseek-verification.log。
