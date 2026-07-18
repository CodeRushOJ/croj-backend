# 判题任务与结果回传契约

本文定义后端与 `croj-judging` 之间的 v1 内部协议。后端 context path 是 `/api`，因此结果回传地址为：

```text
POST ${BACKEND_INTERNAL_URL}/internal/v1/judge-results
```

`BACKEND_INTERNAL_URL` 必须包含 `/api`，例如 Kubernetes 集群内使用 `http://croj-backend:7999/api`。该接口只供集群内判题服务调用，不应通过公网 Ingress 暴露。

## SubmissionRequested 事件

RocketMQ topic 默认是 `submission-topic`。消息体是版本化 JSON；同一个 Outbox 事件重投时消息体和 `eventId` 保持不变：

```json
{
  "schemaVersion": 1,
  "eventId": "50f75fdf-fdea-473f-a156-bf1ed60acf58",
  "submissionId": 99,
  "attemptNo": 1,
  "problemId": 42,
  "userId": 7,
  "language": "java17"
}
```

消费者必须拒绝不支持的 `schemaVersion`，并把 `eventId` 作为任务侧去重键。`attemptNo` 是提交的判题代次；后续重判必须递增，旧代次结果不能覆盖新代次。

## 鉴权

请求必须携带：

```text
X-CROJ-Service-Token: <JUDGE_RESULT_SERVICE_TOKEN>
```

令牌由环境变量 `JUDGE_RESULT_SERVICE_TOKEN` 注入，UTF-8 长度至少 32 字节。缺失、错误令牌或普通用户 JWT 均返回 HTTP 401。后端使用常量时间字节比较；判题器和后端必须从同一个 Kubernetes Secret 读取令牌。

## 回传请求

```json
{
  "resultId": "result-c320fa18",
  "submissionId": 99,
  "attemptNo": 1,
  "status": "ACCEPTED",
  "exitCode": 0,
  "timeUsedMillis": 12,
  "memoryUsedKb": 2048,
  "stdout": "ok\n",
  "stderr": "",
  "compileError": ""
}
```

终态枚举：`ACCEPTED`、`COMPILE_ERROR`、`WRONG_ANSWER`、`TIME_LIMIT_EXCEEDED`、`MEMORY_LIMIT_EXCEEDED`、`RUNTIME_ERROR`、`SYSTEM_ERROR`。

约束：

- `resultId` 非空且最多 128 字符；重试必须复用同一值和完全相同的语义载荷。
- `submissionId` 为正数，`attemptNo >= 1`。
- `0 <= timeUsedMillis <= 86400000`，`0 <= memoryUsedKb <= 2147483647`。
- `stdout`、`stderr` 各最多 65536 字符，`compileError` 最多 32768 字符。
- `ACCEPTED` 必须满足 `exitCode = 0`；`COMPILE_ERROR` 必须携带非空 `compileError`。

## 响应与重试

首次成功写入：

```json
{"code":20000,"message":"操作成功","data":{"disposition":"APPLIED"},"success":true}
```

相同 `resultId` 和相同载荷重复回传仍返回 HTTP 200，`disposition` 为 `DUPLICATE`，判题器应视为成功并停止重试。

- HTTP 400：必填字段、数值范围或大小校验失败；修正请求，不要原样重试。
- HTTP 401：服务令牌缺失或错误；检查 Secret，不要高频重试。
- HTTP 409：状态与载荷语义冲突（例如 `ACCEPTED` 的退出码非 0）、`resultId` 被不同载荷复用、attempt 过期/已终态或 submission 已终态；停止覆盖并记录告警。
- HTTP 5xx/网络失败：可使用带抖动的指数退避重试，并保持 `resultId` 和载荷不变。

后端在单个事务中登记收件、CAS 更新 attempt、CAS 更新 submission 和累计题目通过数。任一步失败都会回滚，因此并发重复回传最多应用一次。
