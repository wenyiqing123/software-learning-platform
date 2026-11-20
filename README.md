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

当前专用数据库已应用至 V10。已经执行的迁移保持原样；未来表结构变更使用 V11+。不要修改已执行的 SQL 或清空 Flyway 历史。

## 账户与管理端

学生在登录页注册；答题、错题、推荐、会话和实验均按当前用户隔离。

本机管理员用户名见 .env.local 中 SLP_BOOTSTRAP_ADMIN_USERNAME，随机密码见 SLP_BOOTSTRAP_ADMIN_PASSWORD。仅 local/docker profile 且显式配置时创建新管理员，不提升或重置已有账户。

/admin 支持课程新增/编辑/启停、题目新增/编辑/启停和同课程知识点映射，校验单选/多选/判断题的选项与标准答案。学生列表不返回标准答案，提交后才返回正确答案与解析。

/courses 提供软件工程正文、目标与案例；阅读完成状态按用户保存。图谱页按课程概念查询关系、查看三元组并跳转助教提问。

知识图谱页还提供“先修学习路径”视图：选择目标知识点后，平台按课程内先修关系递归整理学习顺序，展示当前用户的系统掌握度、先修未完成状态，并链接到对应课程阅读、专项练习和图谱实体。管理员页面增加只读的先修关系质量检查，用于发现坏 JSON、无效/重复/跨课程引用、自引用和循环依赖；系统不会自动修改课程关系。

## AI 与现有 Python 依赖

通过 OPENAI_BASE_URL、OPENAI_API_KEY、OPENAI_MODEL 可接入兼容 OpenAI Chat Completions 协议的服务。当前本机已配置 DeepSeek `deepseek-flash`，AI 直答、三种模式和消融模式都已实测成功；密钥只保存在被 Git 忽略的 `.env.local`。地址和错误契约见 `tests/docs/ai-contract.md`。更换模型配置后重启服务。

未配置模型密钥时会明确提示，不会伪造答案；非 AI 学习模块仍可使用。

PYTHON_KNOWLEDGE_URL 指向既有 Python Bridge。本项目脚本不启动或修改 Mecha-QA；Python Bridge 与 NebulaGraph 需要按原项目方式运行。离线图谱返回 503，不使用户退出登录，也不以示例图代替真实结果。

三种问答：

- DIRECT：直接调用大模型，不访问 Python。
- TRIPLES：使用共享检索三元组，不向提示词注入相关性分数。
- RANKED：普通助教交互原样使用 Python 返回的排序评分提示词。问答实验比较使用 `ranked-grounded-v3`：先从真实候选三元组中选择直接证据，再要求答案明确覆盖每条证据的主体、关系和宾语，并按关系语义使用自然汉语连接；“规则、类型、方式、形式”等标签关系直接连接宾语，不添加“是/为”，“实质、定义、含义”等名词性判断关系使用“是/为”，不机械用冒号替代连接词。校验缺项时失败，不计入答案指标或人工评分。Python 原始提示词仍作为 user 消息原样发送，服务端系统提示词不返回前端。`provenance` 与证据轨迹记录版本、候选、选择、原始模型输出、耗时和 token。

实验由后端执行并保存实际 AI 返回值，使用相同问题、模型与生成参数以及共享证据。消融实验按接口实际能力开放；不会将未知原始顺序伪装成无排序结果。

支持参考答案、期望实体、字符级 ROUGE-L、BLEU（最高四阶、有效阶数）、实体召回率、人工 1–5 评分、错误率、平均/P95 耗时与 JSON 导出。自动指标是辅助量，不代表事实正确性或 LLM 盲评。没有参考输入或执行失败时，对应评分留空。

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
