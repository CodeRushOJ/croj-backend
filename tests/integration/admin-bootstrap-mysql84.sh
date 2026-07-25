#!/usr/bin/env bash
set -Eeuo pipefail

readonly backend_image="${1:?usage: admin-bootstrap-mysql84.sh <backend-image>}"
readonly mysql_image="${MYSQL84_IMAGE:-mysql:8.4.10}"
readonly primary_database="code_rush_oj_bootstrap"
readonly concurrent_database="code_rush_oj_concurrent"
readonly database_user="coderushoj_bootstrap"
readonly database_password="database-test-secret-mysql84"
readonly mysql_root_password="root-test-secret-mysql84"
readonly first_password="first-admin-test-secret-mysql84"
readonly replay_password="replay-admin-test-secret-mysql84"
readonly conflict_password="conflict-admin-test-secret-mysql84"
readonly concurrent_password_a="concurrent-a-test-secret-mysql84"
readonly concurrent_password_b="concurrent-b-test-secret-mysql84"
readonly descriptor_password="descriptor-url-test-secret-mysql84"
readonly legacy_password="legacy-admin-test-secret-mysql84"

temp_dir="$(mktemp -d /tmp/croj-admin-bootstrap.XXXXXX)"
case "$temp_dir" in
    /tmp/croj-admin-bootstrap.*) ;;
    *)
        printf 'refusing unexpected temporary directory: %s\n' "$temp_dir" >&2
        exit 1
        ;;
esac
readonly temp_dir
readonly suffix="${temp_dir##*.}"
readonly network_name="croj-admin-bootstrap-${suffix}"
readonly mysql_container="croj-admin-bootstrap-mysql-${suffix}"

cleanup() {
    docker rm -f "$mysql_container" >/dev/null 2>&1 || true
    docker network rm "$network_name" >/dev/null 2>&1 || true
    rm -rf -- "$temp_dir"
}
trap cleanup EXIT

fail() {
    printf 'admin bootstrap MySQL 8.4 gate failed: %s\n' "$1" >&2
    exit 1
}

assert_equal() {
    local expected="$1"
    local actual="$2"
    local description="$3"
    if [[ "$actual" != "$expected" ]]; then
        fail "$description (expected $expected, got $actual)"
    fi
}

assert_log_contains() {
    local log_file="$1"
    local expected="$2"
    local description="$3"
    grep -Fq -- "$expected" "$log_file" || fail "$description"
}

assert_log_redacted() {
    local log_file="$1"
    local secret
    for secret in \
        "$database_password" \
        "$mysql_root_password" \
        "$first_password" \
        "$replay_password" \
        "$conflict_password" \
        "$concurrent_password_a" \
        "$concurrent_password_b" \
        "$descriptor_password" \
        "$legacy_password"; do
        if grep -Fq -- "$secret" "$log_file"; then
            fail "a sensitive value appeared in $(basename "$log_file")"
        fi
    done
}

write_environment() {
    local destination="$1"
    local database="$2"
    local username="$3"
    local email="$4"
    local admin_password="$5"
    local database_url="${6:-jdbc:mysql://mysql:3306/${database}?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&forceConnectionTimeZoneToSession=true&useSSL=false&allowPublicKeyRetrieval=true}"

    umask 077
    {
        printf 'CROJ_MODE=bootstrap-admin\n'
        printf 'DATABASE_URL=%s\n' "$database_url"
        printf 'DATABASE_USERNAME=%s\n' "$database_user"
        printf 'DATABASE_PASSWORD=%s\n' "$database_password"
        printf 'BOOTSTRAP_ADMIN_USERNAME=%s\n' "$username"
        printf 'BOOTSTRAP_ADMIN_EMAIL=%s\n' "$email"
        printf 'BOOTSTRAP_ADMIN_PASSWORD=%s\n' "$admin_password"
    } >"$destination"
}

mysql_query() {
    local database="$1"
    local statement="$2"
    docker exec \
        --env "MYSQL_PWD=$database_password" \
        "$mysql_container" \
        mysql --batch --skip-column-names --user "$database_user" "$database" --execute "$statement"
}

run_bootstrap() {
    local environment_file="$1"
    local log_file="$2"
    docker run --rm \
        --network "$network_name" \
        --env-file "$environment_file" \
        "$backend_image" >"$log_file" 2>&1
}

docker network create "$network_name" >/dev/null
docker run --detach --rm \
    --name "$mysql_container" \
    --network "$network_name" \
    --network-alias mysql \
    --env "MYSQL_ROOT_PASSWORD=$mysql_root_password" \
    --env "MYSQL_DATABASE=$primary_database" \
    --env "MYSQL_USER=$database_user" \
    --env "MYSQL_PASSWORD=$database_password" \
    "$mysql_image" \
    --character-set-server=utf8mb4 \
    --collation-server=utf8mb4_0900_ai_ci \
    --default-time-zone=+08:00 >/dev/null

