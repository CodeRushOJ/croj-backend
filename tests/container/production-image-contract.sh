#!/usr/bin/env bash
set -Eeuo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
dockerfile="$repo_root/Dockerfile"
dockerignore="$repo_root/.dockerignore"
pom="$repo_root/pom.xml"
trivy_ignores="$repo_root/.trivyignore.yaml"
prod_config="$repo_root/src/main/resources/application-prod.yml"
healthcheck_source="$repo_root/src/main/java/com/coderushoj/container/ActuatorHealthCheck.java"
workflow="${IMAGE_WORKFLOW_FILE:-$repo_root/.github/workflows/image.yml}"

fail() {
  printf 'container contract: %s\n' "$1" >&2
  exit 1
}

require_file() {
  [[ -f "$1" ]] || fail "missing ${1#"$repo_root/"}"
}

require_pattern() {
  local file="$1"
  local pattern="$2"
  local description="$3"
  grep -Eq -- "$pattern" "$file" || fail "$description"
}

reject_pattern() {
  local file="$1"
  local pattern="$2"
  local description="$3"
  if grep -Eiq -- "$pattern" "$file"; then
    fail "$description"
  fi
}

for required in "$dockerfile" "$dockerignore" "$pom" "$trivy_ignores" "$prod_config" "$healthcheck_source" "$workflow"; do
  require_file "$required"
done

[[ "$(grep -Ec '^FROM .+@sha256:[0-9a-f]{64}( AS [A-Za-z0-9_-]+)?$' "$dockerfile")" -eq 2 ]] ||
  fail 'builder and runtime FROM must both use immutable sha256 digests'
require_pattern "$dockerfile" '^FROM maven:[^ ]+@sha256:[0-9a-f]{64} AS builder$' 'missing pinned Maven builder stage'
require_pattern "$dockerfile" '^FROM (gcr\.io/)?distroless/java17-debian13:[^ ]+@sha256:[0-9a-f]{64} AS runtime$' 'missing pinned Distroless Java 17 runtime stage'
require_pattern "$dockerfile" 'RUN --mount=type=cache,target=/root/\.m2' 'Maven BuildKit cache mount is required'
require_pattern "$dockerfile" 'dependency:go-offline' 'dependency-first Maven prefetch is required'
require_pattern "$dockerfile" 'mvn -B -ntp' 'build must use Maven from the digest-pinned builder image'
require_pattern "$dockerfile" '^USER 65532:65532$' 'runtime must use fixed UID/GID 65532'
require_pattern "$dockerfile" '^EXPOSE 7999$' 'runtime must expose port 7999'
require_pattern "$dockerfile" 'SPRING_PROFILES_ACTIVE=prod' 'runtime must activate the prod profile'
require_pattern "$dockerfile" 'TMPDIR=/tmp' 'runtime must use the mounted /tmp directory'
require_pattern "$dockerfile" 'FILE_UPLOAD_DIR=/app/uploads' 'runtime must use the mounted uploads directory'
require_pattern "$dockerfile" '^HEALTHCHECK .*CMD.*java.*ActuatorHealthCheck' 'runtime must declare the Java Actuator healthcheck'
require_pattern "$dockerfile" '^ENTRYPOINT \["/usr/bin/java","-XX:MaxRAMPercentage=75\.0","-Djava\.io\.tmpdir=/tmp","-jar","/app/croj\.jar"\]$' 'runtime entrypoint must directly exec Java with non-secret JVM settings'
require_pattern "$dockerfile" 'org\.opencontainers\.image\.base\.digest=' 'runtime base digest OCI metadata is required'
reject_pattern "$dockerfile" '(^|[[:space:]])(apt-get|apk|yum|dnf)[[:space:]]' 'Dockerfile must not install a shell or package manager payload'
reject_pattern "$dockerfile" 'JAVA_TOOL_OPTIONS' 'healthcheck must not inherit launcher options that Java prints to health logs'
reject_pattern "$dockerfile" '\./mvnw' 'production build must not download an unpinned Maven distribution'

reject_pattern "$pom" '<artifactId>kaptcha</artifactId>' 'unmaintained kaptcha must not enter the production dependency graph'
require_pattern "$pom" '<netty.version>4\.1\.136\.Final</netty.version>' 'Netty security floor is missing'
require_pattern "$pom" '<grpc.version>1\.75\.0</grpc.version>' 'gRPC security floor is missing'
require_pattern "$pom" '<protobuf.version>3\.25\.5</protobuf.version>' 'Protobuf security floor is missing'
require_pattern "$pom" '<commons-beanutils.version>1\.11\.0</commons-beanutils.version>' 'BeanUtils security floor is missing'
require_pattern "$pom" '<lz4-java.version>1\.10\.1</lz4-java.version>' 'lz4-java security floor is missing'
require_pattern "$pom" '<groupId>at\.yawk\.lz4</groupId>' 'maintained lz4-java coordinate is required'
require_pattern "$pom" '<artifactId>maven-enforcer-plugin</artifactId>' 'transitive vulnerability floors must be enforced'

