# Docker 服务器部署设计

**状态：已由用户确认设计方向；待用户审阅本文后进入实施。**

## 目标与范围

将现有“知序”软件工程课程学习平台部署到 `120.26.186.201`，通过 Docker 运行前端、三个 Java 服务和 Mecha-QA Python Knowledge Bridge。服务器端使用独立的 MySQL 数据库、RabbitMQ vhost 和项目 Redis 键前缀；NebulaGraph 只读使用服务器现存的 `air_ticket` 图空间。部署保留已有数据库、图数据卷、Mecha-QA 原始代码和现有服务数据。

部署入口使用服务器现有 Nginx 的 HTTP 80 端口，临时以 `http://120.26.186.201/` 提供演示访问。此配置没有 TLS，只适合毕业设计演示和受控测试，不应用于输入真实或重复使用的密码。以后配置域名后，应再启用 HTTPS。

不在本次范围内：清空或重建 agent-chat MySQL/Nebula 数据；修改、导入或写入原始 Nebula 图空间；修改 `Mecha-QA-main` Python 源码；升级服务器操作系统或现有 MySQL/Nebula 镜像。

## 服务器现状

- Ubuntu Server 24.04.5 LTS，x86-64，2 vCPU，约 1.7 GiB RAM，40 GiB 根盘；Docker Engine 29.1.3 与 Compose 2.40.3 已安装。
- 服务器上的 agent-chat 前端、后端和知识服务已按用户确认停止；其 MySQL 与 NebulaGraph Meta/Storage/Graph 容器继续运行，数据卷保留。
- MySQL 8.0.46 当前包含 `agent_chat` 数据库，没有 `software_learning` 数据库。部署只新增独立 schema 和最小权限账号。
- 现有 NebulaGraph 集群位于 Docker 网络 `agent-chat-stack-net`。服务器的 `air_ticket` 图空间实际 Nebula ID 为 2196，并已只读验证包含“需求分析”“系统设计”“软件需求规格说明书”等课程关系。
- Mecha-QA 原代码的逻辑 `spaceId=1` 通过 `space_dict` 映射到空间名 `air_ticket`；它不是 Nebula 控制台显示的内部数值 ID 2196。因此部署继续传 `spaceId=1`，不创建新图空间、不复制图数据、不改 Bridge 原代码。
- RabbitMQ 与 Redis 的既有 Docker 容器处于停止状态，位于 `cloudchat-data` 网络；部署时复用现有容器，配置独立 RabbitMQ vhost/用户和 Redis `slp:` 命名空间，不复用 cloudchat 的应用数据。
- Nginx 当前监听公网 80 端口，配置文件 `/etc/nginx/sites-available/cloudchat.conf` 默认指向已停止的旧应用。部署前备份该配置，验证新配置并通过 `nginx -t` 后再 reload。

## 容器与网络

新的 Compose 项目只管理本项目应用容器：

| 服务 | 用途 | 网络与公开端口 |
| --- | --- | --- |
| `frontend` | Vue 生产构建，由 Nginx 静态托管并提供 SPA fallback | 仅绑定宿主机 `127.0.0.1:18083` |
| `learning-gateway` | Spring Cloud Gateway | 仅绑定宿主机 `127.0.0.1:18084` |
| `learning-service` | 认证、课程、题库、错题、推荐和学习记录 | Compose 内网；连接 MySQL、Redis、RabbitMQ |
| `ai-service` | AI 助教、问答对照和实验评价 | Compose 内网；连接 Python Bridge 与 DeepSeek |
| `python-bridge` | Mecha-QA 知识图谱只读检索 | Compose 内网，并加入 `agent-chat-stack-net` 访问 NebulaGraph |

应用服务加入独立的 `software-learning` 网络。`learning-service` 另外加入 `agent-chat-stack-net` 和 `cloudchat-data`；`python-bridge` 另外加入 `agent-chat-stack-net`。RabbitMQ 和 Redis 使用现存 `cloudchat-data` 网络，MySQL 和 NebulaGraph 使用现存 `agent-chat-stack-net`。数据库、图数据库、Redis、RabbitMQ、Python Bridge 不绑定公网端口。
Python Bridge 进程在容器内监听 `0.0.0.0:8999`，但该端口不发布到宿主机；其他容器通过 Compose 内网访问。

宿主机 Nginx 将 `/api/` 转发到 `127.0.0.1:18084`，其余路径转发到 `127.0.0.1:18083`。Gateway 内部路由目标改为 Compose 服务名 `http://learning-service:8081` 和 `http://ai-service:8082`；学习服务和 AI 服务的 Python URL 改为 `http://python-bridge:8999`。只有宿主机 Nginx 对公网提供 HTTP 80。

## 数据与配置

