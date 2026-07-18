# Backend Production Image Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a digest-pinned, non-root Java 17 production image plus offline contracts and fail-visible image supply-chain CI, without starting the backend service.

**Architecture:** A Maven/JDK builder uses dependency-first layers and a BuildKit cache; a Distroless Java 17 runtime contains only the Spring Boot JAR and a tiny localhost-only Java Actuator checker. Kubernetes owns read-only-root enforcement and writable `/tmp` and `/app/uploads` mounts, while repository tests inspect both Docker metadata and the exported filesystem without executing the application.

**Tech Stack:** Docker BuildKit/Buildx, Maven 3.9, Eclipse Temurin JDK 17, Distroless Java 17 Debian 12 nonroot, Bash, GitHub Actions, Syft, Trivy.

---

### Task 1: Lock the offline container contract

**Files:**
- Create: `tests/container/production-image-contract.sh`
- Create: `tests/container/inspect-production-image.sh`
- Create: `.dockerignore`

- [ ] Write a static test that requires digest-pinned multi-stage bases, dependency cache/prefetch, UID/GID 65532, port 7999, prod Profile, writable-path environment, Java healthcheck, exec entrypoint, graceful shutdown, `.dockerignore`, and CI scan/provenance markers.
- [ ] Run the static test and confirm RED because the Dockerfile/workflow are absent.
- [ ] Write an image inspect/export test that never starts a container and rejects source, Maven, compiler, shell, package-manager, root user, or missing OCI metadata.
- [ ] Add a restrictive `.dockerignore` and shellcheck both scripts.

### Task 2: Build the minimal image and healthcheck

**Files:**
- Create: `Dockerfile`
- Create: `src/container/java/com/coderushoj/container/ActuatorHealthCheck.java`
- Create: `src/test/java/com/zephyr/croj/container/ActuatorHealthCheckContractTest.java`

- [ ] Write failing Java contract tests for fixed localhost URL, strict connect/read timeouts, no redirect, 2xx-only success, and response-body-free output behavior.
- [ ] Implement the dependency-free healthcheck class and make its focused tests GREEN.
- [ ] Resolve and record multi-architecture OCI index digests for builder and runtime bases.
- [ ] Implement dependency-first Maven multi-stage build, cache mount, package build, minimal copies, OCI labels, non-root user, environment, exposed port, Java healthcheck, and exec entrypoint.
- [ ] Run the offline static contract GREEN.

### Task 3: Align production runtime configuration

**Files:**
- Modify: `src/main/resources/application-prod.yml`
- Modify: `.env.example`

- [ ] Add a failing configuration assertion for graceful shutdown phase timeout and production writable paths.
- [ ] Configure `spring.lifecycle.timeout-per-shutdown-phase=30s` and document `/tmp` plus `/app/uploads` variables.
- [ ] Run focused Spring/configuration tests GREEN.

### Task 4: Build and inspect without starting the service

**Files:**
- Modify: `tests/container/inspect-production-image.sh`

- [ ] Build the local OCI image with BuildKit provenance/SBOM enabled.
- [ ] Run `docker inspect`, `docker create`, and `docker export` checks only; verify the default process is never executed.
- [ ] Verify `linux/amd64` and `linux/arm64` availability from the pinned multi-platform base provenance.
- [ ] Record exact image size, digest/config evidence, and filesystem checks.

### Task 5: Add fail-visible supply-chain CI

**Files:**
- Create: `.github/workflows/image.yml`

- [ ] Add Buildx cache-backed image build and load for inspection.
- [ ] Run static and exported-rootfs contracts in CI.
- [ ] Generate SPDX JSON with Syft and upload it as an artifact.
- [ ] Scan HIGH/CRITICAL with Trivy, upload SARIF, and fail on findings without suppressing unfixed vulnerabilities.
- [ ] Pin third-party Actions to commit SHA and request BuildKit provenance/SBOM.
- [ ] Re-run the static contract and workflow syntax checks GREEN.

### Task 6: Documentation, regression, review, and publication

**Files:**
- Modify: `README.md`
- Modify: `CHANGELOG.md`

- [ ] Document builder/runtime base provenance, local build/inspect/SBOM/scan commands, OCI health semantics, no-service test policy, Kubernetes read-only and writable-path contract, and production digest deployment.
- [ ] Update Unreleased changelog and remove any statement that the backend production Dockerfile is missing.
- [ ] Run Maven regression, static container contract, shellcheck, image build/inspect, and secret scan; do not start the application.
- [ ] Request code review focused on supply-chain fail-open behavior, rootfs contents, signal handling, and Helm consistency; fix blocking findings test-first.
- [ ] Update Issue #10 with exact evidence, commit, push `codex/backend-production-image`, and open a stacked Draft PR against `codex/contest-core-api`.
