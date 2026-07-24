# CodeRushOJ Backend

Spring Boot 业务 API，当前覆盖身份、用户、题目、标签、提交、竞赛、论坛、评论、题解、全局公告、邮箱验证、私有测试包和可靠判题投递。代码按业务能力组织为模块化单体，题目导入、发布门禁、比赛计分、内容治理与管理接口共享同一套事务和权限边界。

论坛与题解使用 `/api/v1` 版本化 REST API。公开内容允许匿名读取，发布和删除需要 JWT；作者只能删除自己的内容，管理员和超级管理员可以执行内容治理。详细请求、分页、状态与前端联调契约见 [`docs/api/community.md`](docs/api/community.md)。

全局公告公开接口只返回当前发布窗口内的内容，支持稳定置顶顺序和分页；管理员可以创建草稿、排期、立即发布、撤回和归档。生命周期由 UTC 时间窗口即时推导，不依赖定时任务，所有变更保留操作者审计并使用乐观锁防止并发覆盖。完整接口与错误语义见 [`docs/api/announcements.md`](docs/api/announcements.md)。

## 配置安全

运行时 Secret 不写入 Git，也不在构建阶段替换进 JAR。复制 `.env.example` 到本机 Secret 管理方式中，并至少设置：

- `DATABASE_PASSWORD`
- `REDIS_PASSWORD`
- `JWT_SECRET`（至少 32 个随机字节）
- `JUDGE_RESULT_SERVICE_TOKEN`（至少 32 个随机字节，仅判题服务与后端持有）
- `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY`（MinIO/S3 私有隐藏测试桶）

数据库地址、用户名、SMTP 和 RocketMQ 地址也可以通过 `.env.example` 中的变量覆盖。`.env` 与 `.env.*` 默认被 Git 忽略，`.env.example` 只保留本地开发值或无效占位值。

## SMTP 邮件配置

SMTP 传输完全由环境变量控制，不在镜像内固定认证或 TLS 模式。默认值适用于本机 Mailpit 的明文 SMTP（Web UI 通常为 `http://localhost:8025`）：

```dotenv
SMTP_HOST=localhost
SMTP_PORT=1025
SMTP_USERNAME=noreply@coderushoj.local
SMTP_PASSWORD=
SMTP_AUTH=false
SMTP_STARTTLS=false
SMTP_SSL=false
```

使用 587 端口的生产 SMTP 通常开启认证和 STARTTLS：

```dotenv
SMTP_HOST=smtp.example.com
SMTP_PORT=587
SMTP_USERNAME=replace-with-smtp-account
SMTP_PASSWORD=replace-with-smtp-authorization-code
SMTP_AUTH=true
SMTP_STARTTLS=true
SMTP_SSL=false
```

如果供应商要求 465 隐式 TLS，则使用：

```dotenv
SMTP_PORT=465
SMTP_AUTH=true
SMTP_STARTTLS=false
SMTP_SSL=true
```

生产 TLS 模式下 `SMTP_STARTTLS` 与 `SMTP_SSL` 二选一，不能同时开启；Mailpit 明文模式应同时关闭。在 Docker Compose 或 Kubernetes 中运行后端时，把 `SMTP_HOST` 改为 Mailpit 的 Service 名称。未启用认证时 `SMTP_PASSWORD` 可以留空，`SMTP_USERNAME` 仍建议使用合法发件地址。

## 首个超级管理员

全新数据库不包含固定管理员或默认密码。运维通过生产镜像的一次性 `CROJ_MODE=bootstrap-admin` 模式，从 Secret 读取身份和密码，执行 Flyway 后在事务 guard 中永久声明首个 `SUPER_ADMIN` 的 ID 与身份；相同身份重跑不会改密，任何其他身份或账号冲突都会失败关闭。长期 Backend Deployment 不接收这些 bootstrap 变量。完整命令、清理、跨仓 Kubernetes Job 职责和故障处理见 [`docs/operations/admin-bootstrap.md`](docs/operations/admin-bootstrap.md)。

## 数据库迁移与判题投递

Flyway 在应用启动时按顺序执行 `src/main/resources/db/migration` 中的生产迁移；`dev` Profile 额外加载可重复执行的标签与论坛分类种子。已经发布的版本迁移不可修改，结构变更必须新增更高版本迁移。

v1 发布版以全新的 MySQL schema 为安装合同。早期原型使用仓库根目录手工 `db.sql` 建表，没有 Flyway schema history，非空原型库不能直接原地升级为 v1。当前项目没有生产数据时，应创建新 schema、由 Flyway 执行 V1–V11，再通过一次性 bootstrap 建立首个管理员；如需保留历史原型数据，必须先导出并经过单独、可审计的数据迁移，不能通过 `baseline-on-migrate` 跳过 V1。V10 会为生产环境补齐公告、算法交流和题目讨论三个基础论坛分类，创建帖子不依赖 `dev` Profile；V11 为旧题目版本补齐公开投影所需的来源和难度字段。

