# 首个超级管理员

全新数据库必须通过一次性 bootstrap 命令建立首个 `SUPER_ADMIN`。普通注册始终只能创建 `USER`，不要通过修改公开注册接口、提交明文 SQL 或在镜像内预置默认密码来绕过权限边界。

## 命令合同

使用与后端完全相同的生产镜像，并只在一次性容器或 Kubernetes Job 中注入以下变量：

| 变量 | 要求 |
| --- | --- |
| `CROJ_MODE` | 必须精确为 `bootstrap-admin` |
| `DATABASE_URL` | 简单 host/schema 形式的目标 MySQL JDBC URL；禁止内嵌凭据 |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | 有 Flyway 和用户写权限的一次性管理账号 |
| `BOOTSTRAP_ADMIN_USERNAME` | 3–50 位字母、数字、点、下划线或短横线 |
| `BOOTSTRAP_ADMIN_EMAIL` | 唯一邮箱，最长 100 字符 |
| `BOOTSTRAP_ADMIN_PASSWORD` | 至少 12 个 Unicode 字符且 UTF-8 编码不超过 72 bytes，只能来自 Secret |

JDBC URL 只能使用 `jdbc:mysql://host[:port]/schema` 或逗号分隔的简单 host 列表。命令拒绝 userinfo、Connector/J `address=(...)`/`(host=...)` descriptor、分号属性和未知 query 参数，避免 Flyway 或驱动日志泄露凭据。当前 query allowlist 为 `useUnicode`、`characterEncoding`、`serverTimezone`、`useSSL`、`sslMode`、`allowPublicKeyRetrieval`、`connectTimeout`、`socketTimeout`、`tcpKeepAlive`、`enabledTLSProtocols`、`verifyServerCertificate`、`requireSSL`；数据库用户名和密码必须使用独立变量。

命令先执行全部 Flyway 迁移，再锁定 V9 的 `first-super-admin` guard 行。没有任何超级管理员或身份冲突时，在同一事务中创建启用、已验证的超级管理员，把其 ID、用户名和邮箱写入 guard，并写入 `SYSTEM_BOOTSTRAP_SUPER_ADMIN` 审计事件。guard 一经声明不可换绑：同一身份重跑会成功退出但不会修改密码；任何其他身份、普通账号、禁用账号或软删除账号冲突都失败退出。升级旧库时，只要 guard 尚未认领但数据库已经存在任意 `SUPER_ADMIN`，命令也会失败关闭，绝不静默收编或修改旧账号；运维人员必须先审计并通过独立迁移流程处理历史权限。

成功日志只包含 `created` 或 `already present`，不会打印用户名、邮箱、数据库凭据或管理员密码。执行完成后应删除 Job 和承载初始密码的 Secret；后续密码变更使用正常的认证流程。

## 本地一次性执行

把真实变量放在 Git 忽略且权限为 `0600` 的文件中，然后运行已经构建的生产镜像：

```bash
chmod 600 .workspace/secrets/backend-admin-bootstrap.env
docker run --rm \
  --env-file .workspace/secrets/backend-admin-bootstrap.env \
  --network coderushoj-dev \
  ghcr.io/coderushoj/croj-backend@sha256:<64-hex-image-digest>
```

不要在 shell 命令行使用 `--env BOOTSTRAP_ADMIN_PASSWORD=...`，否则值可能进入 shell history 或进程列表。

## Kubernetes 职责边界

Backend 仓库提供生产镜像命令、V9 事务合同、V10 生产论坛分类以及 MySQL 8.4 集成门禁；[`CodeRushOJ/croj-platform`](https://github.com/CodeRushOJ/croj-platform) 的 `coderushoj` Helm chart 负责 disabled-by-default Kubernetes Job、Secret 引用、active deadline 和 Job/Secret 清理。Bootstrap Secret 只挂载到一次性 Job，绝不能进入长期 Backend Deployment。Kind 和生产环境均通过 platform chart 的管理员 bootstrap values 启用一次 Job，成功后立即关闭该 value 并删除 Secret；具体 values 名称和 Helm 命令以 platform 仓库同版本运维文档为准。

## 故障处理

- `configuration is incomplete/invalid`：检查 Secret 的键、长度和格式，不会访问或修改数据库。
- `conflicts with an existing account`：用户名或邮箱已被不同身份占用；命令不会提升、复活或重置该账号。
- `bootstrap failed`：数据库、迁移或事务失败；凭据不会出现在标准输出。先检查数据库连通性和 Flyway 状态，再用同一身份安全重跑。

## 自动验收

CI 构建生产镜像后运行 `tests/integration/admin-bootstrap-mysql84.sh <image>`。脚本使用临时 MySQL 8.4 schema 真实执行 V1–V10，验证生产论坛分类、首次创建、不同密码的同身份重放、不同身份冲突、不同身份并发竞争、旧库已有超级管理员时 fail-closed、BCrypt hash 不变、唯一 guard/审计记录，并扫描所有命令输出确保测试 Secret 未泄露。
