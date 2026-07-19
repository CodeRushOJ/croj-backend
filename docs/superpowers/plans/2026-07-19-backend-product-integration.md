# Backend Product Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce one deployable backend branch that contains announcements, resource-scoped discussions, contests, judge callbacks, private TestBundle publication gates, FPS import adapters, and the production container assets.

**Architecture:** Keep `origin/codex/discussion-resources` as the product base because it already contains the ordered V1-V7 schema and the newest contest, announcement, and forum security fixes. Merge the TestBundle branch first from its `3dea71d` merge base, then merge the FPS/container branch from its older `d67e898` merge base, resolving shared documentation and configuration additively so newer product behavior is never replaced by an older branch snapshot.

**Tech Stack:** Java 17, Spring Boot 3.5, MyBatis-Plus, Flyway/MySQL 8.4, S3-compatible object storage, secure XML parsing, Docker, Maven.

---

### Task 1: Establish the integration baseline

**Files:**
- Create: `docs/superpowers/plans/2026-07-19-backend-product-integration.md`

- [x] Record the three exact remote heads and merge bases.
- [x] Verify the base has exactly one ordered Flyway chain from V1 through V7.
- [x] Create `codex/backend-product-integration` from `origin/codex/discussion-resources` in an isolated worktree.
- [x] Confirm the worktree is clean before merging.

### Task 2: Integrate private TestBundle storage

**Files:**
- Merge all changes from `origin/codex/test-bundle-storage`.
- Resolve shared files: `.env.example`, `pom.xml`, `README.md`, `CHANGELOG.md`, `src/main/resources/application.yml`, and `ProblemServiceImpl.java`.

- [x] Run `git merge --no-ff origin/codex/test-bundle-storage` and inspect every conflict against the branch merge base.
- [x] Preserve the current contest/announcement/forum code while adding S3 configuration, TestBundle services, and publication gating.
- [x] Verify no extra Flyway version is introduced and V1-V7 checksums remain unchanged.
- [x] Run focused TestBundle and problem publication tests in a Java 17 container.

### Task 3: Integrate FPS import and production image assets

**Files:**
- Merge all changes from `origin/codex/problem-import-adapters`.
- Resolve shared files: `.env.example`, `.gitattributes`, `README.md`, `CHANGELOG.md`, and production configuration.

- [x] Run `git merge --no-ff origin/codex/problem-import-adapters` and inspect every conflict against `d67e898`.
- [x] Preserve newer contest fixes and all TestBundle dependencies/configuration.
- [x] Keep the pinned FreeProblemSet fixture, LGPL notice, secure FPS 1.1/1.2/1.4 parser, and production Docker/CI contracts.
- [x] Run focused FPS parser, parser registry, container contract, and configuration tests.

### Task 4: Verify the single backend product line

**Files:**
- Modify as needed only when integration conflicts expose a real incompatibility.

- [x] Confirm announcements, discussions, contests, judge result ingestion, TestBundle storage, and FPS classes all compile together.
- [x] Confirm the migration directory contains one each of V1, V2, V3, V4, V5, V6, and V7.
- [x] Run the complete Maven test suite with Java 17 in a clean container and record the exact test count.
- [x] Run `git diff --check`, migration contract tests, and production container contract checks.
- [x] Inspect the final diff for accidentally reverted security or documentation changes.

### Task 5: Review and publish

**Files:**
- Review the complete range from `origin/codex/discussion-resources` to the integration head.

- [ ] Request an independent read-only review focused on Critical and Important integration regressions.
- [ ] Resolve all valid Critical and Important findings and rerun affected tests.
- [ ] Commit only intentional integration changes, push `codex/backend-product-integration`, and open a Draft PR against `codex/discussion-resources`.
- [ ] In the PR body, identify integrated PRs #20 and #22, migration guarantees, exact validation commands, and test count.

## Integration record

- Discussion base: `0162cb1fa8a5a971827370a40e218aded2ae58d8`
- TestBundle head: `31af831983d4ce230a23898d60aeb2b0e6f000e5`
- FPS/container head: `fb49ca558f3aa16d24bdd9aaf547e6a7840d5e8f`
- Discussion/TestBundle merge base: `3dea71de2ffa9b3548c2d747d7f0dd92b0a1804c`
- Discussion/FPS and TestBundle/FPS merge base: `d67e898a7509812715d587ce87f772a9598a4d88`
- Merge commits: `e4b780d` (TestBundle), `afcdd09` (FPS/container)
- Verification: Java 17 Maven suite `141/141`, focused integration suite `32/32`, Java 17 package, ShellCheck, production image static contract, `git diff --check`, and an unchanged unique V1-V7 migration chain.
