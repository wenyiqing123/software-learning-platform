#!/usr/bin/env bash
set -Eeuo pipefail
set +x

release=${1:-}
[[ "$release" =~ ^/opt/software-learning-platform/releases/[A-Za-z0-9_.-]+$ ]] || {
    printf '%s\n' 'ERROR: Invalid versioned release path.' >&2
    exit 2
}
root=/opt/software-learning-platform
tag=${release##*/}
cd "$release"

sha256sum -c "$tag.images.tar.sha256"
sha256sum -c "$tag.graph.tar.sha256"
tar -xf "$tag.graph.tar" -C graph
chmod -R a-w graph/Mecha-QA-main/nebula graph/output/air_ticket-finetuned-model-riddle_new/model
install -m 0750 provision-data.sh "$root/provision-data.sh"
install -m 0644 compose.server.yml "$root/compose.server.yml"
docker load -i "$tag.images.tar" >/dev/null
bash "$root/provision-data.sh"
docker compose --env-file "$root/.env.server" -f "$root/compose.server.yml" config --quiet

compose() {
    docker compose --env-file "$root/.env.server" -f "$root/compose.server.yml" "$@"
}

wait_healthy() {
    local service=$1
    local deadline=$(( $(date +%s) + 210 ))
    while (( $(date +%s) < deadline )); do
        local container_id status
        container_id=$(compose ps -q "$service")
        status=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_id" 2>/dev/null || true)
        if [[ "$status" == healthy ]]; then return 0; fi
        if [[ "$status" == unhealthy || "$status" == exited ]]; then
            printf 'Container for %s did not become healthy.\n' "$service" >&2
            return 1
        fi
        sleep 3
    done
    printf 'Timed out waiting for %s health.\n' "$service" >&2
    return 1
}

compose up -d python-bridge
wait_healthy python-bridge
compose up -d learning-service
wait_healthy learning-service
compose up -d ai-service
wait_healthy ai-service
compose up -d learning-gateway
wait_healthy learning-gateway
compose up -d frontend
wait_healthy frontend

curl -fsS http://127.0.0.1:18083/login -o /dev/null
curl -fsS http://127.0.0.1:18084/actuator/health -o /dev/null
docker exec "$(compose ps -q python-bridge)" python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8999/health', timeout=3)" >/dev/null

nginx_conf=/etc/nginx/sites-available/cloudchat.conf
backup="${nginx_conf}.software-learning.$(date -u +%Y%m%dT%H%M%SZ)"
cp -a "$nginx_conf" "$backup"
nginx_installed=false
rollback_nginx() {
    if [[ "$nginx_installed" != true ]]; then
        cp -a "$backup" "$nginx_conf"
        nginx -t >/dev/null
        nginx -s reload || true
        sleep 2
    fi
}
trap rollback_nginx ERR
install -m 0644 "$release/nginx-software-learning.conf" "$nginx_conf"
nginx -t
nginx -s reload
nginx_routed=false
for _ in $(seq 1 10); do
    if curl -fsS http://127.0.0.1/ -o /dev/null >/dev/null 2>&1; then
        nginx_routed=true
        break
    fi
    sleep 1
done
[[ "$nginx_routed" == true ]] || { printf '%s\n' 'Nginx did not route to the new frontend.' >&2; exit 1; }
nginx_installed=true
trap - ERR

printf 'release=%s\nnginx_backup=%s\n' "$release" "$backup"
compose ps --format 'table {{.Name}}\t{{.Status}}'
free -h
docker stats --no-stream --format 'table {{.Name}}\t{{.MemUsage}}\t{{.CPUPerc}}'
