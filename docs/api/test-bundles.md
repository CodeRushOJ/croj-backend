# TestBundle storage and publication

隐藏测试数据不存入 MySQL，也不通过公开下载 URL 暴露。生产环境使用私有 AWS S3 或 MinIO 桶；数据库 `t_test_bundle` 只保存对象键、SHA-256、压缩包大小和 manifest。

## 配置

```dotenv
TEST_BUNDLE_STORAGE_ENABLED=true
TEST_BUNDLE_S3_BUCKET=coderushoj-test-bundles
TEST_BUNDLE_S3_ENDPOINT=http://minio.storage.svc.cluster.local:9000
TEST_BUNDLE_S3_PATH_STYLE=true
TEST_BUNDLE_MAX_ARCHIVE_BYTES=268435456
TEST_BUNDLE_MAX_MANIFEST_BYTES=1048576
TEST_BUNDLE_MAX_CASE_BYTES=66060288
TEST_BUNDLE_MAX_UNCOMPRESSED_BYTES=66060288
TEST_BUNDLE_MAX_CASES=256
TEST_BUNDLE_MAX_COMPRESSION_RATIO=200
AWS_REGION=us-east-1
AWS_ACCESS_KEY_ID=replace-me
AWS_SECRET_ACCESS_KEY=replace-me
```

AWS S3 可省略 `TEST_BUNDLE_S3_ENDPOINT` 并按部署区域设置 `AWS_REGION`。凭据必须由 Kubernetes Secret 或工作负载身份注入；不要提交到 Git。MinIO 使用 path-style，AWS S3 可以按环境关闭。桶必须保持私有。

## Manifest contract

```json
{
  "schemaVersion": 1,
  "judgeMode": "ACM",
  "checker": "exact",
  "limits": {
    "timeLimitMillis": 1000,
    "memoryLimitMiB": 256
  },
  "cases": [
    {
      "id": "1",
      "input": "cases/1.in",
      "output": "cases/1.out",
      "weight": 1
    }
  ]
}
```

这是 Backend 与 Judging Server 共用的严格 v1 契约：只接受 `schemaVersion=1`、`judgeMode=ACM`、`checker=exact|token`；`limits` 的时间和内存必须为正整数，并与绑定的不可变 `ProblemVersion.limits_json` 完全一致；`cases` 不得为空，字符串 ID 必须匹配 `[A-Za-z0-9][A-Za-z0-9._-]{0,63}` 且唯一；ACM 权重必须为 `1`；输入输出路径必须是 `cases/` 下的安全相对路径且互不重复。未知字段会被拒绝。

ZIP 根目录必须含且只含一个 `manifest.json`，其规范化结构必须与数据库 `manifest_json` 完全一致；其余文件必须恰好是 manifest 引用的测试输入输出。`TestBundleService` 是最终信任边界：它只采用与 Judging Server 相同的中央目录视图，不再依赖可被截断或伪造的 local-header-only 视图，并拒绝加密、symlink/非普通文件、不支持的压缩方法、路径穿越、重复/未声明/缺失文件、manifest 不一致、非法 UTF-8、单文件或总解压大小超限；每个 entry 还会独立校验压缩比、CRC 与声明大小。Backend 最多接受 256 个测试点；manifest 上限为 1 MiB，单文件和整个 bundle 的展开预算均为 63 MiB，压缩比阈值为 200。这为 Judging Server 的 64 MiB batch wire 上限保留至少 1 MiB 给源码和协议开销。FPS 解析器还会独立限制题目数、文本、测试点、内嵌图片并禁止 DTD/XXE 和网络访问。

## Publication flow

1. 创建题目与不可变 `ProblemVersion(DRAFT)`。
2. 解析器规范化测试文件，生成 canonical v1 manifest，并把同一 JSON 写入 ZIP 根目录 `manifest.json`。
3. `TestBundleService` 校验限制，计算 SHA-256，并写入内容寻址的私有对象。
4. 写入唯一的 `t_test_bundle.problem_version_id` 元数据。
5. `ProblemVersionPublicationService` 先锁定题目聚合行，再锁定版本与测试包，原子更新版本为 `PUBLISHED`、题目 `published_version_id` 和公开状态。

对象写入成功、数据库事务失败时可能留下不可达的内容寻址对象，后续可由 GC 清理；系统不会因此产生已发布但不可判的版本。

`t_problem` 始终保存管理端最新草稿，公开详情、题号查询和列表则以
`published_version_id` 指向的 `ProblemVersion` 为展示快照；标题搜索、难度过滤和分页总数也按该快照计算。
因此编辑已发布题目不会提前泄漏新题面或限制，只有上述原子指针切换才会改变公开内容。新快照包含
`source` 与 `difficulty`，V11 会为 V3 等历史版本补齐这两个投影字段。

## 管理端导入 API

以下接口都要求 `ADMIN` 或 `SUPER_ADMIN`。

### 为单个草稿版本上传测试包

创建或编辑题目后，管理端先列出该题目的真实版本；响应按 `versionNo` 从新到旧排列，并返回每个版本的 `versionId`、`state`、TestBundle 元数据和当前强 ETag：

```http
GET /api/v1/admin/problems/{problemId}/versions
```

客户端必须从该列表选择 `DRAFT` 版本，不应猜测版本 ID。未知或已删除题目返回 404。选定版本后读取最新元数据和强 ETag：

```http
GET /api/v1/admin/problems/{problemId}/versions/{versionId}/test-bundle
```

随后携带该 ETag 上传完整的 TestBundle v1 ZIP：

```http
PUT /api/v1/admin/problems/{problemId}/versions/{versionId}/test-bundle
If-Match: "tb-v1-{versionId}-DRAFT-none"
Content-Type: multipart/form-data

file=@test-bundle.zip
```

验证及私有对象写入成功后，响应会返回包含 SHA-256 的新 ETag。发布必须使用这个新值：

```http
POST /api/v1/admin/problems/{problemId}/versions/{versionId}/test-bundle/publish
If-Match: "tb-v1-{versionId}-DRAFT-{sha256}"
```

`If-Match` 是必需的并且只接受单个强实体标签；缺失返回 428，格式非法返回 400，版本或测试包已被其他管理员修改返回 412。只有 `DRAFT` 版本可以接收测试包，且没有已验证 TestBundle 的版本不能发布。上传体和 ZIP 内容继续受本页所述硬限制约束；解析器内部细节不会暴露给 HTTP 客户端。

### 批量题目包导入

```http
POST /api/v1/admin/problem-imports/preflight
Content-Type: multipart/form-data

file=@fps.xml
```

预检按内容识别 FPS XML 或仅包含一个 XML 文件的安全 ZIP，并在返回 `READY` 前检查 256-case 上限及隐藏输入输出容量；校验后把原始包写入私有 S3/MinIO 暂存区，并在 V8 的 `t_problem_import_job` 保存归属管理员、SHA-256、摘要和 24 小时过期时间。多副本只共享数据库与对象存储，不依赖 Pod 本地文件。

```http
POST /api/v1/admin/problem-imports/{jobId}/commit
```

提交会锁定当前管理员拥有的未过期任务，重新下载、验 SHA-256、解析和校验，然后为每道题创建草稿版本、以 `STORED` entry 生成不会触发自身压缩比门禁的确定性 TestBundle，并通过发布门禁公开。整个数据库步骤在一个事务中完成；重复提交已完成任务会返回相同 `importedCount`，不会重复创建题目。
