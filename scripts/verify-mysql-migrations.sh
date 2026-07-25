#!/usr/bin/env bash

set -Eeuo pipefail

readonly MYSQL_IMAGE="${MYSQL_IMAGE:-mysql:8.4.10}"
readonly MAVEN_IMAGE="${MAVEN_IMAGE:-eclipse-temurin:17-jdk}"
readonly MYSQL_DATABASE="${MYSQL_DATABASE:-croj_migration_gate}"
readonly MYSQL_USER="${MYSQL_USER:-croj_migrator}"
readonly MYSQL_PASSWORD="${MYSQL_PASSWORD:-croj-migration-only}"
readonly MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-croj-root-migration-only}"
readonly MYSQL_START_TIMEOUT_SECONDS="${MYSQL_START_TIMEOUT_SECONDS:-90}"
readonly CONTAINER_NAME="croj-mysql-migration-${RANDOM}-$$"
readonly NETWORK_NAME="croj-mysql-migration-${RANDOM}-$$"
REPOSITORY_ROOT="$(pwd -P)"
readonly REPOSITORY_ROOT
readonly MAVEN_CACHE_DIR="${MAVEN_CACHE_DIR:-$REPOSITORY_ROOT/.cache/maven}"

FLYWAY_VERSION="$(sed -n 's:^[[:space:]]*<flyway.version>\([^<]*\)</flyway.version>[[:space:]]*$:\1:p' pom.xml)"
readonly FLYWAY_VERSION
MYSQL_CONNECTOR_VERSION="$(sed -n 's:^[[:space:]]*<mysql.version>\([^<]*\)</mysql.version>[[:space:]]*$:\1:p' pom.xml)"
readonly MYSQL_CONNECTOR_VERSION

container_started=false
network_started=false

cleanup() {
  if [[ "$container_started" == true ]]; then
    docker rm --force "$CONTAINER_NAME" >/dev/null 2>&1 || true
  fi
  if [[ "$network_started" == true ]]; then
    docker network rm "$NETWORK_NAME" >/dev/null 2>&1 || true
  fi
}

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

mysql_query() {
  local sql="$1"
  docker exec \
    --env "MYSQL_PWD=$MYSQL_PASSWORD" \
    "$CONTAINER_NAME" \
    mysql --batch --skip-column-names --raw \
    --user="$MYSQL_USER" "$MYSQL_DATABASE" \
    --execute="$sql"
}

assert_equals() {
  local description="$1"
  local expected="$2"
  local actual="$3"
  if [[ "$actual" != "$expected" ]]; then
    fail "$description: expected '$expected', got '$actual'"
  fi
  printf 'PASS: %s\n' "$description"
}

run_flyway() {
  local target="$1"
  docker run --rm \
    --network "$NETWORK_NAME" \
    --env MAVEN_USER_HOME=/maven-cache \
    --volume "$REPOSITORY_ROOT:/workspace:ro" \
    --volume "$MAVEN_CACHE_DIR:/maven-cache" \
    --workdir /workspace \
    "$MAVEN_IMAGE" \
    ./mvnw --file scripts/migration-gate/pom.xml \
    --batch-mode --no-transfer-progress \
    -Dmaven.repo.local=/maven-cache \
    -Dflyway.version="$FLYWAY_VERSION" \
    -Dmysql.version="$MYSQL_CONNECTOR_VERSION" \
    -Dflyway.url="jdbc:mysql://${CONTAINER_NAME}:3306/${MYSQL_DATABASE}?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC" \
    -Dflyway.user="$MYSQL_USER" \
    -Dflyway.password="$MYSQL_PASSWORD" \
    -Dflyway.locations=filesystem:/workspace/src/main/resources/db/migration \
    -Dflyway.target="$target" \
    flyway:migrate
}

trap cleanup EXIT INT TERM

command -v docker >/dev/null 2>&1 || fail "docker is required"
[[ -x ./mvnw ]] || fail "run this script from the repository root"
[[ -n "$FLYWAY_VERSION" ]] || fail "pom.xml must pin flyway.version for the migration gate"
[[ -n "$MYSQL_CONNECTOR_VERSION" ]] || fail "pom.xml must pin mysql.version for the migration gate"
[[ "$MYSQL_START_TIMEOUT_SECONDS" =~ ^[1-9][0-9]*$ ]] || \
  fail "MYSQL_START_TIMEOUT_SECONDS must be a positive integer"

