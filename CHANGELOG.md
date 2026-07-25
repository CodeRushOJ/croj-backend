# Changelog

本文件记录 CodeRushOJ Backend 已交付的主要能力。版本日期以代码进入仓库的迭代时间为准，早期原型能力按 README 和现有迁移整理。

## [Unreleased]

### Added

- SMTP 环境配置同时支持本地 Mailpit 明文传输、生产 STARTTLS（587）和隐式 TLS（465），认证、TLS 与凭据不再硬编码。
- 一次性首个超级管理员 bootstrap 模式：V9 持久化 identity guard、并发幂等创建、BCrypt 密码、审计事件和 Secret-only 生产镜像命令。
- 生产镜像级 MySQL 8.4 bootstrap 门禁，真实执行 V1–V9、重放、冲突、不同身份并发、旧库权限 fail-closed、hash 不变性和完整日志脱敏检查。
- 真实 MySQL 8.4 Flyway V1-V7 升级门禁：在一次性数据库中验证旧论坛数据回填、V7 `CHECK` 约束、精确复合索引及非法资源关联拒绝，并提供本地与 CI 共用脚本。
- TestBundle 与 Judging Server 统一使用严格 manifest v1；题包现在内嵌同一 `manifest.json`，并对未知字段、单用例、总展开大小及跨副本不一致 fail-closed。
- 论坛主题新增 `GENERAL/PROBLEM/CONTEST` 资源关联、按资源分页过滤和 V7 前向迁移；题目/比赛详情可只加载自己的讨论。
- 全局公告公开分页、当前公告和详情 API，严格按 UTC 发布窗口过滤并支持稳定置顶顺序。
- 公告管理端草稿、未来排期、立即发布、撤回、归档和全生命周期筛选 API。
- V6 公告迁移、管理员创建/更新/发布审计和基于客户端 `If-Match` 版本的并发覆盖保护。
- 可扩展 `ProblemPackageParser` 题目导入模型与 FreeProblemSet FPS XML 1.1/1.2/1.4 流式解析器，规范化限制单位、样例、隐藏测试、图片和可选代码资源。
- FPS 解析契约测试覆盖真实上游 1.4 题包、完整字段映射、版本拒绝、分组测试点配对以及 XXE/非官方 DTD fail-closed 行为。
- 生产后端镜像：digest 固定的 Maven/JDK 17 builder、Distroless Java 17 runtime、BuildKit 依赖缓存和 OCI provenance/SBOM。
- shell-free Actuator liveness healthcheck，严格 localhost、连接/读取超时、不跟随重定向且不读取响应体。
- 容器静态合同与 stopped-container rootfs 导出检查；验证 non-root UID/GID 65532、端口、prod Profile 及 runtime 不含源码/Maven/compiler/shell/package manager。
- GitHub Actions 镜像门禁：Syft SPDX artifact、Trivy SARIF，以及不忽略 unfixed 的 HIGH/CRITICAL 阻断扫描。
- 竞赛核心：公开/私有比赛、报名与托管名单、公告、澄清回复、严格赛时题目可见性。
- ACM 实时榜、冻结榜和最终榜，稳定 first AC、罚时与独占截止边界。
- 比赛提交固定编排时的不可变题目版本；冻结/最终榜加入可校验的数据库快照缓存。
- 普通与比赛提交统一固定可判题的已发布题目版本，并在缺少测试数据包时提前拒绝。
- 私有 S3/MinIO TestBundle 存储、SHA-256 内容寻址、manifest 限制与题目版本原子发布门禁。
- 管理端 FPS 题包预检/提交 REST 闭环：V8 持久化导入任务、私有对象暂存、管理员归属与过期校验、提交重解析及幂等返回。
- TestBundle 最终信任边界现在会流式核验真实 ZIP entry、实际字节数、manifest 完整性、路径安全、总解压大小与压缩比。
- 竞赛 V5 数据库迁移、H2 集成测试、MySQL 迁移契约和完整 API 文档。

### Security

- 生产依赖门禁移除无安全修复的 penggle/kaptcha，改用 `SecureRandom` 验证码生成器；RocketMQ 的 Netty、gRPC、Protobuf、BeanUtils 与 lz4 传递依赖升级到已修复版本，并由 Maven Enforcer 阻止回退。
- Trivy 仍阻断全部应用级 HIGH/CRITICAL；仅对 Distroless Debian 13 当前无修复包的 7 个 OS finding 使用包级、带说明且 2026-08-31 到期的临时例外。
- 全新部署不再依赖固定默认管理员密码；bootstrap guard 一经声明便拒绝所有其他身份，重跑不会提升冲突账号、复活已删除账号或静默重置凭据，命令输出经过凭据脱敏。
- Bootstrap JDBC URL 只接受简单 MySQL host/schema 与显式非敏感参数 allowlist，拒绝 userinfo、Connector/J address/host descriptor 和嵌套连接属性；密码同时执行 Unicode 字符下限与 BCrypt 72 UTF-8 bytes 上限。
- 论坛列表、详情、评论读取和评论发布统一复核关联资源可见性，阻止通过帖子 ID 访问隐藏题目或未公开比赛。
- 全局公告写接口仅允许管理员和超级管理员；公开响应不泄漏操作者 ID 或内部版本号。
- FPS XML 导入只允许官方 PUBLIC DOCTYPE 声明，同时禁用 DTD 解析、外部实体与解析器网络访问；题目、文本、测试点和内嵌图片均有硬上限，导入代码资源不会执行。
- runtime 使用无 shell/包管理器的 Distroless nonroot 基础镜像，Kubernetes 合同固定只读根文件系统，并只允许挂载 `/tmp` 与 `/app/uploads`。
- 管理端竞赛接口实施角色校验，阻止跨比赛澄清回复、无效题目版本和幽灵参赛用户。
- 题目编排与发布使用聚合行锁串行化，避免发布竞态破坏比赛题单。
- 题目创建和编辑强制先进入 DRAFT，阻止无隐藏测试包的版本绕过门禁公开。
- 已发布题目编辑会保留当前 `published_version_id` 与公开状态，只有新草稿完成 TestBundle 门禁后才切换发布指针。

### Changed

- `prod` Profile 的 graceful shutdown phase timeout 固定为 30 秒，并将 multipart 临时目录与上传目录分别对齐 `/tmp`、`/app/uploads`。
- 生产镜像工作流先在 Java 17 中运行完整 Maven 测试，测试失败时上传 Surefire 报告，镜像构建必须等待测试通过。
- 管理端跨域预检允许 `If-Match`，浏览器现在可以调用公告等使用乐观并发控制的写接口。

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
