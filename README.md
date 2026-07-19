# CodeRushOJ Backend

Spring Boot 业务 API，当前覆盖身份、用户、题目、标签、提交、论坛、评论、题解、全局公告、邮箱验证和管理端基础能力。完整 OJ v1 在保留现有接口价值的基础上演进为按业务能力拆分的模块化单体，并继续补齐竞赛、内容审核、对象存储和可靠判题投递。

论坛与题解使用 `/api/v1` 版本化 REST API。公开内容允许匿名读取，发布和删除需要 JWT；作者只能删除自己的内容，管理员和超级管理员可以执行内容治理。详细请求、分页、状态与前端联调契约见 [`docs/api/community.md`](docs/api/community.md)。

全局公告公开接口只返回当前发布窗口内的内容，支持稳定置顶顺序和分页；管理员可以创建草稿、排期、立即发布、撤回和归档。生命周期由 UTC 时间窗口即时推导，不依赖定时任务，所有变更保留操作者审计并使用乐观锁防止并发覆盖。完整接口与错误语义见 [`docs/api/announcements.md`](docs/api/announcements.md)。

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

提交数据库迁移前必须运行真实 MySQL 兼容门禁：

```bash
scripts/verify-mysql-migrations.sh
```

该命令只要求 Docker，不要求宿主机安装 Java、Maven 或 MySQL 客户端。脚本在私有 Docker network 中启动一次性 MySQL 8.4.10 和 Java 容器，先用 Flyway 将空库迁到 V6，写入旧版论坛数据，再升级到 V7；随后验证 V1-V7 历史、旧帖 `GENERAL/NULL` 回填、`CHECK` 约束、复合索引顺序及非法 `GENERAL + resource_id` 写入被数据库拒绝。脚本退出时自动删除数据库容器与 network，Maven 依赖保存在被 Git 忽略的 `.cache/maven`。

CI 使用 digest 固定的 MySQL 8.4.10 与 Java 镜像。排查镜像代理或预拉取问题时，可临时通过 `MYSQL_IMAGE`、`MAVEN_IMAGE`、`MAVEN_CACHE_DIR` 和 `MYSQL_START_TIMEOUT_SECONDS` 覆盖默认值；这些变量只控制一次性测试环境，不能用于传入生产凭据。

提交请求不会在数据库事务内直接访问 RocketMQ。后端在同一事务中写入提交记录、首次判题 attempt、题目提交计数和 `SubmissionRequested` Outbox 事件。后台发布器逐条 claim 并投递包含稳定 `eventId`、`submissionId`、`attemptNo`、语言等字段的 v1 JSON 事件。Broker 暂时不可用时按指数退避重试；后端异常退出留下的 claim 会在租约过期后由其他副本恢复。

Outbox 参数可通过 `.env.example` 中的 `OUTBOX_*` 变量覆盖。`OUTBOX_CLAIM_TIMEOUT` 必须至少是 `OUTBOX_PUBLISH_TIMEOUT` 的两倍，默认分别为 30 秒和 5 秒；不满足约束时应用拒绝启动，避免多副本在消息尚未发送完成时重复抢占。

判题器完成任务后调用 `POST /api/internal/v1/judge-results`。后端通过独立强服务令牌鉴权，以 `resultId` 幂等收件，并用数据库 CAS 只允许 `QUEUED/RUNNING` attempt 和 `PENDING` submission 进入一次终态；重复回传返回 `DUPLICATE`，过期 attempt、终态覆盖或复用 `resultId` 返回 HTTP 409。完整事件与回传契约见 [`docs/api/judge-result-ingestion.md`](docs/api/judge-result-ingestion.md)。

竞赛核心支持公开/私有比赛、报名名单、严格赛时题目可见性、公告、私密/公开澄清、固定题目版本的比赛提交，以及带封榜的 ACM 排名。比赛只持久化 `DRAFT/PUBLISHED/CANCELLED`，运行阶段按时间推导；冻结/最终快照只是可丢弃缓存，提交记录始终是真相源。接口、时间边界、权限和计分规则见 [`docs/api/contests.md`](docs/api/contests.md)。

论坛帖子通过 `resource_type + resource_id` 明确归属全站、公开题目或公开比赛；列表、详情和评论统一执行资源可见性校验，题目页不会混入其他题目的讨论。帖子、评论与题解的删除是状态迁移而非物理删除；公开查询只返回 `PUBLISHED`。题解记录发布时的 `problem_version_id`，确保题目后续更新不会改变历史题解所对应的题面。客户端只提交 Markdown，`content_html` 由服务端生成安全转义内容，禁止客户端注入 HTML。

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