printf 'Starting disposable MySQL 8.4 container from %s\n' "$MYSQL_IMAGE"
mkdir -p "$MAVEN_CACHE_DIR"
docker network create "$NETWORK_NAME" >/dev/null
network_started=true
docker run --detach --rm \
  --name "$CONTAINER_NAME" \
  --network "$NETWORK_NAME" \
  --env "MYSQL_ROOT_PASSWORD=$MYSQL_ROOT_PASSWORD" \
  --env "MYSQL_DATABASE=$MYSQL_DATABASE" \
  --env "MYSQL_USER=$MYSQL_USER" \
  --env "MYSQL_PASSWORD=$MYSQL_PASSWORD" \
  "$MYSQL_IMAGE" \
  --authentication-policy='*,,' >/dev/null
container_started=true

ready=false
for ((attempt = 1; attempt <= MYSQL_START_TIMEOUT_SECONDS; attempt++)); do
  if docker exec \
    --env "MYSQL_PWD=$MYSQL_PASSWORD" \
    "$CONTAINER_NAME" \
    mysqladmin ping --silent \
    --protocol=TCP --host=127.0.0.1 --port=3306 \
    --user="$MYSQL_USER" >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 1
done

if [[ "$ready" != true ]]; then
  docker logs "$CONTAINER_NAME" >&2 || true
  fail "MySQL did not become ready within ${MYSQL_START_TIMEOUT_SECONDS}s"
fi

printf 'Migrating a clean schema to legacy target V6\n'
run_flyway 6

legacy_id="$(mysql_query "
  INSERT INTO t_forum_post (
    category_id, author_id, title, content_markdown, content_html,
    status, pinned, locked
  ) VALUES (
    11, 42, 'legacy migration gate post', 'legacy markdown', '<p>legacy</p>',
    'PUBLISHED', 0, 0
  );
  SELECT LAST_INSERT_ID();
")"
[[ "$legacy_id" =~ ^[0-9]+$ ]] || fail "could not create the legacy forum row"

printf 'Upgrading the populated legacy schema from V6 to V7\n'
run_flyway 7

flyway_versions="$(mysql_query "
  SELECT GROUP_CONCAT(version ORDER BY installed_rank SEPARATOR ',')
  FROM flyway_schema_history
  WHERE type = 'SQL' AND success = 1;
")"
assert_equals "Flyway recorded successful V1-V7 migrations" \
  "1,2,3,4,5,6,7" "$flyway_versions"

legacy_resource="$(mysql_query "
  SELECT CONCAT(resource_type, '|', IFNULL(CAST(resource_id AS CHAR), 'NULL'))
  FROM t_forum_post
  WHERE id = ${legacy_id};
")"
assert_equals "legacy forum rows are backfilled to GENERAL with no resource id" \
  "GENERAL|NULL" "$legacy_resource"

constraint_type="$(mysql_query "
  SELECT constraint_type
  FROM information_schema.table_constraints
  WHERE constraint_schema = DATABASE()
    AND table_name = 't_forum_post'
    AND constraint_name = 'chk_forum_resource_ref';
")"
assert_equals "V7 installs the named forum resource CHECK constraint" \
  "CHECK" "$constraint_type"

index_columns="$(mysql_query "
  SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 't_forum_post'
    AND index_name = 'idx_forum_resource_feed';
")"
assert_equals "V7 installs the exact resource feed index order" \
  "resource_type,resource_id,status,pinned,created_at" "$index_columns"

mysql_query "
  INSERT INTO t_forum_post (
    category_id, author_id, resource_type, resource_id, title,
    content_markdown, content_html, status, pinned, locked
  ) VALUES (
    11, 42, 'PROBLEM', 1001, 'valid problem post',
    'valid markdown', '<p>valid</p>', 'PUBLISHED', 0, 0
  );
" >/dev/null
printf 'PASS: V7 accepts a valid PROBLEM resource reference\n'

