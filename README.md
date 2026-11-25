# 知序 · 软件工程课程学习平台

基于既有 Mecha-QA 的 Python Knowledge Bridge 接入软件工程知识图谱，实现课程阅读、图谱浏览、智能问答、题库练习、错题复习、可解释推荐与问答对照实验。

交付目录：D:/develop/PythonProject/pythonallwork/毕设/software-learning-platform。

## 项目边界

- Mecha-QA 与 agent-chat-studio 是只读参考和依赖，不修改其中的代码与数据。
- Java 只调用 Python Bridge 的 GET /health 和 POST /api/knowledge/retrieve，固定 spaceId=1，继续使用既有 air_ticket 图空间。
- MySQL 使用 software_learning 专用库；RabbitMQ 使用 /software-learning 专用 vhost；Redis 使用 slp: 前缀。
- 保留本机 MySQL 8.0.33、Redis 5.0.14.1、RabbitMQ 4.2.3、Nacos 2.2.0 的原安装与业务数据。未升级这些服务。
- 网关使用显式服务地址，当前运行不依赖 Nacos。

## 模块与版本

| 模块 | 地址 | 职责 |
| --- | --- | --- |
| Vue 3 前端 | http://127.0.0.1:5173 | 学生页面与管理端 |
| learning-gateway | http://127.0.0.1:8080 | Spring Cloud Gateway 统一路由 |
| learning-service | http://127.0.0.1:8081 | 认证、课程、题库、错题、推荐、记录和评测 |
| ai-service | http://127.0.0.1:8082 | 三种问答、同证据对比、SSE、Python 与模型调用 |
| 既有 Python Bridge | http://127.0.0.1:8999 | 实体识别、图检索、评分与原始提示词 |

JDK 为 Temurin OpenJDK 21.0.13+9，安装于 D:/develop/Java/temurin-21/jdk-21.0.13+9。脚本显式选择该版本，不修改系统 JAVA_HOME。

后端版本锁定在 backend/pom.xml：Spring Boot 4.1.2、Spring Cloud 2025.1.4、Spring AI 2.0.2、MyBatis-Plus 3.5.18。前端依赖以 frontend/package-lock.json 为准，TypeScript 固定 5.9.3。

## 启动

本机双击入口位于项目上一级：`../一键启动学习平台.bat`，配套 `../start-all.ps1`。先检测前端 5173，复用已启动的本项目进程，只补齐缺少的 Java/Vue 服务；按顺序启动并等待既有 NebulaGraph 容器健康。Python Bridge 必须再通过“需求分析”的真实三元组检索才报告全部就绪，首次检索会预热评分模型。Bridge 未运行或旧连接失效时明确报错，按同目录 `一键启动说明.md` 中的原有 `run.ps1` 方式启动或重启 Bridge。命令行复用前后端可以执行 `./scripts/start.ps1 -ReuseRunning`；已打包且代码无变化可再加 `-SkipBuild`。

本机已配置被 Git 忽略的 .env.local，保存专属数据库、消息队列账号、JWT 密钥与本地管理员引导配置。不要提交或公开此文件。

在 PowerShell 运行：

    Set-Location 'D:/develop/PythonProject/pythonallwork/毕设/software-learning-platform'
    ./scripts/start.ps1

脚本打包后端、启动三个 Java 服务和前端，并逐个验证健康状态。失败时检查 logs/ 下对应的 out.log 和 err.log。已打包且代码未变时可使用 ./scripts/start.ps1 -SkipBuild。

知识图谱和 Python Bridge 依赖本机 Docker Desktop 中既有 NebulaGraph Desktop 容器与 Mecha-QA 的独立 Python Bridge。数据库、Redis、RabbitMQ 已由本机服务提供；Docker Desktop 可用时，仓库外原容器仍需先启动：

```powershell
docker compose -f "$env:APPDATA/nebulagraph-desktop/.nebulagraph-desktop/docker-compose.yml" start
```

然后在另一个 PowerShell 窗口启动只读 Python Bridge：

