# Docker 服务器部署实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Run remote actions only after reviewing the target paths and preserving existing service volumes.

**Goal:** Deploy the software-learning platform to `120.26.186.201` with Docker, reusing the server’s existing MySQL, NebulaGraph, RabbitMQ, and Redis without altering their existing application data.

**Architecture:** Build the Vue static site, three Java services, and Python Bridge as Linux/amd64 images on the developer workstation, then transfer the images and read-only Mecha-QA graph module/model to the Ubuntu server. The Compose app joins the existing Docker networks; host Nginx proxies the public HTTP endpoint to the frontend and Gateway. MySQL, RabbitMQ, and Redis receive project-specific schema/vhost/ACL isolation; NebulaGraph remains read-only.

**Tech Stack:** Docker Engine 29.1.3; Compose 2.40.3; Ubuntu 24.04 x86-64; JDK 21; Python 3.11; Node.js 22; Spring Boot 4; Vue 3; MySQL 8.0.46; NebulaGraph 3.8; RabbitMQ 3.12; Redis 7.

**Spec:** `docs/superpowers/specs/2026-11-13-docker-server-deployment-design.md`

## Global Constraints

- The target server is `120.26.186.201`; SSH access uses the already configured key and BatchMode. Never request or print private keys or passwords.
- Preserve `agent_chat` MySQL data, all existing NebulaGraph data/volumes, existing cloudchat vhosts/keys, and existing Nginx configuration backup.
- Create only the `software_learning` MySQL schema/user and `/software-learning` RabbitMQ vhost/user for this application.
- Use the existing Nebula logical mapping `spaceId=1 -> air_ticket`; do not write to or migrate the graph.
- Keep `Mecha-QA-main` source unchanged; mount only its `nebula` module and model read-only in the Bridge container.
- Put secrets only in server `/opt/software-learning-platform/.env.server` with mode `0600`; do not copy the whole local `.env.local` or include secrets in images, logs, shell history, or Git.
- Build images locally to avoid heavy Maven, npm, and PyTorch builds on the 2-vCPU/1.7-GiB server.
- Publish only host-loopback ports `18083` (frontend) and `18084` (Gateway); public access stays through Nginx HTTP port 80. HTTP is for the user-approved demo only; make no TLS or production-security claim.
- Never run `docker compose down -v`, prune commands, or remove existing external networks/volumes.
- The repository currently has no baseline commit and application files are untracked; do not commit application files without a separate request.

---

### Task 1: Make Runtime Configuration Container-Aware

**Files:**
- Modify: `backend/learning-gateway/src/main/resources/application.yml`
- Modify: `backend/learning-service/src/main/resources/application.yml`
- Modify: `backend/learning-service/src/main/java/edu/wfust/learning/platform/admin/LocalAdminBootstrap.java`
- Modify: `backend/learning-gateway/pom.xml` (test dependency for the new route configuration test)
- Test: `backend/learning-gateway/src/test/java/edu/wfust/learning/gateway/GatewayRouteConfigurationTest.java`
- Test: `backend/learning-service/src/test/java/edu/wfust/learning/platform/admin/LocalAdminBootstrapTest.java`
- Test: `backend/learning-service/src/test/java/edu/wfust/learning/platform/config/RedisUsernameConfigurationTest.java`

**Interfaces:**
- Gateway accepts `AI_SERVICE_URL` and `LEARNING_SERVICE_URL`, defaulting to the current loopback URIs for local development.
- Learning Service binds optional `REDIS_USERNAME` through `spring.data.redis.username`; an empty username preserves password-only/local compatibility.
- The administrator bootstrap runs only in explicit `local` or `docker` profiles. Existing-account rules remain unchanged: an enabled administrator is left untouched, and a student is never promoted.

- [x] **Step 1: Add failing Gateway route test**
  Create a Spring context test with `AI_SERVICE_URL=http://ai-service:8082` and `LEARNING_SERVICE_URL=http://learning-service:8081`. Assert the route definitions for `/api/ai/**` and `/api/**` resolve to those exact URIs, and add a second assertion that absent variables retain `127.0.0.1` defaults.
- [x] **Step 2: Run the targeted test and verify RED**
  Run: `mvn -f backend/pom.xml -pl learning-gateway -am -Dtest=GatewayRouteConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test`
  Expected: FAIL because current route URIs hard-code `127.0.0.1` and only parameterize the port.
- [x] **Step 3: Add failing Redis username binding test**
  Load the Learning Service configuration with `REDIS_USERNAME=software_learning` and assert Spring binds it to `spring.data.redis.username` while an unset value remains empty.