mysql_ready=false
for _ in {1..90}; do
    if docker exec \
        --env "MYSQL_PWD=$mysql_root_password" \
        "$mysql_container" \
        mysql --protocol TCP --host 127.0.0.1 \
        --batch --skip-column-names --user root --execute 'SELECT 1' >/dev/null 2>&1; then
        mysql_ready=true
        break
    fi
    sleep 1
done
[[ "$mysql_ready" == true ]] || fail "MySQL did not become ready"

mysql_version="$(docker exec \
    --env "MYSQL_PWD=$mysql_root_password" \
    "$mysql_container" \
    mysql --protocol TCP --host 127.0.0.1 \
    --batch --skip-column-names --user root --execute 'SELECT VERSION()')"
[[ "$mysql_version" == 8.4.* ]] || fail "expected MySQL 8.4, got $mysql_version"
assert_equal "+08:00" "$(mysql_query "$primary_database" "SELECT @@session.time_zone")" \
    "MySQL test precondition did not use a non-UTC session"

docker exec \
    --env "MYSQL_PWD=$mysql_root_password" \
    "$mysql_container" \
    mysql --user root --execute \
    "CREATE DATABASE ${concurrent_database} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
     GRANT ALL PRIVILEGES ON ${concurrent_database}.* TO '${database_user}'@'%';" >/dev/null

write_environment \
    "$temp_dir/first.env" \
    "$primary_database" \
    admin \
    admin@coderushoj.test \
    "$first_password"
if ! run_bootstrap "$temp_dir/first.env" "$temp_dir/first.log"; then
    assert_log_redacted "$temp_dir/first.log"
    tail -n 80 "$temp_dir/first.log" >&2
    mysql_query "$primary_database" \
        "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank" >&2 || true
    mysql_query "$primary_database" \
        "SELECT name, administrator_id, administrator_username FROM t_system_bootstrap_lock" >&2 || true
    fail "the first production command failed"
fi
assert_log_contains "$temp_dir/first.log" "super-admin bootstrap created" "first run did not create the administrator"
assert_log_redacted "$temp_dir/first.log"

assert_equal "13" "$(mysql_query "$primary_database" \
    "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1 AND version IS NOT NULL")" \
    "V1-V13 were not applied"
assert_equal "3" "$(mysql_query "$primary_database" \
    "SELECT COUNT(*) FROM t_forum_category WHERE slug IN ('announcements','algorithms','problems')")" \
    "production forum categories were not seeded"
assert_equal "1" "$(mysql_query "$primary_database" \
    "SELECT COUNT(*) FROM t_user WHERE role = 2 AND status = 0 AND email_verified = 1 AND is_deleted = 0")" \
    "the first active verified super administrator was not created"
assert_equal "1" "$(mysql_query "$primary_database" \
    "SELECT COUNT(*) FROM t_audit_log WHERE action = 'SYSTEM_BOOTSTRAP_SUPER_ADMIN'")" \
    "the bootstrap audit event was not created"
assert_equal "1" "$(mysql_query "$primary_database" \
    "SELECT COUNT(*) FROM t_system_bootstrap_lock WHERE administrator_id IS NOT NULL AND administrator_username = 'admin' AND administrator_email = 'admin@coderushoj.test'")" \
    "the one-shot guard was not claimed"
assert_equal "1" "$(mysql_query "$primary_database" \
    "SELECT ABS(TIMESTAMPDIFF(SECOND, create_time, UTC_TIMESTAMP(3))) <= 30 FROM t_user WHERE username = 'admin'")" \
    "bootstrap CURRENT_TIMESTAMP did not use UTC when the MySQL server default was non-UTC"
first_hash="$(mysql_query "$primary_database" "SELECT password FROM t_user WHERE username = 'admin'")"
[[ "$first_hash" == "\$2a\$"* || "$first_hash" == "\$2b\$"* || "$first_hash" == "\$2y\$"* ]] \
    || fail "the administrator password is not BCrypt"

write_environment \
    "$temp_dir/replay.env" \
    "$primary_database" \
    admin \
    admin@coderushoj.test \
    "$replay_password"
if ! run_bootstrap "$temp_dir/replay.env" "$temp_dir/replay.log"; then
    assert_log_redacted "$temp_dir/replay.log"
    tail -n 80 "$temp_dir/replay.log" >&2
    fail "the exact replay command failed"
fi
assert_log_contains "$temp_dir/replay.log" "super-admin bootstrap already present" "exact replay was not idempotent"
assert_log_redacted "$temp_dir/replay.log"
assert_equal "$first_hash" "$(mysql_query "$primary_database" "SELECT password FROM t_user WHERE username = 'admin'")" \
    "exact replay rotated the password"

write_environment \
    "$temp_dir/conflict.env" \
    "$primary_database" \
    other-admin \
    other-admin@coderushoj.test \
    "$conflict_password"
set +e
run_bootstrap "$temp_dir/conflict.env" "$temp_dir/conflict.log"
conflict_status=$?
set -e
assert_equal "1" "$conflict_status" "a different identity did not fail closed"
assert_log_contains "$temp_dir/conflict.log" "conflicts with an existing account" "different identity failure was not a conflict"
assert_log_redacted "$temp_dir/conflict.log"
assert_equal "1" "$(mysql_query "$primary_database" "SELECT COUNT(*) FROM t_user WHERE role = 2")" \
    "a different identity created another super administrator"
