#!/usr/bin/env bash
set -Eeuo pipefail
set +x
umask 077

readonly env_file=/opt/software-learning-platform/.env.server
readonly mysql_container=agent-chat-mysql
readonly rabbit_container=cloudchat-rabbitmq-1
readonly redis_container=cloudchat-redis-1

fail() { printf '%s\n' "ERROR: $1" >&2; exit 1; }
[[ -r "$env_file" ]] || fail 'The protected server environment file is missing.'
source "$env_file"

[[ "${MYSQL_DATABASE:-}" == software_learning ]] || fail 'Refusing to provision a database other than software_learning.'
[[ "${MYSQL_USERNAME:-}" =~ ^slp_mysql_[a-f0-9]{12}$ ]] || fail 'Unexpected application MySQL username.'
[[ "${RABBITMQ_VHOST:-}" == /software-learning ]] || fail 'Refusing to provision an unexpected RabbitMQ vhost.'
[[ "${RABBITMQ_USERNAME:-}" =~ ^slp_rabbit_[a-f0-9]{12}$ ]] || fail 'Unexpected application RabbitMQ username.'
[[ "${REDIS_USERNAME:-}" =~ ^slp_redis_[a-f0-9]{12}$ ]] || fail 'Unexpected application Redis username.'
[[ "${MYSQL_PASSWORD:-}" =~ ^[a-f0-9]{64}$ ]] || fail 'The generated MySQL application password is invalid.'
[[ "${RABBITMQ_PASSWORD:-}" =~ ^[a-f0-9]{64}$ ]] || fail 'The generated RabbitMQ application password is invalid.'
[[ "${REDIS_PASSWORD:-}" =~ ^[a-f0-9]{64}$ ]] || fail 'The generated Redis application password is invalid.'

for container in "$mysql_container" agent-chat-nebula-graphd agent-chat-nebula-metad agent-chat-nebula-storaged; do
    docker inspect "$container" >/dev/null 2>&1 || fail "Required existing container is missing: $container"
done

docker start "$rabbit_container" "$redis_container" >/dev/null