提交数据库迁移前必须运行真实 MySQL 兼容门禁：

```bash
scripts/verify-mysql-migrations.sh
```

该命令只要求 Docker，不要求宿主机安装 Java、Maven 或 MySQL 客户端。脚本在私有 Docker network 中启动一次性 MySQL 8.4.10 和 Java 容器，先用 Flyway 将空库迁到 V6，写入旧版论坛数据，再升级到 V7 并最终迁到 V11；随后验证完整 V1–V11 历史、旧帖 `GENERAL/NULL` 回填、`CHECK` 约束、复合索引顺序、非法资源关联拒绝、生产论坛分类、既有运维自定义分类不被覆盖，以及旧题目版本公开投影字段的补齐与保留。脚本退出时自动删除数据库容器与 network，Maven 依赖保存在被 Git 忽略的 `.cache/maven`。

CI 使用 digest 固定的 MySQL 8.4.10 与 Java 镜像。排查镜像代理或预拉取问题时，可临时通过 `MYSQL_IMAGE`、`MAVEN_IMAGE`、`MAVEN_CACHE_DIR` 和 `MYSQL_START_TIMEOUT_SECONDS` 覆盖默认值；这些变量只控制一次性测试环境，不能用于传入生产凭据。

提交请求不会在数据库事务内直接访问 RocketMQ。后端在同一事务中写入提交记录、首次判题 attempt、题目提交计数和 `SubmissionRequested` Outbox 事件。后台发布器逐条 claim 并投递包含稳定 `eventId`、`submissionId`、`attemptNo`、语言等字段的 v1 JSON 事件。Broker 暂时不可用时按指数退避重试；后端异常退出留下的 claim 会在租约过期后由其他副本恢复。

Outbox 参数可通过 `.env.example` 中的 `OUTBOX_*` 变量覆盖。`OUTBOX_CLAIM_TIMEOUT` 必须至少是 `OUTBOX_PUBLISH_TIMEOUT` 的两倍，默认分别为 30 秒和 5 秒；不满足约束时应用拒绝启动，避免多副本在消息尚未发送完成时重复抢占。

判题器完成任务后调用 `POST /api/internal/v1/judge-results`。后端通过独立强服务令牌鉴权，以 `resultId` 幂等收件，并用数据库 CAS 只允许 `QUEUED/RUNNING` attempt 和 `PENDING` submission 进入一次终态；重复回传返回 `DUPLICATE`，过期 attempt、终态覆盖或复用 `resultId` 返回 HTTP 409。完整事件与回传契约见 [`docs/api/judge-result-ingestion.md`](docs/api/judge-result-ingestion.md)。

竞赛核心支持公开/私有比赛、报名名单、严格赛时题目可见性、公告、私密/公开澄清、固定题目版本的比赛提交，以及带封榜的 ACM 排名。比赛只持久化 `DRAFT/PUBLISHED/CANCELLED`，运行阶段按时间推导；冻结/最终快照只是可丢弃缓存，提交记录始终是真相源。接口、时间边界、权限和计分规则见 [`docs/api/contests.md`](docs/api/contests.md)。

论坛帖子通过 `resource_type + resource_id` 明确归属全站、公开题目或公开比赛；列表、详情和评论统一执行资源可见性校验，题目页不会混入其他题目的讨论。帖子、评论与题解的删除是状态迁移而非物理删除；公开查询只返回 `PUBLISHED`。题解记录发布时的 `problem_version_id`，确保题目后续更新不会改变历史题解所对应的题面。客户端只提交 Markdown，`content_html` 由服务端生成安全转义内容，禁止客户端注入 HTML。

题目创建和编辑只生成私有 `DRAFT` 版本，不再直接公开。导入或管理流程先把规范化隐藏测试绑定为 `TestBundle`，后端以 SHA-256 生成 `test-bundles/{problemId}/{versionId}/{sha256}.zip` 对象键并写入私有 S3/MinIO 桶，随后才可通过发布门禁原子设置 `PUBLISHED` 与 `published_version_id`。管理员先通过 `/api/v1/admin/problems/{problemId}/versions` 发现真实版本 ID 和状态，再使用带强 `If-Match` 的 `/api/v1/admin/problems/{problemId}/versions/{versionId}/test-bundle` 接口查看、上传并发布单个草稿版本；并发覆盖会被拒绝。配置、HTTP 契约、manifest 约束和故障模型见 [`docs/api/test-bundles.md`](docs/api/test-bundles.md)。

### 题目包导入