```powershell
Set-Location 'D:/develop/PythonProject/pythonallwork/Mecha-QA/python-knowledge-bridge'
./run.ps1
```

Bridge 使用原 Mecha-QA 项目目录、模型文件和 `air_ticket` 图空间；它只执行检索，不导入数据或写图。图空间 ID 1 在本地全量验收中读取到软件工程关系。如 Docker Desktop 未自动带起 graphd，可在 NebulaGraph Desktop 容器启动且 metad/storaged healthy 后再次执行上述 `docker compose start`，不要使用 `down`、`rm` 或重新导入。

## Docker 服务器部署

当前部署目标为 `120.26.186.201`，公网演示地址为 `http://120.26.186.201/`。该入口仅为 HTTP，不应输入真实或重复使用的密码。部署复用服务器既有 MySQL、RabbitMQ、Redis 与 NebulaGraph；不会清空或重建这些容器/数据卷，也不会修改 Mecha-QA 源码。平台仅使用 `software_learning` 数据库、`/software-learning` vhost、`slp:` Redis 键空间和只读 `air_ticket` 图空间。

本机 Docker Desktop 启动后，在 PowerShell 执行以下命令构建五个 Linux/amd64 镜像（脚本会运行完整 Java/前端测试）：

```powershell
Set-Location 'D:/develop/PythonProject/pythonallwork/毕设/software-learning-platform'
./scripts/build-server-images.ps1 -JavaHome 'D:/develop/Java/temurin-21/jdk-21.0.13+9'
```

镜像与服务器参数见 `deploy/compose.server.yml` 和无密钥模板 `deploy/.env.server.example`。部署仅将镜像、配置、Mecha-QA `nebula` Python 模块及评分模型传至服务器；服务器凭据保存在 `/opt/software-learning-platform/.env.server` 并限制权限。具体步骤、数据服务隔离、Nginx 备份和回滚见 `scripts/deploy-server.ps1`。禁止对既有服务执行 `down -v`、volume 删除或 Docker prune。

完整 smoke test 会提交登录、答题和管理请求，必须经 SSH 隧道运行，不能将 `.env.local` 中的管理员凭据或 Bearer token 发送到公网 HTTP：

```powershell
$tunnel = Start-Process ssh.exe -WindowStyle Hidden -PassThru -ArgumentList @(
    '-o', 'BatchMode=yes', '-o', 'ExitOnForwardFailure=yes',
    '-N', '-L', '127.0.0.1:18094:127.0.0.1:18084', 'root@120.26.186.201'
)
try {
    Start-Sleep -Seconds 2
    ./scripts/smoke-test.ps1 -BaseUrl 'http://127.0.0.1:18094'
} finally {
    Stop-Process -Id $tunnel.Id -ErrorAction SilentlyContinue
}
```

服务器约 1.7 GiB 内存；Python 模型首次加载期间需监控 `docker stats`、可用内存和 swap。若出现持续 OOM 或 swap 增长，应回滚应用入口并先扩容。该小规格 HTTP 演示服务器不是生产环境。

停止本项目：

    ./scripts/stop.ps1

停止脚本识别自身的 Java/Node 进程，不停止本机 MySQL、RabbitMQ 等依赖。

## 新电脑配置

1. 安装 JDK 21、Maven、Node.js、MySQL 8、Redis、RabbitMQ。
2. 复制 .env.example 为 .env.local，设置随机 JWT_SECRET 和专属服务凭据。
3. 创建空的 software_learning 数据库及仅限该库的账号；创建 /software-learning RabbitMQ vhost 及专属账号。不要连接已有业务库。
4. 在 frontend/ 执行 npm ci；start.ps1 缺少 node_modules 时也会执行安装。
5. 启动本机依赖后运行脚本，由 Flyway 自动应用数据库迁移。

`.env.example` 覆盖本地 MySQL、Redis、RabbitMQ、JWT、Python Bridge、Gateway、模型超时、RANKED/Qwen 可选开关和管理员引导变量；服务器部署使用 `deploy/.env.server.example`，其中只放占位符。Compose 会显式传递 `RABBITMQ_VHOST`、Python 图谱超时、Gateway 地址、AI 生成参数和可选 RANKED 参数，实际密钥只写入服务器的受限 `.env.server`。