while IFS= read -r base_ref; do
  grep -Fq -- "$base_ref" "$workflow" || fail "workflow must verify Dockerfile base $base_ref"
done < <(sed -nE 's/^FROM ([^ ]+@sha256:[0-9a-f]{64}) AS .+$/\1/p' "$dockerfile")

require_pattern "$healthcheck_source" 'http://127\.0\.0\.1:7999/api/actuator/health/liveness' 'healthcheck URL must be fixed to localhost liveness'
require_pattern "$healthcheck_source" 'setConnectTimeout\(' 'healthcheck must set a connect timeout'
require_pattern "$healthcheck_source" 'setReadTimeout\(' 'healthcheck must set a read timeout'
require_pattern "$healthcheck_source" 'setInstanceFollowRedirects\(false\)' 'healthcheck must reject redirects'
reject_pattern "$healthcheck_source" 'getInputStream\(|getErrorStream\(' 'healthcheck must not read or print a response body'

require_pattern "$prod_config" 'shutdown:[[:space:]]+graceful' 'prod profile must enable graceful shutdown'
require_pattern "$prod_config" 'timeout-per-shutdown-phase:[[:space:]]+30s' 'shutdown phase timeout must match the Helm grace period'

for ignored in '.git' 'target' '.m2' '.env' 'uploads' 'docs' 'tests'; do
  require_pattern "$dockerignore" "^${ignored//./\\.}(/|$)" ".dockerignore must exclude $ignored"
done

require_pattern "$workflow" 'provenance:[[:space:]]+mode=max' 'CI must generate maximum BuildKit provenance'
require_pattern "$workflow" '^  java-tests:' 'CI must define a Java test job before image construction'
require_pattern "$workflow" 'needs:[[:space:]]+java-tests' 'production image must depend on Java tests'
require_pattern "$workflow" '\./mvnw' 'CI Java job must use the repository Maven wrapper'
require_pattern "$workflow" 'maven\.repo\.local=.*[[:space:]]test$' 'CI Java job must run the Maven test suite'
require_pattern "$workflow" 'surefire-reports' 'CI must retain Surefire diagnostics on failure'
require_pattern "$workflow" 'sbom:[[:space:]]+true' 'CI must request BuildKit SBOM attestations'
require_pattern "$workflow" 'imagetools inspect --raw' 'CI must inspect machine-readable base index manifests'
require_pattern "$workflow" 'jq -e' 'CI must fail when required base platforms are absent'
require_pattern "$workflow" "hashFiles\('trivy-results\.sarif'\)[[:space:]]*!=[[:space:]]*''" 'SARIF upload must require an existing report'
require_pattern "$workflow" 'anchore/sbom-action' 'CI must generate a Syft SBOM'
require_pattern "$workflow" 'aquasecurity/trivy-action' 'CI must scan the image with Trivy'
[[ "$(grep -Ec 'trivyignores:[[:space:]]+\.trivyignore\.yaml' "$workflow")" -eq 2 ]] ||
  fail 'both Trivy report and gate must use the reviewed ignore file'
require_pattern "$workflow" "severity:[[:space:]]*['\"]?HIGH,CRITICAL" 'Trivy must report HIGH and CRITICAL findings'
require_pattern "$workflow" "exit-code:[[:space:]]*['\"]1['\"]?" 'Trivy must fail CI when HIGH/CRITICAL findings exist'
reject_pattern "$workflow" 'ignore-unfixed:[[:space:]]*true' 'CI must not hide unfixed HIGH/CRITICAL findings'
reject_pattern "$workflow" 'continue-on-error:[[:space:]]*true' 'image security gates must not fail open'

for unfixed_os_cve in \
  CVE-2025-59375 \
  CVE-2026-25210 \
  CVE-2026-45186 \
  CVE-2026-56131 \
  CVE-2026-56407 \
  CVE-2026-56408 \
  CVE-2026-53615; do
  require_pattern "$trivy_ignores" "id:[[:space:]]+$unfixed_os_cve" "missing reviewed exception for $unfixed_os_cve"
done
[[ "$(grep -Ec '^[[:space:]]+- id:[[:space:]]+CVE-' "$trivy_ignores")" -eq 7 ]] ||
  fail 'only the seven reviewed Distroless OS findings may be suppressed'
require_pattern "$trivy_ignores" 'expired_at:[[:space:]]+2026-08-31' 'temporary OS exceptions must expire'

printf 'container contract: static checks passed\n'
