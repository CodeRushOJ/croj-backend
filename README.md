# CodeRushOJ Backend

Spring Boot 业务 API，当前覆盖身份、用户、题目、标签、提交、邮箱验证和管理端基础能力。完整 OJ v1 将在保留现有接口价值的基础上演进为按业务能力拆分的模块化单体，并补齐竞赛、论坛、题解、审核、对象存储和可靠判题投递。

## 配置安全

运行时 Secret 不写入 Git，也不在构建阶段替换进 JAR。复制 `.env.example` 到本机 Secret 管理方式中，并至少设置：

- `DATABASE_PASSWORD`
- `REDIS_PASSWORD`
- `JWT_SECRET`（至少 32 个随机字节）
- `SMTP_PASSWORD`

数据库地址、用户名、SMTP 和 RocketMQ 地址也可以通过 `.env.example` 中的变量覆盖。`.env` 与 `.env.*` 默认被 Git 忽略，`.env.example` 只保留无效占位值。

## 数据库迁移与判题投递

Flyway 在应用启动时按顺序执行 `src/main/resources/db/migration` 中的生产迁移；`dev` Profile 额外加载可重复执行的标签与论坛分类种子。已经发布的版本迁移不可修改，结构变更必须新增更高版本迁移。

提交请求不会在数据库事务内直接访问 RocketMQ。后端在同一事务中写入提交记录、题目提交计数和 `SubmissionRequested` Outbox 事件，后台发布器再逐条 claim 并同步投递 submission ID。Broker 暂时不可用时按指数退避重试；后端异常退出留下的 claim 会在租约过期后由其他副本恢复。

Outbox 参数可通过 `.env.example` 中的 `OUTBOX_*` 变量覆盖。`OUTBOX_CLAIM_TIMEOUT` 必须至少是 `OUTBOX_PUBLISH_TIMEOUT` 的两倍，默认分别为 30 秒和 5 秒；不满足约束时应用拒绝启动，避免多副本在消息尚未发送完成时重复抢占。

## 测试

不需要在宿主机安装 Java。使用 Java 17 容器和持久化 Maven 缓存运行：

```bash
docker run --rm \
  -e DATABASE_PASSWORD=test-only \
  -e REDIS_PASSWORD=test-only \
  -e JWT_SECRET=test-only-secret-with-at-least-32-bytes \
  -e SMTP_PASSWORD=test-only \
  -v "$PWD:/workspace" \
  -v coderushoj-maven-cache:/root/.m2 \
  -w /workspace eclipse-temurin:17-jdk \
  ./mvnw test
```

生产部署由 `croj-platform` 固定镜像、注入 Kubernetes Secret 并运行跨仓库验收。不要把真实凭据写回 `application*.yml`。
