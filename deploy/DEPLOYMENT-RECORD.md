# Deployment Record

Date: 2026-11-14  
Release: `20261114-0205`  
Server: `120.26.186.201` (Ubuntu 24.04, x86-64)  
Demo: `http://120.26.186.201/` (HTTP only)

## Result

The Vue frontend, learning service, AI service, Gateway, and Python Knowledge Bridge are running as Docker containers. All five application containers reported healthy with zero restarts and no OOM kill in the final read-only check. Nginx serves the current application; the configuration before this deployment is backed up at `/etc/nginx/sites-available/cloudchat.conf.software-learning.20261113T183141Z`.

Images are tagged `software-learning/{frontend,learning-service,ai-service,learning-gateway,python-bridge}:20261114-0205` and were built and inspected as `linux/amd64`. The server received the images and a separate graph dependency archive; SHA-256 verification passed before extraction/loading.

## Follow-up Feature Deployment

This release contains the learner-facing prerequisite path, the administrator read-only prerequisite quality report, and the final frontend graph/path mode switching fixes. The deployed learning-path endpoint was checked through an SSH tunnel and returned a two-node path for `软件维护`: `软件生命周期` was `UP_NEXT`, `软件维护` was `BLOCKED`, and `cycleDetected=false`. The administrator report returned eight knowledge points, seven valid prerequisite edges, zero errors, and zero warnings. The new frontend path view, graph instance recreation, navigation links, and administrator report were included in the production image.

## Verification

- Backend Maven suites passed: learning-service 64 tests, ai-service 47 tests, and learning-gateway 2 tests; frontend Vitest suite passed 23 tests and the production frontend build completed.
- Docker Compose configuration, PowerShell deployment scripts, and Bash server scripts passed syntax validation.
- Python scoring model loaded from the read-only model mount and generated a 768-dimensional embedding.
- Credentialed end-to-end smoke test passed 18 checks through a local SSH tunnel: registration, login/refresh, role isolation, course reading/progress, quiz, wrong-book, recommendations, tutor history, and logout.
- The learning-path endpoint returned the expected prerequisite order and mastery statuses. The administrator quality endpoint returned a valid report, and an unauthenticated request returned HTTP 401.
- Knowledge graph endpoint returned 8 nodes and 7 edges for `需求分析` from logical `spaceId=1`, which maps to the existing `air_ticket` space. The Bridge and model mounts are read-only; no graph write was performed.
- DeepSeek `DIRECT`, `TRIPLES`, and `RANKED` comparison completed after the Bridge model was warm. The `RANKED` response included 7 scored triples and confirmed scores were present in the prompt.
- Public read-only checks returned HTTP 200 for the homepage and HTTP 401 for an unauthenticated API request. No password, administrator credential, or bearer token was sent over public HTTP.
- `/opt/software-learning-platform/.env.server` has mode `0600`; a scan of recent application logs found no match for configured secret values.
- Existing `agent_chat` and `software_learning` databases are present; Flyway reached V5 in `software_learning`. RabbitMQ uses only `/software-learning`; the application Redis ACL is restricted to `~slp:*`. Existing MySQL and NebulaGraph containers remained healthy.

## Capacity Warning

The VM has about 1.7 GiB of RAM. The final read-only sample showed 1.1 GiB used, 109 MiB free, 566 MiB available, and 535 MiB of the 2-GiB swap file in use. Current application memory samples were approximately: frontend 5 MiB / 48 MiB, Gateway 158 MiB / 224 MiB, AI service 180 MiB / 256 MiB, learning service 271 MiB / 320 MiB, and Python Bridge 11 MiB / 640 MiB. There were no OOM kills or container restarts, but this is not adequate headroom for sustained or concurrent workloads. Increasing the VM to at least 4 GiB RAM is recommended before further load testing or long-term use. The VM size and swap configuration were not changed during this deployment.

## Data Boundaries

The application reuses the server's existing MySQL, RabbitMQ, Redis, and NebulaGraph containers. It creates a separate `software_learning` schema, random project-specific service accounts, `/software-learning` vhost, and `slp:` Redis key namespace. It does not alter `agent_chat`, replace existing data volumes, write to NebulaGraph, or modify Mecha-QA source files.

## Rollback

Rollback preserves databases, graph data, service containers, and volumes. From an SSH session on the server:

```bash
cd /opt/software-learning-platform
docker compose --env-file .env.server -f compose.server.yml stop
cp -a /etc/nginx/sites-available/cloudchat.conf.software-learning.20261113T183141Z \
  /etc/nginx/sites-available/cloudchat.conf
nginx -t && nginx -s reload
```

Do not run `docker compose down -v`, Docker prune, or volume deletion as part of rollback.

## Access Limitation

The public endpoint uses plain HTTP and is for controlled demonstration only. Do not submit real or reused passwords or tokens through it. Use the SSH tunnel procedure in the repository `README.md` for credentialed smoke tests. HTTPS remains a prerequisite for normal public use.
