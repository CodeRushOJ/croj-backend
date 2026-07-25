# TestBundle storage and publication

隐藏测试数据不存入 MySQL，也不通过公开下载 URL 暴露。生产环境使用私有 AWS S3 或 MinIO 桶；数据库 `t_test_bundle` 只保存对象键、SHA-256、压缩包大小和 manifest。

## 配置

```dotenv
TEST_BUNDLE_STORAGE_ENABLED=true
TEST_BUNDLE_S3_BUCKET=coderushoj-test-bundles
TEST_BUNDLE_S3_ENDPOINT=http://minio.storage.svc.cluster.local:9000
TEST_BUNDLE_S3_PATH_STYLE=true
TEST_BUNDLE_MAX_COMPRESSION_RATIO=100
AWS_REGION=us-east-1
AWS_ACCESS_KEY_ID=replace-me
AWS_SECRET_ACCESS_KEY=replace-me
```

AWS S3 可省略 `TEST_BUNDLE_S3_ENDPOINT` 并按部署区域设置 `AWS_REGION`。凭据必须由 Kubernetes Secret 或工作负载身份注入；不要提交到 Git。MinIO 使用 path-style，AWS S3 可以按环境关闭。桶必须保持私有。

## Manifest contract

```json
{
  "totalUncompressedBytes": 8,
  "cases": [
    {
      "id": 1,
      "input": "cases/1.in",
      "output": "cases/1.out",
      "inputBytes": 4,
      "outputBytes": 4
    }
  ]
}
```

`cases` 不得为空，ID 必须为正整数且唯一；输入输出路径必须是 `cases/` 下的安全相对路径且互不重复；声明总字节数必须等于各文件字节数之和并低于配置上限。

`TestBundleService` 是最终信任边界：它流式读取真实 ZIP，拒绝非 ZIP、目录、路径穿越、重复文件、manifest 未声明的文件、缺失文件、实际字节数不符、总解压大小超限和压缩比超限。不能仅依赖上传方提供的 manifest。FPS 解析器还会独立限制题目数、文本、测试点、内嵌图片并禁止 DTD/XXE 和网络访问。

## Publication flow

1. 创建题目与不可变 `ProblemVersion(DRAFT)`。
2. 解析器规范化测试文件并生成 ZIP 与 manifest。
3. `TestBundleService` 校验限制，计算 SHA-256，并写入内容寻址的私有对象。
4. 写入唯一的 `t_test_bundle.problem_version_id` 元数据。
5. `ProblemVersionPublicationService` 锁定版本与测试包，原子更新版本为 `PUBLISHED`、题目 `published_version_id` 和公开状态。

对象写入成功、数据库事务失败时可能留下不可达的内容寻址对象，后续可由 GC 清理；系统不会因此产生已发布但不可判的版本。

## 管理端导入 API

两个接口都要求 `ADMIN` 或 `SUPER_ADMIN`：

```http
POST /api/v1/admin/problem-imports/preflight
Content-Type: multipart/form-data

file=@fps.xml
```

预检按内容识别 FPS XML 或仅包含一个 XML 文件的安全 ZIP，校验后把原始包写入私有 S3/MinIO 暂存区，并在 V8 的 `t_problem_import_job` 保存归属管理员、SHA-256、摘要和 24 小时过期时间。多副本只共享数据库与对象存储，不依赖 Pod 本地文件。

```http
POST /api/v1/admin/problem-imports/{jobId}/commit
```

提交会锁定当前管理员拥有的未过期任务，重新下载、验 SHA-256、解析和校验，然后为每道题创建草稿版本、生成确定性 TestBundle 并通过发布门禁公开。整个数据库步骤在一个事务中完成；重复提交已完成任务会返回相同 `importedCount`，不会重复创建题目。
