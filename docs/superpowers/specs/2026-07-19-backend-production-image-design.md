# Backend Production Image 设计

## 目标与边界

本阶段为 Spring Boot 后端交付可复现、最小权限、可由 Kubernetes 只读运行的生产镜像，并把镜像供应链检查沉淀到 CI。只执行构建、静态合同检查、镜像导出检查和安全扫描；不启动业务进程，也不连接 MySQL、Redis、RocketMQ 或 SMTP。

镜像合同与 `croj-platform` Chart 保持一致：服务端口为 `7999`，Servlet context path 为 `/api`，生产 Profile 为 `prod`，进程 UID/GID 固定为 `65532:65532`，`/tmp` 与 `/app/uploads` 是仅有的可写运行目录。Kubernetes 继续负责 `readOnlyRootFilesystem: true`、两个显式 volume mount、Secret 注入、探针、资源限制和 30 秒终止宽限期。

## 构建与运行时镜像

Dockerfile 使用两个阶段：

- builder 使用按 OCI index digest 固定的 Maven 3.9 + Eclipse Temurin JDK 17 镜像。先只复制 Maven wrapper 与 POM，通过 BuildKit cache mount 执行依赖预取，再复制源码并运行 Maven package；缓存不进入最终 layer。
- runtime 使用按 OCI index digest 固定的 Distroless Java 17 Debian 13 `nonroot` 镜像。Debian 13 是 Distroless 当前支持 Java 17 的发行线；最终镜像只复制可执行 Spring Boot JAR 和独立健康检查 class，不包含源码、Maven、JDK 编译器、包管理器或 shell。

两个基础镜像的 tag、index digest、上游 registry 与核验命令记录在 Dockerfile OCI annotation、README 和 CI 中。选择 multi-platform index digest，使同一个 Dockerfile 可在 `linux/amd64` 与 `linux/arm64` 解析到对应 manifest；CI 至少构建和检查其运行平台镜像，并通过 Buildx 输出 provenance 与 SBOM。

最终镜像显式声明 `USER 65532:65532`、`EXPOSE 7999`、`SPRING_PROFILES_ACTIVE=prod`、`TMPDIR=/tmp` 和 `FILE_UPLOAD_DIR=/app/uploads`。ENTRYPOINT 使用 exec form 直接运行 Java，不经过 shell。JVM 通过 `-XX:MaxRAMPercentage` 等容器友好参数运行；运行时参数仍可由平台注入的 `JAVA_TOOL_OPTIONS` 扩展。

## 健康检查与停机

Spring Actuator 的 liveness/readiness 端点保持匿名只读：

- `/api/actuator/health/liveness` 表示进程自身能否继续工作，作为 OCI `HEALTHCHECK` 和 Kubernetes liveness contract。
- `/api/actuator/health/readiness` 表示副本能否接流量，由 Kubernetes startup/readiness probe 调用。

Distroless 不携带 curl、wget 或 shell。builder 用 JDK 编译一个无第三方依赖的极小 Java healthcheck class，runtime 直接用 Java 执行。检查器只连接固定的 `127.0.0.1:7999` liveness URL，设置严格连接与读取超时，不跟随重定向，不打印响应体、header 或 Secret，只有 HTTP 2xx 返回 0，其余状态和异常返回非 0。

`application-prod.yml` 明确启用 graceful shutdown，并把 shutdown phase timeout 对齐为 30 秒。Kubernetes 收到 SIGTERM 后，Java 进程直接接收信号并在 Chart 的 `terminationGracePeriodSeconds: 30` 边界内停止接流量、完成在途请求。

## 只读文件系统与持久数据

镜像不在 build 时创建可变数据，也不依赖 root 修复权限。`/tmp` 用于 JVM 临时文件和 multipart 临时数据；Chart 将其挂载为 `emptyDir`。`/app/uploads` 由 `FILE_UPLOAD_DIR` 指向，开发 Chart 可挂 `emptyDir`，production 必须挂预先创建的 RWX PVC。镜像自身不声明 `VOLUME`，避免匿名 volume 绕过平台的显式生命周期管理。

## 合同测试与供应链门禁

仓库保存两层测试：

1. 静态合同测试在不访问 registry 时检查 multi-stage、digest pin、BuildKit Maven cache、non-root、端口、prod Profile、healthcheck、graceful shutdown、`.dockerignore` 和 CI 门禁。
2. 镜像检查脚本用 `docker inspect/create/export` 检查 config 和 rootfs；它不执行默认 ENTRYPOINT、不启动 Spring Boot。检查 UID/GID、端口、环境变量、健康命令，以及 rootfs 中不存在源码、Maven、JDK compiler、常见 shell 和包管理器。

GitHub Actions 使用 Buildx 构建 OCI image、生成构建 provenance/SBOM，运行镜像检查，使用 Syft 额外产出 SPDX JSON artifact，并用 Trivy 扫描 `HIGH,CRITICAL`。扫描结果写入 SARIF/可读日志并在发现对应漏洞时失败，不能通过 `ignore-unfixed`、`continue-on-error` 或零退出码隐藏风险。第三方 Actions 固定到 commit SHA。

## 文档与发布

README 提供本地 build、inspect、SBOM、scan 与 Kubernetes writable-path 合同，不提供会误导为已上线的启动证明。CHANGELOG 在 Unreleased 中记录 production image、健康与供应链能力。Issue #10 更新真实测试证据；分支 `codex/backend-production-image` 以 `codex/contest-core-api` 为 base 创建 stacked Draft PR。