redis_admin_password=$(docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$redis_container" \
    | sed -n 's/^REDIS_PASSWORD=//p' | head -n 1)
if [[ -n "$redis_admin_password" ]]; then
    redis_cli() { docker exec -e REDISCLI_AUTH="$redis_admin_password" "$redis_container" redis-cli "$@"; }
else
    redis_cli() { docker exec "$redis_container" redis-cli "$@"; }
fi

ready=false
for _ in $(seq 1 60); do
    if docker exec "$rabbit_container" rabbitmq-diagnostics -q ping >/dev/null 2>&1 &&
       redis_cli PING 2>/dev/null | grep -qx PONG; then
        ready=true
        break
    fi
    sleep 2
done
[[ "$ready" == true ]] || fail 'The existing RabbitMQ or Redis container did not become ready.'

mysql_health=$(docker inspect --format '{{.State.Health.Status}}' "$mysql_container")
[[ "$mysql_health" == healthy ]] || fail 'The existing MySQL container is not healthy.'

mysql_root_password=$(docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$mysql_container" \
    | sed -n 's/^MYSQL_ROOT_PASSWORD=//p' | head -n 1)
[[ -n "$mysql_root_password" ]] || fail 'Could not locate the existing MySQL root credential without displaying it.'

existing_mysql_user=$( {
    printf '%s\n' "$mysql_root_password"
    printf "SELECT COUNT(*) FROM mysql.user WHERE user='%s' AND host='%%';\n" "$MYSQL_USERNAME"
} | docker exec -i "$mysql_container" sh -c 'IFS= read -r MYSQL_PWD; export MYSQL_PWD; exec mysql --protocol=socket --user=root --batch --skip-column-names')
if [[ "$existing_mysql_user" == 1 ]]; then
    existing_grants=$( {
        printf '%s\n' "$mysql_root_password"
        printf "SHOW GRANTS FOR '%s'@'%%';\n" "$MYSQL_USERNAME"
    } | docker exec -i "$mysql_container" sh -c 'IFS= read -r MYSQL_PWD; export MYSQL_PWD; exec mysql --protocol=socket --user=root --batch --skip-column-names')
    while IFS= read -r grant; do
        [[ "$grant" == *'GRANT USAGE ON *.*'* || "$grant" == *' ON `software_learning`.* TO '* ]] \
            || fail 'The existing application MySQL username has grants outside software_learning; refusing to change it.'
    done <<< "$existing_grants"
elif [[ "$existing_mysql_user" != 0 ]]; then
    fail 'Could not verify the application MySQL username without altering existing users.'
fi

{
    printf '%s\n' "$mysql_root_password"
    cat <<SQL
CREATE DATABASE IF NOT EXISTS software_learning CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS '${MYSQL_USERNAME}'@'%' IDENTIFIED BY '${MYSQL_PASSWORD}';
ALTER USER '${MYSQL_USERNAME}'@'%' IDENTIFIED BY '${MYSQL_PASSWORD}';
GRANT ALL PRIVILEGES ON software_learning.* TO '${MYSQL_USERNAME}'@'%';
SQL
} | docker exec -i "$mysql_container" sh -c 'IFS= read -r MYSQL_PWD; export MYSQL_PWD; exec mysql --protocol=socket --user=root --batch --skip-column-names' >/dev/null

if ! docker exec "$rabbit_container" rabbitmqctl -q list_vhosts name | grep -Fxq "$RABBITMQ_VHOST"; then
    docker exec "$rabbit_container" rabbitmqctl add_vhost "$RABBITMQ_VHOST" >/dev/null
fi
if docker exec "$rabbit_container" rabbitmqctl -q list_users | awk '{print $1}' | grep -Fxq "$RABBITMQ_USERNAME"; then
    existing_vhosts=$(docker exec "$rabbit_container" rabbitmqctl -q list_user_permissions "$RABBITMQ_USERNAME" \
        | awk '$1 != "vhost" && $1 != "Listing" && NF {print $1}')
    [[ "$existing_vhosts" == "$RABBITMQ_VHOST" ]] \
        || fail 'The RabbitMQ username already belongs to another vhost; refusing to change it.'
else
    docker exec "$rabbit_container" rabbitmqctl add_user "$RABBITMQ_USERNAME" "$RABBITMQ_PASSWORD" >/dev/null
fi
docker exec "$rabbit_container" rabbitmqctl set_permissions -p "$RABBITMQ_VHOST" \
    "$RABBITMQ_USERNAME" \
    '^slp\.learning\.events(\.dlx)?$|^slp\.learning\.events\.(quiz|dead)$' \
    '^slp\.learning\.events$|^slp\.learning\.events\.dlx$' \
    '^slp\.learning\.events\.(quiz|dead)$' >/dev/null

existing_redis_user=$(redis_cli --raw ACL GETUSER "$REDIS_USERNAME" 2>/dev/null || true)
if [[ -n "$existing_redis_user" ]] && ! printf '%s\n' "$existing_redis_user" | grep -Fq '~slp:*'; then
    fail 'The Redis username already exists without the slp:* key restriction; refusing to change it.'
fi
redis_args=(ACL SETUSER "$REDIS_USERNAME" reset on ">${REDIS_PASSWORD}" '~slp:*' \
    +get +set +del +ping +hello +info)
redis_result=$(redis_cli "${redis_args[@]}" 2>&1) || fail 'Could not configure the dedicated Redis ACL user.'
[[ "$redis_result" == OK ]] || fail 'Redis did not confirm the dedicated ACL configuration.'

mysql_schema=$(printf '%s\n' 'SHOW DATABASES LIKE '\''software_learning'\'';' \
    | { printf '%s\n' "$mysql_root_password"; cat; } \
    | docker exec -i "$mysql_container" sh -c 'IFS= read -r MYSQL_PWD; export MYSQL_PWD; exec mysql --protocol=socket --user=root --batch --skip-column-names')
printf '%s\n' "$mysql_schema" | grep -Fxq software_learning || fail 'The isolated MySQL schema verification failed.'
docker exec "$rabbit_container" rabbitmqctl -q list_vhosts name | grep -Fxq "$RABBITMQ_VHOST" || fail 'RabbitMQ vhost verification failed.'
docker exec "$rabbit_container" rabbitmqctl list_user_permissions "$RABBITMQ_USERNAME" \
    | awk -v expected="$RABBITMQ_VHOST" '$1 == expected { found = 1 } END { exit !found }' \
    || fail 'RabbitMQ user permission verification failed.'
redis_acl=$(redis_cli --raw ACL GETUSER "$REDIS_USERNAME" 2>/dev/null) || fail 'Redis ACL verification failed.'
printf '%s\n' "$redis_acl" | grep -Fq '~slp:*' || fail 'Redis ACL key isolation verification failed.'
for container in agent-chat-nebula-graphd agent-chat-nebula-metad agent-chat-nebula-storaged; do
    docker inspect "$container" >/dev/null
done

printf '%s\n' 'Provisioning complete: software_learning schema, /software-learning vhost, and slp:-scoped Redis ACL are ready.'
