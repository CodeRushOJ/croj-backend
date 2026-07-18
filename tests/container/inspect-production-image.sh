#!/usr/bin/env bash
set -Eeuo pipefail

image_ref="${1:-}"
[[ -n "$image_ref" ]] || {
  printf 'usage: %s IMAGE_REFERENCE\n' "$0" >&2
  exit 64
}

command -v docker >/dev/null || {
  printf 'docker is required\n' >&2
  exit 69
}

container_id=""
inspection_dir="$(mktemp -d)"
cleanup() {
  if [[ -n "$container_id" ]]; then
    docker container rm "$container_id" >/dev/null
  fi
  rm -rf -- "$inspection_dir"
}
trap cleanup EXIT

fail() {
  printf 'image inspection: %s\n' "$1" >&2
  exit 1
}

user="$(docker image inspect --format '{{.Config.User}}' "$image_ref")"
[[ "$user" == '65532:65532' ]] || fail "expected user 65532:65532, found $user"

docker image inspect --format '{{json .Config.ExposedPorts}}' "$image_ref" | grep -q '7999/tcp' ||
  fail 'port 7999/tcp is not exposed'
docker image inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$image_ref" | grep -qx 'SPRING_PROFILES_ACTIVE=prod' ||
  fail 'prod profile is not active'
docker image inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$image_ref" | grep -qx 'TMPDIR=/tmp' ||
  fail 'TMPDIR is not /tmp'
docker image inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$image_ref" | grep -qx 'FILE_UPLOAD_DIR=/app/uploads' ||
  fail 'FILE_UPLOAD_DIR is not /app/uploads'

entrypoint="$(docker image inspect --format '{{json .Config.Entrypoint}}' "$image_ref")"
[[ "$entrypoint" == '["/usr/bin/java","-jar","/app/croj.jar"]' ]] || fail "unexpected entrypoint: $entrypoint"
healthcheck="$(docker image inspect --format '{{json .Config.Healthcheck.Test}}' "$image_ref")"
[[ "$healthcheck" == *'ActuatorHealthCheck'* ]] || fail 'Java Actuator healthcheck is missing'

for label in org.opencontainers.image.source org.opencontainers.image.revision org.opencontainers.image.base.name org.opencontainers.image.base.digest; do
  value="$(docker image inspect --format "{{index .Config.Labels \"$label\"}}" "$image_ref")"
  [[ -n "$value" && "$value" != '<no value>' ]] || fail "missing OCI label $label"
done

# `docker create` materializes metadata and a stopped rootfs. It does not execute
# the image ENTRYPOINT, so this test cannot start Spring Boot or contact services.
container_id="$(docker container create "$image_ref")"
docker container export "$container_id" --output "$inspection_dir/rootfs.tar"
tar -tf "$inspection_dir/rootfs.tar" > "$inspection_dir/files.txt"

grep -Eq '(^|\./)app/croj\.jar$' "$inspection_dir/files.txt" || fail 'application JAR is missing'
grep -Eq '(^|\./)app/healthcheck/com/coderushoj/container/ActuatorHealthCheck\.class$' "$inspection_dir/files.txt" ||
  fail 'compiled healthcheck class is missing'

for forbidden in \
  '(^|/)app/src(/|$)' \
  '(^|/)root/\.m2(/|$)' \
  '(^|/)(mvn|mvnw)$' \
  '(^|/)javac$' \
  '(^|/)(ba|d?a|z|k)?sh$' \
  '(^|/)(apt|apt-get|apk|yum|dnf|rpm|dpkg)$'; do
  if grep -Eq "$forbidden" "$inspection_dir/files.txt"; then
    fail "forbidden runtime path matches $forbidden"
  fi
done

printf 'image inspection: metadata and exported rootfs passed (service was not started)\n'