当前专用数据库已通过 Flyway 应用至 V17（包含 V17 为 `knowledge_mastery` 增加并自动回填的单调历史峰值 `peak_mastery` 列，以及 V16 为实验组增加的可空 `task_order` 评估顺序字段）。已经执行的迁移保持原样；历史迁移严格不可变，未来扩展预留 V18+。不要修改已执行的 SQL 或清空 Flyway 历史。

## 账户、工作台与权限边界

### 双角色空间与工作台架构

平台严格实施基于角色的访问控制（RBAC）与工作区分割：

- **学生学习空间（5 大核心学习分组，16 个 Canonical 路由）**：
  - 基础学习：课程阅读（`/courses`）、概念知识图谱（`/knowledge`）、先修掌握图谱（`/mastery-map`）、AI 交互助教（`/tutor`）；
  - 题库练习：基础章节练习（`/practice`）、自适应强化练习（`/adaptive-practice`）；
  - 复习规划：错题笔记本（`/wrong-book`）、艾宾浩斯间隔复习（`/review`）、今日动态学习计划（`/study-plan`）；
  - 微课微测：微课浏览中心（`/micro-lessons`）、学习成效评估向导（`/evaluation`）、学情报告与雷达图（`/learning-report`）；
  - 受限任务：指派实验任务入口（`/experiment-tasks`）、受限实验运行页（`/experiment-run`）。
- **管理员管理空间（3 大聚合工作台中心）**：
  - 教学总览工作台（`/teaching-dashboard`）：班级学习指标、学习活跃度、薄弱知识点分布与匿名学情导出；
  - 内容与题库中心（`/admin/content`）：集成课程与知识点管理、题库版本与批次 JSON 导入、干扰项错因诊断配置、已发布微课管理；
  - 实验与评价中心（`/admin/experiments`）：集成多模式问答对照实验、实验任务配组与学生派发、去标识化心理努力与成效统计；
  - 兼容保留独立问答实验入口（`/experiments`）以及 7 条 Legacy 管理端重定向路由。

### 安全凭据、密码修改与 Single-flight 刷新

- **修改密码与令牌撤销**：提供安全修改密码接口（`PATCH /api/auth/password`），客户端弹出修改密码安全对话框，校验当前密码与新密码复杂度（8~72 字符，新旧密码不一致校验）。密码修改成功后，服务端事务内主动撤销（revoke）该用户所有的活跃 Refresh Token；客户端一次性彻底清理本地凭据并安全重定向至登录页。
- **并发 401 令牌刷新（Single-flight）**：HTTP 客户端拦截器实现并发请求 Single-flight 保护。当多个异步请求几乎同时收到 401 响应时，仅发出唯一一次 `/api/auth/refresh` 请求，挂起的请求排队等待；刷新成功后携带新 Token 恰好重放一次；刷新失败则统一注销会话并拒绝所有等待中的请求，彻底杜绝死循环和并发重刷竞争。

### 实验分组顺序与评估向导权威性

- **V16 组间任务顺序**：`V16__experiment_group_task_order.sql` 为实验组持久化 `task_order`（可设置为 `'A→B'` 或 `'B→A'`，默认可空）；该字段表示预先设定的评估测验顺序（**不代表随机化**），由管理员在任务中心配组时指定。
- **服务端权威状态机**：评估向导步骤与任务顺序严格以服务端返回的 `taskOrder` 和 `completedPhases` 为唯一权威，客户端严禁伪造解锁或跳过前置阶段；问答运行不提前结单，必须完成完整向导并在后测提交后闭环结单。
- **受限运行与答案隔离**：学生在受限实验运行页（`/experiment-run`）完成任务时，服务端严格隔离参考答案与评分标准，客户端不呈现评分标准与参考答案。

### 固定案例目录与批次实验

