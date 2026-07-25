# CodeRushOJ Backend

Spring Boot 业务 API，当前覆盖身份、用户、题目、标签、提交、论坛、评论、题解、邮箱验证和管理端基础能力。完整 OJ v1 在保留现有接口价值的基础上演进为按业务能力拆分的模块化单体，并继续补齐竞赛、内容审核、对象存储和可靠判题投递。

论坛与题解使用 `/api/v1` 版本化 REST API。公开内容允许匿名读取，发布和删除需要 JWT；作者只能删除自己的内容，管理员和超级管理员可以执行内容治理。详细请求、分页、状态与前端联调契约见 [`docs/api/community.md`](docs/api/community.md)。

## 配置安全

运行时 Secret 不写入 Git，也不在构建阶段替换进 JAR。复制 `.env.example` 到本机 Secret 管理方式中，并至少设置：

- `DATABASE_PASSWORD`
- `REDIS_PASSWORD`
- `JWT_SECRET`（至少 32 个随机字节）
- `JUDGE_RESULT_SERVICE_TOKEN`（至少 32 个随机字节，仅判题服务与后端持有）
- `SMTP_PASSWORD`

数据库地址、用户名、SMTP 和 RocketMQ 地址也可以通过 `.env.example` 中的变量覆盖。`.env` 与 `.env.*` 默认被 Git 忽略，`.env.example` 只保留无效占位值。

## 数据库迁移与判题投递

Flyway 在应用启动时按顺序执行 `src/main/resources/db/migration` 中的生产迁移；`dev` Profile 额外加载可重复执行的标签与论坛分类种子。已经发布的版本迁移不可修改，结构变更必须新增更高版本迁移。

提交请求不会在数据库事务内直接访问 RocketMQ。后端在同一事务中写入提交记录、首次判题 attempt、题目提交计数和 `SubmissionRequested` Outbox 事件。后台发布器逐条 claim 并投递包含稳定 `eventId`、`submissionId`、`attemptNo`、语言等字段的 v1 JSON 事件。Broker 暂时不可用时按指数退避重试；后端异常退出留下的 claim 会在租约过期后由其他副本恢复。

Outbox 参数可通过 `.env.example` 中的 `OUTBOX_*` 变量覆盖。`OUTBOX_CLAIM_TIMEOUT` 必须至少是 `OUTBOX_PUBLISH_TIMEOUT` 的两倍，默认分别为 30 秒和 5 秒；不满足约束时应用拒绝启动，避免多副本在消息尚未发送完成时重复抢占。

判题器完成任务后调用 `POST /api/internal/v1/judge-results`。后端通过独立强服务令牌鉴权，以 `resultId` 幂等收件，并用数据库 CAS 只允许 `QUEUED/RUNNING` attempt 和 `PENDING` submission 进入一次终态；重复回传返回 `DUPLICATE`，过期 attempt、终态覆盖或复用 `resultId` 返回 HTTP 409。完整事件与回传契约见 [`docs/api/judge-result-ingestion.md`](docs/api/judge-result-ingestion.md)。

竞赛核心支持公开/私有比赛、报名名单、严格赛时题目可见性、公告、私密/公开澄清、固定题目版本的比赛提交，以及带封榜的 ACM 排名。比赛只持久化 `DRAFT/PUBLISHED/CANCELLED`，运行阶段按时间推导；冻结/最终快照只是可丢弃缓存，提交记录始终是真相源。接口、时间边界、权限和计分规则见 [`docs/api/contests.md`](docs/api/contests.md)。

论坛帖子、评论与题解的删除是状态迁移而非物理删除；公开查询只返回 `PUBLISHED`。题解记录发布时的 `problem_version_id`，确保题目后续更新不会改变历史题解所对应的题面。客户端只提交 Markdown，`content_html` 由服务端生成安全转义内容，禁止客户端注入 HTML。

## 测试

不需要在宿主机安装 Java。使用 Java 17 容器和持久化 Maven 缓存运行：

```bash
docker run --rm \
  -e DATABASE_PASSWORD=test-only \
  -e REDIS_PASSWORD=test-only \
  -e JWT_SECRET=test-only-secret-with-at-least-32-bytes \
  -e JUDGE_RESULT_SERVICE_TOKEN=test-only-judge-result-token-at-least-32-bytes \
  -e SMTP_PASSWORD=test-only \
  -v "$PWD:/workspace" \
  -v coderushoj-maven-cache:/root/.m2 \
  -w /workspace eclipse-temurin:17-jdk \
  ./mvnw test
```

生产部署由 `croj-platform` 固定镜像、注入 Kubernetes Secret 并运行跨仓库验收。不要把真实凭据写回 `application*.yml`。

<a id="production-container"></a>
## Production container（生产容器）