- [x] **Step 4: Run the targeted Redis test and verify RED**
  Run: `mvn -f backend/pom.xml -pl learning-service -am -Dtest=RedisUsernameConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test`
  Expected: FAIL because the current `spring.data.redis` configuration has no username property.
- [x] **Step 5: Add failing Docker bootstrap profile test**
  Assert `LocalAdminBootstrap` declares `@Profile({"local", "docker"})`; preserve existing tests for new-account creation, no promotion, no reset, and weak credential rejection.
- [x] **Step 6: Run the targeted profile test and verify RED**
  Run: `mvn -f backend/pom.xml -pl learning-service -am -Dtest=LocalAdminBootstrapTest -Dsurefire.failIfNoSpecifiedTests=false test`
  Expected: FAIL because the current component is restricted to `local` only.
- [x] **Step 7: Implement the three minimal configuration changes**
  Replace Gateway loopback URIs with `${AI_SERVICE_URL:http://127.0.0.1:8082}` and `${LEARNING_SERVICE_URL:http://127.0.0.1:8081}`. Add `username: ${REDIS_USERNAME:}` under `spring.data.redis`. Extend the bootstrap profile to `local` and `docker` without changing account validation or existing-account behavior.
- [x] **Step 8: Run targeted tests, then the complete backend suite**
  Run the three targeted Maven tests above, then `mvn -f backend/pom.xml test`.
  Expected: all targeted tests pass and the existing 98 backend tests remain green.

### Task 2: Add Production Docker Images

**Files:**
- Create: `deploy/docker/java.Dockerfile`
- Create: `deploy/docker/frontend.Dockerfile`
- Create: `deploy/docker/frontend.nginx.conf`
- Create: `deploy/docker/python-bridge.Dockerfile`
- Create: `.dockerignore`
- Create: `scripts/build-server-images.ps1`

**Interfaces:**
- Java image build argument `JAR_FILE` selects one already packaged module JAR; runtime listens on the module’s existing internal port.
- Frontend image serves `frontend/dist` on container port 80 and uses `try_files $uri $uri/ /index.html` for Vue history fallback.
- Bridge image runs `uvicorn app:app --host 0.0.0.0 --port 8999`; only the Compose network can reach that port. Mecha-QA `nebula` source and the scoring model are supplied through read-only host mounts, not copied into the Python source tree.

- [x] **Step 1: Add the slim Java runtime Dockerfile**
  Use a JRE 21 base, copy `${JAR_FILE}` to `/app/app.jar`, set a bounded `JAVA_TOOL_OPTIONS`, and run `java -jar /app/app.jar`. Install only the minimal probe tool needed by the healthcheck.
- [x] **Step 2: Add the frontend multi-stage Dockerfile and SPA Nginx config**
  Use Node 22 to run `npm ci` and `npm run build`, then copy `dist` into an Nginx Alpine runtime. Configure SPA fallback; do not proxy API traffic from the frontend container because the host Nginx owns the `/api` route.
- [x] **Step 3: Add the Bridge Dockerfile**
  Use Python 3.11 slim, install the pinned `python-knowledge-bridge/requirements.txt`, copy only the Bridge package code, set the working directory to `/app`, and launch Uvicorn on the container interface.
- [x] **Step 4: Add an image build script that stages Bridge source temporarily**
  `build-server-images.ps1` checks Docker Engine is running, packages backend JARs, runs frontend tests/build, stages the unmodified Bridge source in a unique `%TEMP%` directory, builds `linux/amd64` images, and removes the temporary stage in `finally`. It must never copy `.env.local` into a build context.
- [x] **Step 5: Build and inspect the images locally**
  Run: `./scripts/build-server-images.ps1`
  Expected: images for `learning-service`, `ai-service`, `learning-gateway`, `frontend`, and `python-bridge` build for `linux/amd64`; `docker image inspect` reports the expected architecture; no secret values appear in build logs.

### Task 3: Define the Server Compose Topology

**Files:**
- Create: `deploy/compose.server.yml`
- Create: `deploy/.env.server.example`
- Modify: `.gitignore`
- Modify: `README.md`

**Interfaces:**
- Compose project name is `software-learning`; external networks are `agent-chat-stack-net` and `cloudchat-data`; the app-only network is `software-learning`.
- `frontend` binds `127.0.0.1:18083:80`; Gateway binds `127.0.0.1:18084:8080`; Java backends and Bridge have no host-published ports.
- Compose environment points learning-service to `agent-chat-mysql:3306`, RabbitMQ to its `cloudchat-data` alias and `/software-learning` vhost, Redis to its `cloudchat-data` alias with a dedicated ACL username/password, AI/Gateway upstreams to service DNS, and Bridge to `agent-chat-nebula-graphd:9669` with logical `NEBULA_SPACE_ID=1`.

