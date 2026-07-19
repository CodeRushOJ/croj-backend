# Changelog

本文件记录 CodeRushOJ Backend 已交付的主要能力。版本日期以代码进入仓库的迭代时间为准，早期原型能力按 README 和现有迁移整理。

## [Unreleased]

### Added

- 可扩展 `ProblemPackageParser` 题目导入模型与 FreeProblemSet FPS XML 1.1/1.2 流式解析器，规范化限制单位、样例、隐藏测试、图片和可选代码资源。
- FPS 解析契约测试覆盖完整字段映射、版本拒绝、测试点配对以及 XXE/DTD fail-closed 行为。
- 生产后端镜像：digest 固定的 Maven/JDK 17 builder、Distroless Java 17 runtime、BuildKit 依赖缓存和 OCI provenance/SBOM。
- shell-free Actuator liveness healthcheck，严格 localhost、连接/读取超时、不跟随重定向且不读取响应体。
- 容器静态合同与 stopped-container rootfs 导出检查；验证 non-root UID/GID 65532、端口、prod Profile 及 runtime 不含源码/Maven/compiler/shell/package manager。
- GitHub Actions 镜像门禁：Syft SPDX artifact、Trivy SARIF，以及不忽略 unfixed 的 HIGH/CRITICAL 阻断扫描。
- 竞赛核心：公开/私有比赛、报名与托管名单、公告、澄清回复、严格赛时题目可见性。
- ACM 实时榜、冻结榜和最终榜，稳定 first AC、罚时与独占截止边界。
- 比赛提交固定编排时的不可变题目版本；冻结/最终榜加入可校验的数据库快照缓存。
- 普通与比赛提交统一固定可判题的已发布题目版本，并在缺少测试数据包时提前拒绝。
- 竞赛 V5 数据库迁移、H2 集成测试、MySQL 迁移契约和完整 API 文档。

### Security

- FPS XML 导入禁用 DTD、外部实体与解析器网络访问，并对题目、文本、测试点和内嵌图片设置硬上限；导入代码资源不会执行。
- runtime 使用无 shell/包管理器的 Distroless nonroot 基础镜像，Kubernetes 合同固定只读根文件系统，并只允许挂载 `/tmp` 与 `/app/uploads`。
- 管理端竞赛接口实施角色校验，阻止跨比赛澄清回复、无效题目版本和幽灵参赛用户。
- 题目编排与发布使用聚合行锁串行化，避免发布竞态破坏比赛题单。

### Changed

- `prod` Profile 的 graceful shutdown phase timeout 固定为 30 秒，并将 multipart 临时目录与上传目录分别对齐 `/tmp`、`/app/uploads`。

## [0.3.0] - 2026-07-18

### Added

- 判题结果回传 v1：服务令牌鉴权、幂等收件、attempt/submission 双 CAS 终态更新和审计记录。
- 可靠 Submission Outbox、claim 租约、指数退避和多副本恢复机制。
- 论坛帖子、评论、题解及内容举报、通知、审计表结构与版本化 API。

## [0.2.0] - 2026-07-17

### Added

- Flyway 生产迁移体系和开发种子数据。
- 题目不可变版本、测试数据包元数据、判题 attempt 与 RocketMQ 提交事件。
- JWT 登录、刷新会话、用户与管理员基础能力。

## [0.1.0] - 2025-01-11

### Added

- CodeRushOJ Spring Boot 原型：用户、题目、标签、提交记录、邮件验证和基础管理接口。