生产 Dockerfile 使用 Maven/JDK 17 builder 和 Distroless Java 17 Debian 13 runtime。两个基础镜像都固定 multi-platform OCI index digest；runtime 只包含 Java 运行时、`croj.jar` 与一个本机 Actuator 检查 class，不包含源码、Maven、JDK compiler、shell 或包管理器。

| 阶段 | 固定引用 | 目标平台 |
| --- | --- | --- |
| builder | `maven:3.9-eclipse-temurin-17@sha256:1ed5d1f54416b706707b4f3238f63a20bb06aab27c6d240090a2bb9ad895ed45` | `linux/amd64`, `linux/arm64` |
| runtime | `gcr.io/distroless/java17-debian13:nonroot@sha256:81d09cac6ec47f6a13c61a941557f95079213320f3ddbf9d353de9317669aab5` | `linux/amd64`, `linux/arm64` |

核验 index digest 与平台，不要把输出中的某个单平台 manifest digest 误写成 multi-platform base：

```bash
docker buildx imagetools inspect --raw \
  'maven:3.9-eclipse-temurin-17@sha256:1ed5d1f54416b706707b4f3238f63a20bb06aab27c6d240090a2bb9ad895ed45' |
  jq -r '.manifests[].platform | "\(.os)/\(.architecture)"'

docker buildx imagetools inspect --raw \
  'gcr.io/distroless/java17-debian13:nonroot@sha256:81d09cac6ec47f6a13c61a941557f95079213320f3ddbf9d353de9317669aab5' |
  jq -r '.manifests[].platform | "\(.os)/\(.architecture)"'
```

### 本地构建与离线检查

macOS 上可用 Homebrew 安装仓库验收工具；Docker、Buildx 与 Java 17 测试容器仍按前文准备：

```bash
brew install shellcheck actionlint
brew install anchore/syft/syft aquasecurity/trivy/trivy
```

构建可供本机检查的镜像。BuildKit cache 只缓存 Maven 依赖，不会复制到 runtime：

```bash
docker buildx build --load \
  --build-arg "VCS_REF=$(git rev-parse HEAD)" \
  --build-arg "VERSION=0.4.0-rc.1" \
  --build-arg "BUILD_DATE=$(git show -s --format=%cI HEAD)" \
  --tag coderushoj/croj-backend:local .

shellcheck tests/container/*.sh
tests/container/production-image-contract.sh
tests/container/inspect-production-image.sh coderushoj/croj-backend:local
```

`inspect-production-image.sh` 只调用 `docker image inspect`、`docker create` 和 `docker export`；created container 从未 start，脚本退出时会删除，因此不会启动 Spring Boot 或访问数据库、Redis、RocketMQ、SMTP。

生成带最大 provenance 和 BuildKit SBOM attestation 的 OCI archive，并额外输出可下载的 SPDX JSON：

```bash
docker buildx build \
  --provenance=mode=max \
  --sbom=true \
  --output type=oci,dest=target/croj-backend.oci.tar .

syft coderushoj/croj-backend:local -o spdx-json=target/croj-backend.spdx.json
trivy image --severity HIGH,CRITICAL --exit-code 1 coderushoj/croj-backend:local
```

Trivy 不使用 `--ignore-unfixed` 或默认 ignore file；任何 HIGH/CRITICAL 都必须在升级依赖或基础镜像后重新验证，不能通过把 exit code 改成 0 绕过。CI 同时上传 SARIF 和 SPDX artifact，并以阻断扫描作为最终结果。

### Kubernetes 运行合同

- 镜像固定 `USER 65532:65532`、`EXPOSE 7999` 和 `SPRING_PROFILES_ACTIVE=prod`；ENTRYPOINT 直接 exec `/usr/bin/java`，SIGTERM 能到达 Spring Boot。
- OCI healthcheck 只请求 `http://127.0.0.1:7999/api/actuator/health/liveness`，连接/读取各超时 1 秒，不跟随重定向、不读取响应体，只有 2xx 成功。Kubernetes startup/readiness 使用 `/api/actuator/health/readiness`。
- 根文件系统按只读运行。`/tmp` 必须挂 `emptyDir`；`TMPDIR=/tmp` 同时承载 JVM 和 multipart 临时文件。
- `/app/uploads` 必须显式挂载；开发环境可以使用可丢弃 `emptyDir`，production 必须使用管理员预建的 RWX PVC。`FILE_UPLOAD_DIR=/app/uploads`，镜像不声明匿名 `VOLUME`。
- `prod` Profile 启用 graceful shutdown，phase timeout 为 30 秒，与 `croj-platform` 的 `terminationGracePeriodSeconds: 30` 一致。

构建并推送 GHCR 镜像后，production Helm values 必须使用 registry 返回的 image digest，不使用可漂移 tag。Secret、RWX PVC、离线预检和回滚命令以 `croj-platform/docs/guide/application-deployment.md` 为准。