- [x] **Step 1: Add Compose service definitions**
  Define the five app images, restart policies, healthchecks, conservative memory/heap limits, log rotation, read-only Mecha-QA/model mounts, and only the required external network attachments. Do not add replacement MySQL or NebulaGraph containers.
- [x] **Step 2: Add secret-free server environment example**
  Document variable names for DB host/schema/app user/password, Rabbit host/vhost/app user/password, Redis host/ACL user/password, JWT secret, Bridge/Nebula settings, DeepSeek base URL/key/model, and one-time administrator credentials. Use blank/example placeholders only; no real values.
- [x] **Step 3: Add `.env.server` and artifact ignores**
  Ignore `deploy/.env.server`, deployment image archives, temporary Bridge staging, `frontend/dist`, and server credential exports. Keep `.env.server.example` tracked.
- [x] **Step 4: Validate the Compose model without connecting to server services**
  Create a temporary env file populated with dummy values, then run `docker compose --env-file <temp-env> -f deploy/compose.server.yml config --quiet`. Remove the temporary env file afterward.
  Expected: Compose syntax and external network declarations validate; the example file has no secrets or blank required substitutions.

### Task 4: Provision Isolated Existing Dependencies

**Files:**
- Create: `deploy/server/provision-data.sh`
- Create: `scripts/create-server-env.ps1`
- Modify: `README.md`

**Interfaces:**
- Provision script is idempotent and only creates a schema/user, a vhost/user, and a Redis ACL user. It never changes `agent_chat`, other RabbitMQ vhosts, other Redis keys/users, or NebulaGraph graph contents.
- Server environment file is created on the server with mode `0600`; credentials are never printed or committed.

- [x] **Step 1: Implement server environment generation**
  Generate a fresh 64-byte JWT secret plus unique MySQL, RabbitMQ, and Redis app passwords using a cryptographic random source. Copy only the approved DeepSeek settings and initial administrator credentials from the ignored local environment without logging values. Write `/opt/software-learning-platform/.env.server` under `umask 077` and set mode `0600`.
- [x] **Step 2: Start only the required existing broker/cache containers**
  Start `cloudchat-rabbitmq-1` and `cloudchat-redis-1`; wait for their running/health state. Do not start the stopped cloudchat frontend/backend/knowledge containers. Do not recreate the containers or remove their volumes.
- [x] **Step 3: Create the dedicated MySQL schema and app account**
  Through `agent-chat-mysql`, create database `software_learning` and a unique app account restricted to `software_learning.*`. Make the script idempotent; never run a SQL statement against `agent_chat` and never print the root/app passwords.
- [x] **Step 4: Create the isolated RabbitMQ vhost and permissions**
  Create `/software-learning`, create the app user from the server env file, and grant only this vhost’s `configure`, `read`, and `write` permissions for the project’s `slp.learning.events` exchange and queues.
- [x] **Step 5: Create the project-scoped Redis ACL user**
  Add a random project-scoped ACL user restricted to `~slp:*` keys. The application cache uses `GET`, `SET`, `DEL`, and `PING`; Spring Data Redis health checks/negotiation also require `INFO` and `HELLO`. Do not alter existing ACL users or requirepass. The server's Redis 7.0.15 rejects `CLIENT SETINFO` as an ACL modifier, so it is not granted. Update the environment file with the user and password before starting the app.
- [x] **Step 6: Verify isolation before application startup**
  Run read-only checks for database names, RabbitMQ vhost/user permissions, Redis ACL key pattern, external Docker network names, and NebulaGraph health. Expected: `software_learning` exists; `agent_chat` remains present; `/software-learning` exists; `air_ticket` remains the only queried graph target and is untouched.

### Task 5: Package, Transfer, and Start the Application

**Files:**
- Create: `scripts/deploy-server.ps1`
- Create: `deploy/server/nginx-software-learning.conf`
- Modify: `README.md`

**Interfaces:**
- Deployment target: root SSH at `120.26.186.201` via existing key and BatchMode.
- Deploy path: `/opt/software-learning-platform`; runtime config: `/opt/software-learning-platform/.env.server`; app endpoint after Nginx switch: `http://120.26.186.201/`.