题目导入使用 `ProblemPackageParser` SPI 将外部格式转换成统一的 `ProblemImportDraft`。管理员通过 `POST /api/v1/admin/problem-imports/preflight` 上传原始 XML 或单 XML ZIP；验证成功后原包暂存到私有 S3/MinIO，V8 数据库存储 24 小时、归属管理员的导入任务。`POST /api/v1/admin/problem-imports/{jobId}/commit` 会锁定任务、校验对象摘要并重新解析，在同一事务中创建草稿、生成真实 TestBundle、通过发布门禁公开；已完成任务可安全重试。该设计适用于 Kubernetes 多副本，不依赖 Pod 本地状态。完整契约见 [`docs/api/test-bundles.md`](docs/api/test-bundles.md)。首个解析器准确面向 [FreeProblemSet](https://github.com/zhblue/freeproblemset/tree/master) FPS XML 1.1/1.2/1.4，兼容题面、时/内存单位、多样例、隐藏测试、图片、标准解、代码模板、SPJ/TPJ/Interactor 和远程题目标识。

FPS XML 使用 StAX 流式读取。解析器只接受 FreeProblemSet 官方 PUBLIC DOCTYPE 声明，但始终禁用 DTD 解析、外部实体与网络访问；包含内部实体或其他 DTD 的文件会被拒绝。文本、题目数、测试数和内嵌图片均有硬上限。导入的标准解与裁判程序只作为待审核资源保存，解析阶段绝不执行。兼容性测试包含 FreeProblemSet 上游提交 `7782b3815fd40f5bba95b5d7b90e3fbefafae656` 的真实 `fps-zhblue-A+B.xml`，覆盖其 1.4 版本、PUBLIC DOCTYPE 与测试点排列。后续解析器通过同一 SPI 增加 CodeRush 原生包、ICPC problem package 和 Polygon package，不在一个解析器内堆叠格式判断。

## 测试

不需要在宿主机安装 Java。使用 Java 17 容器和持久化 Maven 缓存运行：

```bash
docker run --rm \
  -e DATABASE_PASSWORD=test-only \
  -e REDIS_PASSWORD=test-only \
  -e JWT_SECRET=test-only-secret-with-at-least-32-bytes \
  -e JUDGE_RESULT_SERVICE_TOKEN=test-only-judge-result-token-at-least-32-bytes \
  -v "$PWD:/workspace" \
  -v coderushoj-maven-cache:/root/.m2 \
  -w /workspace eclipse-temurin:17-jdk \
  ./mvnw test
```

生产部署由 `croj-platform` 固定镜像、注入 Kubernetes Secret 并运行跨仓库验收。不要把真实凭据写回 `application*.yml`。

首个管理员还有一条生产镜像级 MySQL 8.4 回归门禁。它在临时网络和全新 schema 上执行 V1–V11、验证生产论坛分类、创建管理员、改密参数重放、不同身份冲突、并发不同身份、旧库已有超级管理员时 fail-closed 与全输出 Secret 扫描：

```bash
tests/integration/admin-bootstrap-mysql84.sh coderushoj/croj-backend:<tested-tag>
```

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

Pull Request 和分支构建只生成可下载、保留 7 天的 attested OCI archive，不向 registry 发布。推送经过 GitHub 验证的 `vX.Y.Z` 签名 tag 后，工作流才会登录 GHCR，并在全部 Java、镜像合同、SBOM 和 Trivy 门禁通过后发布 `linux/amd64`、`linux/arm64` 双架构镜像。发布同时生成版本 tag 和不可变 `sha-<full-commit-sha>` tag，从 registry 取得 digest 后再更新平台 source lock；不发布或部署 `latest`。

### Kubernetes 运行合同

- 镜像固定 `USER 65532:65532`、`EXPOSE 7999` 和 `SPRING_PROFILES_ACTIVE=prod`；ENTRYPOINT 直接 exec `/usr/bin/java`，SIGTERM 能到达 Spring Boot。
- OCI healthcheck 只请求 `http://127.0.0.1:7999/api/actuator/health/liveness`，连接/读取各超时 1 秒，不跟随重定向、不读取响应体，只有 2xx 成功。Kubernetes startup/readiness 使用 `/api/actuator/health/readiness`。
- 根文件系统按只读运行。`/tmp` 必须挂 `emptyDir`；`TMPDIR=/tmp` 同时承载 JVM 和 multipart 临时文件。
- `/app/uploads` 必须显式挂载；开发环境可以使用可丢弃 `emptyDir`，production 必须使用管理员预建的 RWX PVC。`FILE_UPLOAD_DIR=/app/uploads`，镜像不声明匿名 `VOLUME`。
- `prod` Profile 启用 graceful shutdown，phase timeout 为 30 秒，与 `croj-platform` 的 `terminationGracePeriodSeconds: 30` 一致。

构建并推送 GHCR 镜像后，production Helm values 必须使用 registry 返回的 image digest，不使用可漂移 tag。Secret、RWX PVC、离线预检和回滚命令以 `croj-platform/docs/guide/application-deployment.md` 为准。