- MySQL：通过现有 `agent-chat-mysql` 创建 `software_learning` schema 与独立应用账号；账号只获得该 schema 所需权限。Flyway 在新 schema 中执行项目 V1-V5 migration。不得对 `agent_chat` 执行更新或删除。
- RabbitMQ：启动既有 RabbitMQ 容器；创建 `/software-learning` vhost 和独立应用用户。只在此 vhost 授予 `configure/read/write` 权限；保留现有 vhost、用户与数据。
- Redis：启动既有 Redis 7 容器；新增随机、项目专属的 ACL 用户（`slp_redis_` 前缀），授权 `~slp:*` 键范围和应用缓存所需的 `GET`、`SET`、`DEL`、`PING` 命令，并允许 Spring Data Redis 健康探测/连接协商使用 `INFO`、`HELLO`。服务器实际 Redis 7.0.15 不支持 `CLIENT SETINFO` ACL 子命令，因此不授予该权限。后端通过 `spring.data.redis.username` 与环境变量 `REDIS_USERNAME` 连接。ACL 不改变现有用户或全局密码，不清空 Redis。
- MySQL 与 RabbitMQ 使用随机、项目专属用户名（分别以 `slp_mysql_`、`slp_rabbit_` 开头）；MySQL 权限仅限 `software_learning.*`，RabbitMQ 权限仅限 `/software-learning` vhost。
- NebulaGraph：只读连接现有 `air_ticket` 图空间，使用 Bridge 当前逻辑 ID 1。部署探测仅执行查询；不新增、删除、更新顶点、边、索引或图空间。
- Python Bridge：将 `Mecha-QA-main/nebula` 中未修改的原始模块及 `air_ticket-finetuned-model-riddle_new/model` 复制到服务器专用只读挂载目录。Bridge 容器以只读挂载使用它们，不复制或改写原 Python 项目的其他文件。
- 凭据：在服务器 `/opt/software-learning-platform/.env.server` 保存服务器专属 JWT、MySQL/RabbitMQ/Redis 应用凭据、DeepSeek API 配置及管理员引导凭据，文件权限 `0600`。不上传本机 `.env.local`，不把密钥写进镜像、Compose 文件、Nginx 配置或 Git。
- 管理员：后端 `LocalAdminBootstrap` 明确支持 `local` 和 `docker` profile；服务器使用 `SPRING_PROFILES_ACTIVE=docker`。启动时只创建新管理员，不提升、覆盖或重置已存在账号，并补充 `docker` profile 自动化测试。

## 构建与上线流程

1. 在本机确认 Docker Desktop 使用 Linux 容器并可构建 `linux/amd64` 镜像；执行 Maven 测试、Java 打包、前端测试和生产构建。
2. 为三项 Java 服务、前端静态文件和 Python Bridge 构建锁定版本的 Docker 镜像；使用 JDK 21 运行时和 Python 3.11 Bridge 环境。
3. 由于服务器仅有 2 vCPU 和约 1.7 GiB RAM，镜像在本机构建并通过 `docker save`/SSH 传输；不在服务器上进行 Maven、npm 或大型 PyTorch 依赖构建。
4. 在服务器创建仅包含部署文件、只读 Mecha-QA 模块、评分模型和 Docker images 的 `/opt/software-learning-platform` 部署目录；不修改 Mecha-QA 源目录。
5. 启动既有 Redis/RabbitMQ 容器，创建项目专属隔离配置；在现有 MySQL 中创建独立数据库和账号。为每项操作记录结果，不输出凭据。
6. 将现有 Nginx 配置备份到带日期的服务器备份文件，写入项目路由，运行 `nginx -t`；仅验证通过后才 reload。
7. 启动 Compose 中的 Bridge、学习服务、AI 服务、Gateway 和前端；按健康检查确认服务，执行注册/登录、课程、题库和错题流程，并验证只读图检索与 DeepSeek 问答。
8. 检查 `docker stats`、系统内存和 swap；如果服务发生 OOM 或持续占用 swap，先停止新应用容器并回滚 Nginx 路由，不影响现有 MySQL/Nebula 数据卷。

## 资源预算与监测

服务器可用内存较少。Docker 配置设置受控 JVM heap 与各服务内存上限，Python Bridge 使用 CPU 推理，避免在构建或启动时并发执行重型任务。实际限额须经本机镜像运行、服务器 `docker stats` 和完整功能验收校准；不以放宽限制导致宿主机 OOM 作为默认处理方式。若可用内存持续低于安全余量或频繁使用 swap，应升级服务器资源后再作为长期服务运行。

## 安全、备份与回滚

- 已停止的旧 agent-chat 应用容器保持停止；其 MySQL 与 NebulaGraph 容器/数据卷不停止、不删除、不重建。停止旧应用不等于删除其数据。
- Docker Compose 只停止或重建本项目容器；禁止对现有 external networks 使用 `down` 或删除现有 volume。
- 回滚时停止本项目 Compose 容器，恢复 Nginx 配置备份并 reload；保留新建的 `software_learning` schema、Rabbit vhost、Redis 键空间和模型文件，除非用户另行要求删除。
- 当前入口是公网 IP 上的 HTTP，账号密码及 JWT 不具备 TLS 传输保护。此设置仅用于用户确认的毕业设计演示；长期开放前需提供域名、配置 HTTPS，并限制管理入口及防火墙规则。

## 验收标准

- `docker compose ps` 中本项目容器稳定运行；主机 Nginx 检查通过，公网 IP 的首页和 `/api` 路由可访问。
- Gateway、learning-service、ai-service actuator health 均为 UP；Bridge 健康检查为 UP。
- MySQL 只在 `software_learning` schema 应用 Flyway；不修改 `agent_chat` schema。RabbitMQ 的 `/software-learning` vhost 可用，Redis 应用键使用 `slp:` 前缀。
- 课程阅读、注册/登录、答题、错题复习和推荐通过；Python Bridge 针对 `需求分析` 返回软件工程图谱关系；Bridge 和 NebulaGraph 未执行图写入。
- 已配置的 DeepSeek API 可完成实际问答；缺少模型或 Bridge 时，应用明确显示依赖错误，其他学习模块仍可用。
- 部署结束后记录镜像 tag、Compose 状态、health 结果、资源用量、Nginx 备份路径和回滚命令；日志及报告不包含密钥。

