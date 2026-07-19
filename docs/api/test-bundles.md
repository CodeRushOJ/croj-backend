# TestBundle storage and publication

隐藏测试数据不存入 MySQL，也不通过公开下载 URL 暴露。生产环境使用私有 AWS S3 或 MinIO 桶；数据库 `t_test_bundle` 只保存对象键、SHA-256、压缩包大小和 manifest。

## 配置

```dotenv
TEST_BUNDLE_STORAGE_ENABLED=true
TEST_BUNDLE_S3_BUCKET=coderushoj-test-bundles
TEST_BUNDLE_S3_ENDPOINT=http://minio.storage.svc.cluster.local:9000
TEST_BUNDLE_S3_PATH_STYLE=true
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

`cases` 不得为空，ID 必须为正整数且唯一；输入输出路径必须是 `cases/` 下的安全相对路径且互不重复；声明总字节数必须等于各文件字节数之和并低于配置上限。FPS/原生包解析器还必须在进入此服务前完成 ZIP entry 数量、单项/总解压大小、压缩比、路径穿越、重复文件、DTD 与 XXE 校验。

## Publication flow

1. 创建题目与不可变 `ProblemVersion(DRAFT)`。
2. 解析器规范化测试文件并生成 ZIP 与 manifest。
3. `TestBundleService` 校验限制，计算 SHA-256，并写入内容寻址的私有对象。
4. 写入唯一的 `t_test_bundle.problem_version_id` 元数据。
5. `ProblemVersionPublicationService` 锁定版本与测试包，原子更新版本为 `PUBLISHED`、题目 `published_version_id` 和公开状态。

对象写入成功、数据库事务失败时可能留下不可达的内容寻址对象，后续可由 GC 清理；系统不会因此产生已发布但不可判的版本。`/api/v1/admin/problem-imports/preflight` 只做解析与校验，`/{jobId}/commit` 必须复用上述步骤并在全部题目成功后返回 `importedCount`。