assert_equal "$first_hash" "$(mysql_query "$primary_database" "SELECT password FROM t_user WHERE username = 'admin'")" \
    "a different identity changed the original password"

write_environment \
    "$temp_dir/concurrent-a.env" \
    "$concurrent_database" \
    concurrent-a \
    concurrent-a@coderushoj.test \
    "$concurrent_password_a"
write_environment \
    "$temp_dir/concurrent-b.env" \
    "$concurrent_database" \
    concurrent-b \
    concurrent-b@coderushoj.test \
    "$concurrent_password_b"
set +e
run_bootstrap "$temp_dir/concurrent-a.env" "$temp_dir/concurrent-a.log" &
pid_a=$!
run_bootstrap "$temp_dir/concurrent-b.env" "$temp_dir/concurrent-b.log" &
pid_b=$!
wait "$pid_a"
status_a=$?
wait "$pid_b"
status_b=$?
set -e
if ! { [[ "$status_a" == 0 && "$status_b" == 1 ]] || [[ "$status_a" == 1 && "$status_b" == 0 ]]; }; then
    fail "concurrent different identities must produce one success and one conflict (got $status_a/$status_b)"
fi
assert_log_redacted "$temp_dir/concurrent-a.log"
assert_log_redacted "$temp_dir/concurrent-b.log"
if [[ "$status_a" == 1 ]]; then
    assert_log_contains "$temp_dir/concurrent-a.log" "conflicts with an existing account" \
        "the losing concurrent command did not report an identity conflict"
else
    assert_log_contains "$temp_dir/concurrent-b.log" "conflicts with an existing account" \
        "the losing concurrent command did not report an identity conflict"
fi
assert_equal "13" "$(mysql_query "$concurrent_database" \
    "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1 AND version IS NOT NULL")" \
    "concurrent startup did not apply V1-V13 exactly once"
assert_equal "1" "$(mysql_query "$concurrent_database" "SELECT COUNT(*) FROM t_user WHERE role = 2")" \
    "concurrent different identities created more than one super administrator"
assert_equal "1" "$(mysql_query "$concurrent_database" \
    "SELECT COUNT(*) FROM t_system_bootstrap_lock WHERE administrator_id IS NOT NULL")" \
    "concurrent execution did not claim exactly one guard"
assert_equal "1" "$(mysql_query "$concurrent_database" \
    "SELECT COUNT(*) FROM t_audit_log WHERE action = 'SYSTEM_BOOTSTRAP_SUPER_ADMIN'")" \
    "concurrent execution did not create exactly one audit event"

# Reproduce an upgraded database where an administrator predates V9 and the new
# singleton guard is therefore still unclaimed. Bootstrap must not adopt or
# mutate that account merely because its identity matches the request.
mysql_query "$primary_database" \
    "UPDATE t_system_bootstrap_lock
        SET administrator_id = NULL,
            administrator_username = NULL,
            administrator_email = NULL,
            claimed_at = NULL
      WHERE name = 'first-super-admin'" >/dev/null
write_environment \
    "$temp_dir/legacy.env" \
    "$primary_database" \
    admin \
    admin@coderushoj.test \
    "$legacy_password"
set +e
run_bootstrap "$temp_dir/legacy.env" "$temp_dir/legacy.log"
legacy_status=$?
set -e
assert_equal "1" "$legacy_status" "a pre-V9 super administrator was silently adopted"
assert_log_contains "$temp_dir/legacy.log" "conflicts with an existing account" \
    "a pre-V9 super administrator did not fail closed"
assert_log_redacted "$temp_dir/legacy.log"
assert_equal "0" "$(mysql_query "$primary_database" \
    "SELECT COUNT(*) FROM t_system_bootstrap_lock WHERE administrator_id IS NOT NULL")" \
    "the pre-V9 identity claimed the singleton guard"
assert_equal "$first_hash" "$(mysql_query "$primary_database" "SELECT password FROM t_user WHERE username = 'admin'")" \
    "the pre-V9 identity changed the existing password"

descriptor_url="jdbc:mysql://address=(host=mysql)(port=3306)(user=${database_user})(password=${descriptor_password})/${primary_database}"
write_environment \
    "$temp_dir/descriptor.env" \
    "$primary_database" \
    rejected-admin \
    rejected-admin@coderushoj.test \
    "$conflict_password" \
    "$descriptor_url"
set +e
run_bootstrap "$temp_dir/descriptor.env" "$temp_dir/descriptor.log"
descriptor_status=$?
set -e
assert_equal "2" "$descriptor_status" "a Connector/J address descriptor with credentials was accepted"
assert_log_contains "$temp_dir/descriptor.log" "configuration is incomplete" "unsafe URL did not fail before database access"
assert_log_redacted "$temp_dir/descriptor.log"

printf 'MySQL %s admin bootstrap integration gate passed\n' "$mysql_version"
