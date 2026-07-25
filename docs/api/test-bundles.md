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

v1 是永久兼容合同：只接受 `schemaVersion=1`、`judgeMode=ACM`、`checker=exact|token`。v2 在相同的 limits/cases 基础上增加 OI 正权重计分和隔离特殊判题：

```json
{
  "schemaVersion": 2,
  "judgeMode": "OI",
  "checker": "special",
  "limits": {
    "timeLimitMillis": 1000,
    "memoryLimitMiB": 256
  },
  "totalScore": 100,
  "specialJudge": {
    "language": "cpp",
    "source": "checker/main.cpp",
    "sourceSha256": "<64 lowercase hex characters>",
    "timeLimitMillis": 2000,
    "memoryLimitMiB": 128
  },
  "cases": [
    {
      "id": "subtask-1",
      "input": "cases/1.in",
      "output": "cases/1.out",
      "weight": 30
    },
    {
      "id": "subtask-2",
      "input": "cases/2.in",
      "output": "cases/2.out",
      "weight": 70
    }
  ]
}
```

绑定的不可变 `ProblemVersion` 必须 `projection_complete=1`，而且版本 judgeMode、checker、时间、内存和 OI totalScore 必须与 manifest 完全一致。OI 每个权重必须为正整数，`totalScore` 必须严格等于权重总和；ACM 权重仍必须为 `1` 且不允许 totalScore。special checker 必须固定 `go|cpp|python|java|javascript` 源码、路径和小写 SHA-256，源码最多 4 MiB，独立时间/内存限制为正整数；源码内容、语言和摘要必须与版本快照一致。字符串 ID 必须匹配 `[A-Za-z0-9][A-Za-z0-9._-]{0,63}` 且唯一。所有 artifact 路径必须是清理后不变化的安全相对路径，不要求固定目录前缀，但 `manifest.json` 保留且全部 case/SPJ 路径必须全局唯一。未知字段会被拒绝。

attach 会在访问对象存储之前执行上述双边合同校验；publish 会重新读取数据库中的版本和 manifest 再校验一次。因此权重/总分、SPJ 源码摘要、checker、不完整历史版本或手工插入元数据的任何不一致都会失败关闭。上传阶段通过管理 API 返回稳定 422，发布阶段返回稳定 409；管理端应修正版本配置或创建新的完整草稿，不能重试绕过。

ZIP 根目录必须含且只含一个 `manifest.json`，其规范化结构必须与数据库 `manifest_json` 完全一致；其余文件必须恰好是 manifest 引用的测试输入、输出和可选 checker source。`TestBundleService` 是最终信任边界：它只采用与 Judging Server 相同的中央目录视图，不再依赖可被截断或伪造的 local-header-only 视图，并拒绝加密、symlink/非普通文件、不支持的压缩方法、路径穿越、重复/未声明/缺失文件、manifest 不一致、SPJ 摘要不一致、非法 UTF-8、单文件或总解压大小超限；每个 entry 还会独立校验压缩比、CRC 与声明大小。Backend 最多接受 256 个测试点；manifest 上限为 1 MiB，单 case 和整个 bundle 的展开预算均为 63 MiB，SPJ source 另有 4 MiB 硬上限，压缩比阈值为 200。这为 Judging Server 的 64 MiB batch wire 上限保留协议开销。FPS 解析器还会独立限制题目数、文本、测试点、内嵌图片并禁止 DTD/XXE 和网络访问。

`TestBundleArchiveWriter` 是后端正式 producer：固定 1980-01-01 UTC 时间、Unix `0600` 权限、manifest-first、其余 entry 按路径排序并使用 level-0 DEFLATE。保留 Judging 接受的 DEFLATED wire method，同时避免高重复测试数据被自身 200:1 防炸弹门禁拒绝。因此相同 manifest 和文件映射会产生逐字节相同的 ZIP；数据库 SHA-256、size 与 manifest 必须全部来自这一个 artifact，不能分别手写。

## Publication flow

1. 创建题目与不可变 `ProblemVersion(DRAFT)`，在 `statement_json.tags` 冻结有序 `{id,name,color}` 标签，并将 `projection_complete` 设为真。
2. 解析器或管理员工具规范化测试文件，生成 canonical v1/v2 manifest，并把同一 JSON 写入 ZIP 根目录 `manifest.json`。
3. `TestBundleService` 校验限制，计算 SHA-256，并写入内容寻址的私有对象。
4. 写入唯一的 `t_test_bundle.problem_version_id` 元数据。
5. `ProblemVersionPublicationService` 先锁定题目聚合行，再锁定版本与测试包，重新验证对应 schema 的双边合同，然后原子更新版本为 `PUBLISHED`、题目 `published_version_id`、公开状态和可见标签关系。

对象写入成功、数据库事务失败时可能留下不可达的内容寻址对象，后续可由 GC 清理；系统不会因此产生已发布但不可判的版本。

`t_problem` 始终保存管理端最新草稿，公开详情、题号查询和列表则以
`published_version_id` 指向的 `ProblemVersion` 为展示快照；标题搜索、难度过滤和分页总数也按该快照计算。
因此编辑已发布题目不会提前泄漏新题面、限制或标签，公开标签筛选也只解析已发布版本自己的快照；只有上述原子指针切换才会改变公开内容。新快照包含 `source`、`tags`、`difficulty` 和 `checker`。V11 不会拿当前草稿回填历史版本：自身缺少任一公开投影字段的版本会保持原 JSON、标记为不完整，并从公开题目指针撤下。审计与恢复流程见 [`../migrations/V11-problem-version-projections.md`](../migrations/V11-problem-version-projections.md)。

## 管理端导入 API

以下接口都要求 `ADMIN` 或 `SUPER_ADMIN`。

### 审计不可变 checker source

公共题目 DTO 永远不序列化 `specialJudgeCode`。管理员需要排查某个历史或草稿版本时，必须显式读取该版本的私有判题配置：

```http
GET /api/v1/admin/problems/{problemId}/versions/{versionId}/source
Authorization: Bearer <admin-jwt>
```

响应包含版本号、状态、是否 SPJ、checker source、语言和判题模式。未知版本或版本不属于题目返回 404；普通用户返回 403，匿名请求返回 401。该接口只读不可变版本，不会回退到当前 `t_problem` 草稿。

### 为单个草稿版本上传测试包

创建或编辑题目后，管理端先列出该题目的真实版本；响应按 `versionNo` 从新到旧排列，并返回每个版本的 `versionId`、`state`、TestBundle 元数据和当前强 ETag：

```http
GET /api/v1/admin/problems/{problemId}/versions
```

客户端必须从该列表选择 `DRAFT` 版本，不应猜测版本 ID。未知或已删除题目返回 404。选定版本后读取最新元数据和强 ETag：

```http
GET /api/v1/admin/problems/{problemId}/versions/{versionId}/test-bundle
```

随后携带该 ETag 上传完整的 TestBundle v1 或 v2 ZIP：

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

提交会锁定当前管理员拥有的未过期任务，重新下载、验 SHA-256、解析和校验，然后为每道普通 ACM 题创建草稿版本、通过正式 deterministic writer 生成 v1 TestBundle，并通过发布门禁公开。包含 SPJ/OI 语义的题包必须先由对应格式适配器产生完整 v2 manifest，不能降级为 v1。整个数据库步骤在一个事务中完成；重复提交已完成任务会返回相同 `importedCount`，不会重复创建题目。