- [x] **Step 1: Export and checksum local Docker images**
  Save the five `linux/amd64` images to a uniquely named tar archive, compute SHA-256 locally, and confirm the archive contains only app images. Do not include `.env.server`, `.env.local`, MySQL dumps, or Nebula data.
- [x] **Step 2: Transfer deployment assets and the read-only graph dependency**
  Use `scp` to upload the image archive, Compose/server scripts, `Mecha-QA-main/nebula`, and the 391 MiB scoring model into a new versioned release directory. Compare SHA-256 hashes after transfer; set source/model directories read-only.
- [x] **Step 3: Load the images and provision data services**
  On the server, `docker load` the verified archive, run `provision-data.sh` with shell tracing disabled, and confirm all pre-existing MySQL/Nebula containers remain healthy. Do not run `docker compose down` against external projects.
- [x] **Step 4: Start app services in dependency order**
  Bring up Python Bridge and wait for internal health, then `learning-service` and wait for actuator health, then `ai-service`, then Gateway and frontend. Compose `depends_on: condition: service_healthy` is used only among app containers; MySQL/Rabbit/Redis are checked by the provisioning script.
- [x] **Step 5: Prepare and validate the Nginx replacement config**
  Back up `/etc/nginx/sites-available/cloudchat.conf` to a timestamped path; install the new `/api/` and SPA proxy routes; run `nginx -t`; reload only on success. If validation fails, immediately restore the backup and re-run `nginx -t` before reload.
- [x] **Step 6: Verify service routing on the server before public smoke tests**
  From the server, curl `127.0.0.1:18083/login`, Gateway `127.0.0.1:18084/actuator/health`, and Bridge `/health`; only after those pass query `http://120.26.186.201/` externally.

### Task 6: Run End-to-End Acceptance and Record Rollback

**Files:**
- Modify: `README.md`
- Create: `deploy/DEPLOYMENT-RECORD.md` (redacted operational results only)

- [ ] **Step 1: Check health and resource pressure**
  Verify all five app containers and external dependencies, inspect `docker stats`, `free -h`, and swap. Expected: no restart loop or OOM; if swap grows continuously, stop app rollout and request a larger server rather than reducing DB safety or exposing memory exhaustion.
  Deployment health checks are green, but the 1.7-GiB VM has about 1.2 GiB swap in use and non-zero memory PSI; the scoring model peaked near the Bridge container's 640-MiB limit during cold load. Do not declare capacity acceptance complete until the user approves additional RAM and the cold-load test is repeated.
- [x] **Step 2: Run the repeatable application smoke test**
  Run via an SSH tunnel to avoid sending credentials over public HTTP. Authentication, course reading, quiz, wrong-book, recommendation, session isolation, and logout passed 18 checks. Smoke users are uniquely named and are not deleted automatically.
- [x] **Step 3: Verify graph and model integration**
  Through the deployed app, query `需求分析` and verify software-engineering triples are returned from read-only `air_ticket` space; verify a DeepSeek answer and RANKED comparison. Confirm no Nebula write API or graph mutation ran.
- [x] **Step 4: Verify data isolation and secrets handling**
  Confirm Flyway version V5 in `software_learning`, no writes to `agent_chat`, only `/software-learning` RabbitMQ events, Redis keys begin with `slp:`, and `.env.server` is mode `0600` and absent from Git/images/logs.
- [x] **Step 5: Document rollback commands and delivered URL**
  Record image tags, container health, memory usage, Nginx backup path, schema/vhost names, smoke-test summary, and `docker compose stop` plus Nginx restore/reload commands. Never record secret values. Report the public demo URL and the HTTP/TLS limitation.

## Plan Self-Review

- Spec coverage: runtime configuration, external networks, data isolation, read-only graph use, secret handling, local image builds, low-memory limits, HTTP ingress, smoke verification, and rollback all map to Tasks 1-6.
- No database or Nebula data migration is planned because the current server already has the required `air_ticket` graph with software-engineering relations and logical `spaceId=1` maps to it.
- The deployment intentionally changes the host Nginx default route because the user approved stopping the old application and approved the IP-based HTTP demo; the prior config is backed up and rollback-tested before reload.
- No real credentials are included in this plan. The local Docker Desktop daemon was started for image builds; deployment release `20261113-1248` is running on `120.26.186.201`. Credentialed smoke tests used a local SSH tunnel, not the public HTTP endpoint.
- All deployment and integration tasks are complete except Task 6 Step 1 capacity acceptance. The current server is under memory pressure; the application remains deployed and healthy while awaiting the user's decision about increasing VM RAM.
- The repository has no baseline commit and all existing app files are untracked, so implementation steps will not create commits or stage unrelated project files.