if invalid_output="$(mysql_query "
  INSERT INTO t_forum_post (
    category_id, author_id, resource_type, resource_id, title,
    content_markdown, content_html, status, pinned, locked
  ) VALUES (
    11, 42, 'GENERAL', 1001, 'invalid general post',
    'invalid markdown', '<p>invalid</p>', 'PUBLISHED', 0, 0
  );
" 2>&1)"; then
  fail "V7 accepted an invalid GENERAL resource with a non-null resource id"
fi

if [[ "$invalid_output" != *"chk_forum_resource_ref"* ]]; then
  fail "invalid GENERAL resource failed for an unexpected reason: $invalid_output"
fi
printf 'PASS: V7 rejects GENERAL resources with a non-null resource id\n'

mysql_query "
  INSERT INTO t_forum_category (name, slug, sort_order)
  VALUES ('Operator custom problem forum', 'problems', 999);
" >/dev/null

mysql_query "
  INSERT INTO t_problem (
    id, problem_no, title, description, input_description, output_description,
    difficulty, source, create_user_id, status, published_version_id
  ) VALUES (
    9001, 'PMIG11', 'Legacy draft title', 'Legacy description', 'Legacy input', 'Legacy output',
    3, 'legacy-source', 42, 0, NULL
  );
  INSERT INTO t_problem_version (
    id, problem_id, version_no, state, statement_json, limits_json, judge_config_json,
    created_by, published_at
  ) VALUES (
    9101, 9001, 1, 'PUBLISHED',
    JSON_OBJECT(
      'title', 'Legacy published title',
      'description', 'Legacy description',
      'inputDescription', 'Legacy input',
      'outputDescription', 'Legacy output',
      'hints', JSON_ARRAY(),
      'samples', JSON_ARRAY()
    ),
    JSON_OBJECT('timeLimit', 1000, 'memoryLimit', 256, 'totalScore', 100),
    JSON_OBJECT(
      'specialJudge', FALSE,
      'specialJudgeCode', NULL,
      'specialJudgeLanguage', NULL,
      'judgeMode', 0
    ),
    42, CURRENT_TIMESTAMP(3)
  ), (
    9102, 9001, 2, 'DRAFT',
    JSON_OBJECT(
      'title', 'Historical draft title',
      'description', 'Historical description',
      'inputDescription', 'Historical input',
      'outputDescription', 'Historical output',
      'hints', JSON_ARRAY(),
      'samples', JSON_ARRAY(),
      'source', 'snapshot-source',
      'tags', JSON_ARRAY()
    ),
    JSON_OBJECT('timeLimit', 2000, 'memoryLimit', 512, 'totalScore', 100),
    JSON_OBJECT(
      'specialJudge', FALSE,
      'specialJudgeCode', NULL,
      'specialJudgeLanguage', NULL,
      'judgeMode', 0,
      'checker', 'exact',
      'difficulty', 1
    ),
    42, NULL
  );
  UPDATE t_problem SET published_version_id = 9101 WHERE id = 9001;

  INSERT INTO t_problem (
    id, problem_no, title, description, input_description, output_description,
    difficulty, source, create_user_id, status, published_version_id
  ) VALUES (
    9002, 'PMIG12', 'Malformed legacy title', 'Legacy description', 'Legacy input', 'Legacy output',
    2, 'legacy-source', 42, 0, NULL
  );
  INSERT INTO t_problem_version (
    id, problem_id, version_no, state, statement_json, limits_json, judge_config_json,
    created_by, published_at
  ) VALUES (
    9201, 9002, 1, 'PUBLISHED',
    JSON_OBJECT(
      'title', NULL,
      'description', 'Legacy description',
      'inputDescription', 'Legacy input',
      'outputDescription', 'Legacy output',
      'hints', JSON_ARRAY(),
      'samples', JSON_ARRAY(),
      'source', 'legacy-source',
      'tags', JSON_ARRAY(JSON_OBJECT(
        'id', 'not-a-number', 'name', NULL, 'color', '#123456'
      ))
    ),
    JSON_OBJECT('timeLimit', '1000', 'memoryLimit', 256, 'totalScore', 100),
    JSON_OBJECT(
      'specialJudge', FALSE,
      'specialJudgeCode', NULL,
      'specialJudgeLanguage', NULL,
      'judgeMode', 0,
      'checker', 'exact',
      'difficulty', NULL
    ),
    42, CURRENT_TIMESTAMP(3)
  );
  UPDATE t_problem SET published_version_id = 9201 WHERE id = 9002;
