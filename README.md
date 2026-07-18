# CodeRushOJ Backend

Spring Boot 业务 API，当前覆盖身份、用户、题目、标签、提交、邮箱验证和管理端基础能力。完整 OJ v1 将在保留现有接口价值的基础上演进为按业务能力拆分的模块化单体，并补齐竞赛、论坛、题解、审核、对象存储和可靠判题投递。

## 配置安全

运行时 Secret 不写入 Git，也不在构建阶段替换进 JAR。复制 `.env.example` 到本机 Secret 管理方式中，并至少设置：

- `DATABASE_PASSWORD`
- `REDIS_PASSWORD`
- `JWT_SECRET`（至少 32 个随机字节）
- `SMTP_PASSWORD`

数据库地址、用户名、SMTP 和 RocketMQ 地址也可以通过 `.env.example` 中的变量覆盖。`.env` 与 `.env.*` 默认被 Git 忽略，`.env.example` 只保留无效占位值。

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