- **固定案例目录（ADMIN-only）**：`GET /admin/experiments/fixed-suite` 为管理员提供 8 个预置案例清单（复用 V4 种子题干），仅包含 `caseId`、`question`、`sourceSection` 与 `knowledgePoint`，不包含答案 key 也不构成参考答案语料。
- **纯浏览器内存批次队列（Browser-only batch queue）**：批次实验完全在浏览器端内存中串行调度，逐项调用真实实验接口并独立保存，失败继续推进；队列与进度不声明服务端持久队列或刷新恢复；支持实时进度、暂停、继续、跳过与错误重试；支持包含套件元数据的 UTF-8 BOM CSV 与 JSON 导出。

/admin 兼容路由支持课程新增/编辑/启停、题目新增/编辑/启停和同课程知识点映射，校验单选/多选/判断题的选项与标准答案。学生列表不返回标准答案，提交后才返回正确答案与解析。

/courses 提供软件工程正文、目标与案例；阅读完成状态按用户保存。图谱页按课程概念查询关系、查看三元组并跳转助教提问。

登录后的顶部“依赖状态”入口会按固定间隔检查 MySQL、Redis、RabbitMQ、Python Bridge/知识图谱和 AI 服务。状态接口只返回健康状态、检查时间和受影响功能，不返回内部地址、异常栈或凭据；图谱或模型不可用时，课程阅读、题库和错题复习仍保持可用。

知识图谱页还提供“先修学习路径”视图：选择目标知识点后，平台按课程内先修关系递归整理学习顺序，展示当前用户的系统掌握度与基于距上次有效学习天数的可解释遗忘衰减保持度（间隔 ≤ 1 天不衰减，超过 1 天按常数半衰期 `HALF_LIFE_DAYS = 14` 估算 `effectiveMastery` 与 `FRESH`/`FADING`/`DUE` 复习预警，先修解锁门槛始终以单调 `peakMastery` 为准、绝不因久未复习而反复锁死），并链接到对应课程阅读、专项练习、间隔复习和图谱实体。管理员页面增加只读的先修关系质量检查，用于发现坏 JSON、无效/重复/跨课程引用、自引用和循环依赖；系统不会自动修改课程关系。

## AI 与现有 Python 依赖

通过 OPENAI_BASE_URL、OPENAI_API_KEY、OPENAI_MODEL 可接入兼容 OpenAI Chat Completions 协议的服务。当前本机已配置 DeepSeek `deepseek-flash`，AI 直答、三种模式和消融模式都已实测成功；密钥只保存在被 Git 忽略的 `.env.local`。地址和错误契约见 `tests/docs/ai-contract.md`。更换模型配置后重启服务。

本地 Qwen RANKED 验收脚本位于 `scripts/qwen-ranked-backend-acceptance.ps1` 和 `scripts/qwen-ranked-five-case-acceptance.ps1`，脚本只读取 `.env.local` 并输出脱敏验收结果，不把凭据写入仓库；默认开关仍为关闭。

未配置模型密钥时会明确提示，不会伪造答案；非 AI 学习模块仍可使用。

PYTHON_KNOWLEDGE_URL 指向既有 Python Bridge。本项目脚本不启动或修改 Mecha-QA；Python Bridge 与 NebulaGraph 需要按原项目方式运行。离线图谱返回 503，不使用户退出登录，也不以示例图代替真实结果。

三种问答：

- DIRECT：直接调用大模型，不访问 Python。
- TRIPLES：使用共享检索三元组，不向提示词注入相关性分数。
- RANKED：普通助教交互原样使用 Python 返回的排序评分提示词。问答实验比较使用 `ranked-grounded-v3`：先从真实候选三元组中选择直接证据，再要求答案明确覆盖每条证据的主体、关系和宾语，并按关系语义使用自然汉语连接；“规则、类型、方式、形式”等标签关系直接连接宾语，不添加“是/为”，“实质、定义、含义”等名词性判断关系使用“是/为”，不机械用冒号替代连接词。校验缺项时失败，不计入答案指标或人工评分。Python 原始提示词仍作为 user 消息原样发送，服务端系统提示词不返回前端。`provenance` 与证据轨迹记录版本、候选、选择、原始模型输出、耗时和 token。