" >/dev/null

legacy_version_hashes_before="$(mysql_query "
  SELECT GROUP_CONCAT(
    CONCAT(
      id, ':',
      SHA2(CONCAT(
        CAST(statement_json AS CHAR),
        '|',
        CAST(limits_json AS CHAR),
        '|',
        CAST(judge_config_json AS CHAR)
      ), 256)
    )
    ORDER BY id SEPARATOR ','
  )
  FROM t_problem_version
  WHERE problem_id = 9001;
")"

printf 'Upgrading the populated schema through V11\n'
run_flyway 11

flyway_versions="$(mysql_query "
  SELECT GROUP_CONCAT(version ORDER BY installed_rank SEPARATOR ',')
  FROM flyway_schema_history
  WHERE type = 'SQL' AND success = 1;
")"
assert_equals "Flyway recorded successful V1-V11 migrations" \
  "1,2,3,4,5,6,7,8,9,10,11" "$flyway_versions"

category_slugs="$(mysql_query "
  SELECT GROUP_CONCAT(slug ORDER BY sort_order, slug SEPARATOR ',')
  FROM t_forum_category
  WHERE slug IN ('announcements', 'algorithms', 'problems');
")"
assert_equals "V10 provides every production forum category" \
  "announcements,algorithms,problems" "$category_slugs"

custom_category="$(mysql_query "
  SELECT CONCAT(name, '|', sort_order)
  FROM t_forum_category
  WHERE slug = 'problems';
")"
assert_equals "V10 preserves an operator-customized category" \
  "Operator custom problem forum|999" "$custom_category"

legacy_version_count="$(mysql_query "
  SELECT COUNT(*)
  FROM t_problem_version
  WHERE problem_id = 9001;
")"
assert_equals "V11 preserves every historical problem version row" \
  "2" "$legacy_version_count"

legacy_version_hashes_after="$(mysql_query "
  SELECT GROUP_CONCAT(
    CONCAT(
      id, ':',
      SHA2(CONCAT(
        CAST(statement_json AS CHAR),
        '|',
        CAST(limits_json AS CHAR),
        '|',
        CAST(judge_config_json AS CHAR)
      ), 256)
    )
    ORDER BY id SEPARATOR ','
  )
  FROM t_problem_version
  WHERE problem_id = 9001;
")"
assert_equals "V11 never rewrites historical snapshot JSON from mutable draft fields" \
  "$legacy_version_hashes_before" "$legacy_version_hashes_after"

legacy_projection_flags="$(mysql_query "
  SELECT GROUP_CONCAT(
    CONCAT(id, ':', projection_complete)
    ORDER BY id SEPARATOR ','
  )
  FROM t_problem_version
  WHERE problem_id = 9001;
")"
assert_equals "V11 distinguishes complete self-contained snapshots from uncertain history" \
  "9101:0,9102:1" "$legacy_projection_flags"

legacy_visibility="$(mysql_query "
  SELECT CONCAT(status, '|', IFNULL(CAST(published_version_id AS CHAR), 'NULL'))
  FROM t_problem
  WHERE id = 9001;
")"
assert_equals "V11 fails closed when the published pointer targets an incomplete version" \
  "1|NULL" "$legacy_visibility"

malformed_projection="$(mysql_query "
  SELECT CONCAT(
    pv.projection_complete,
    '|',
    p.status,
    '|',
    IFNULL(CAST(p.published_version_id AS CHAR), 'NULL')
  )
  FROM t_problem_version pv
  JOIN t_problem p ON p.id = pv.problem_id
  WHERE pv.id = 9201;
")"
assert_equals "V11 rejects path-complete projections with invalid JSON value types" \
  "0|1|NULL" "$malformed_projection"

legacy_missing_projection="$(mysql_query "
  SELECT CONCAT(
    IF(
      JSON_CONTAINS_PATH(statement_json, 'one', '$.source'),
      JSON_UNQUOTE(JSON_EXTRACT(statement_json, '$.source')),
      'MISSING'
    ),
    '|',
    IF(
      JSON_CONTAINS_PATH(judge_config_json, 'one', '$.difficulty'),
      JSON_UNQUOTE(JSON_EXTRACT(judge_config_json, '$.difficulty')),
      'MISSING'
    )
  )
  FROM t_problem_version
  WHERE id = 9101;
")"
assert_equals "V11 does not copy the latest draft projection into an older version" \
  "MISSING|MISSING" "$legacy_missing_projection"

complete_projection="$(mysql_query "
  SELECT CONCAT(
    JSON_UNQUOTE(JSON_EXTRACT(statement_json, '$.source')),
    '|',
    JSON_UNQUOTE(JSON_EXTRACT(judge_config_json, '$.difficulty'))
  )
  FROM t_problem_version
  WHERE id = 9102;
")"
assert_equals "V11 preserves fields already stored in a problem snapshot" \
  "snapshot-source|1" "$complete_projection"

mysql_query "
  INSERT INTO t_problem_tag (id, name, color)
  VALUES (77, 'recovered-tag', '#123456');
  INSERT INTO t_problem_version (
    id, problem_id, version_no, state, statement_json, limits_json,
    judge_config_json, created_by, published_at, projection_complete
  ) VALUES (
    9103, 9001, 3, 'DRAFT',
    JSON_OBJECT(
      'title', 'Audited replacement',
      'description', 'Audited description',
      'inputDescription', 'Audited input',
      'outputDescription', 'Audited output',
      'hints', JSON_ARRAY(),
      'samples', JSON_ARRAY(),
      'source', 'audited-source',
      'tags', JSON_ARRAY(JSON_OBJECT(
        'id', 77, 'name', 'recovered-tag', 'color', '#123456'
      ))
    ),
    JSON_OBJECT('timeLimit', 1000, 'memoryLimit', 256, 'totalScore', 100),
    JSON_OBJECT(
      'specialJudge', FALSE,
      'specialJudgeCode', NULL,
      'specialJudgeLanguage', NULL,
      'judgeMode', 0,
      'checker', 'exact',
      'difficulty', 2
    ),
    42, NULL, 1
  );
  INSERT INTO t_test_bundle (
    problem_version_id, object_key, sha256, size_bytes, manifest_json
  ) VALUES (
    9103, 'test-bundles/9001/9103/recovered.zip', REPEAT('b', 64), 12,
    JSON_OBJECT(
      'schemaVersion', 1,
      'judgeMode', 'ACM',
      'checker', 'exact',
      'limits', JSON_OBJECT('timeLimitMillis', 1000, 'memoryLimitMiB', 256),
      'cases', JSON_ARRAY(JSON_OBJECT(
        'id', '1',
        'input', 'cases/1.in',
        'output', 'cases/1.out',
        'weight', 1
      ))
    )
  );
  START TRANSACTION;
  SELECT id FROM t_problem WHERE id = 9001 FOR UPDATE;
  SELECT id FROM t_problem_version WHERE id = 9103 AND problem_id = 9001 FOR UPDATE;
  UPDATE t_problem_version
  SET state = 'PUBLISHED', published_at = CURRENT_TIMESTAMP(3)
  WHERE id = 9103 AND state = 'DRAFT' AND projection_complete = 1;
  DELETE FROM t_problem_tag_relation WHERE problem_id = 9001;
  INSERT INTO t_problem_tag_relation (problem_id, tag_id) VALUES (9001, 77);
  UPDATE t_problem
  SET published_version_id = 9103, status = 0
  WHERE id = 9001 AND is_deleted = 0;
  COMMIT;
" >/dev/null

recovered_projection="$(mysql_query "
  SELECT CONCAT(
    p.status, '|', p.published_version_id, '|', pv.state, '|', r.tag_id
  )
  FROM t_problem p
  JOIN t_problem_version pv ON pv.id = p.published_version_id
  JOIN t_problem_tag_relation r ON r.problem_id = p.id
  WHERE p.id = 9001;
")"
assert_equals "an audited complete replacement can restore public visibility" \
  "0|9103|PUBLISHED|77" "$recovered_projection"

printf 'MySQL 8.4 Flyway migration gate passed.\n'