实验由后端执行并保存实际 AI 返回值，使用相同问题、模型与生成参数以及共享证据。消融实验按接口实际能力开放；不会将未知原始顺序伪装成无排序结果。

支持参考答案、期望实体、字符级 ROUGE-L、BLEU（最高四阶、有效阶数）、实体召回率、人工 1–5 评分、错误率、平均/P95 耗时与 JSON/CSV 导出。CSV 按每条回答展开，保留实验类型、策略、模型变体、成功/失败、耗时、分项指标、人工评分和错误信息；自动指标是辅助量，不代表事实正确性或 LLM 盲评。没有参考输入或执行失败时，对应评分留空，失败行不参与成功答案指标。

Wave 4 将问答生成执行拆为显式策略选择器：普通聊天策略负责 DIRECT、TRIPLES 和消融提示词，grounded 策略只负责实验 RANKED；交互式 RANKED 在未启用专用模型时继续保持 Python 原始 Prompt 兼容行为。模型空答案、图谱客户端超时、未知学习事件和消息重试均返回结构化失败或进入重试/DLQ，不伪造有效结果。管理台的停用、题库发布和微课发布需要二次确认，导入文件限制为不超过 2MB 的 JSON。

## 验证

    $env:JAVA_HOME = 'D:/develop/Java/temurin-21/jdk-21.0.13+9'
    $env:PATH = "$env:JAVA_HOME/bin;$env:PATH"
    mvn -f backend/pom.xml test
    Push-Location frontend
    npm test
    npm run build
    Pop-Location

证据与限制见 docs/test-report.md，区分内存数据库/本地 HTTP 契约测试、本机服务联调与真实 Python/NebulaGraph/模型回答验证。

## 毕业设计材料

设计与计划在 docs/superpowers/，接口摘要在 docs/api.md，开发记录在 docs/development/continuation-ledger.md。论文应说明控制变量、评测集来源、人工评分规则与实验限制；真实实验完成前不填写虚构效果或性能结论。

## 终版验收收口（2026-11-24）

- 合规守卫测试已全面加入 AI 服务、learning-service 和前端：覆盖 RANKED 固定系统提示词不泄露、图谱/模型依赖故障诚实降级、学生提交前答案字段隔离和开题报告学术措辞边界（严禁机器学习知识追踪、采集生理信号或预设降低认知负荷表述）。
- 前端生产构建已完成代码分割与供应商分包：最终构建的 `graph-three` 为 565.99 kB（gzip 139.14 kB），`graph-g6` 为 1,366.15 kB（gzip 392.77 kB），0 error、0 chunk warning。四份全局 CSS 精确大小：`styles-design-system.css` 28,734 B、`styles.css` 15,535 B、`styles-learning.css` 11,389 B、`styles-graph.css` 3,297 B，合计 58,955 B，全局严格 0 个 `!important`。
- 终版精测试验矩阵：
  - 后端三模块全量回归：**282/282 项测试全部通过**（0 失败，0 错误），其中 `learning-service` 175/175、`ai-service` 102/102、`learning-gateway` 5/5；
  - 前端全量测试：**56 个测试文件、189 项测试全部通过**（0 失败）；
  - AST 模板类名扫描：49 个 SFC、1155 处模板类名引用，**0 missing classes**（100% 匹配）；
  - 真实运行态视觉巡检（Playwright UI Smoke）：双视口（1440×900 与 1280×800）、三角色（STUDENT, ADMIN, GUEST），覆盖 29 个路由与进阶场景，**170/170 场景全部通过**，无控制台错误、未捕获异常、横向溢出或组件回归；
  - 容器与工程配置：`docker compose --env-file deploy/.env.server.example -f deploy/compose.server.yml config --quiet` 校验通过；`git diff --check` 通过（0 处空白错误）。
- 答辩点击路径和故障应急命令见 [`docs/答辩现场演示剧本与故障应急清单.md`](docs/答辩现场演示剧本与故障应急清单.md)。
