# 全局公告 API

全局公告使用 `/v1` 控制器前缀；应用配置了 `/api` context path，因此对外路径为 `/api/v1/...`。响应沿用统一 `Result<T>`。公开读取无需 JWT，管理接口仅允许 `ADMIN` 和 `SUPER_ADMIN`。

## 生命周期和 UTC 规则

数据库持久化 `DRAFT`、`SCHEDULED`、`PUBLISHED`、`ARCHIVED`。`EXPIRED` 是读模型状态：已排期或已发布公告满足 `expiresAt <= now` 时立即生效，不依赖定时任务。排期公告满足 `publishAt <= now` 时读模型状态为 `PUBLISHED`，并进入公开查询。

所有时间字段必须是带时区的 ISO-8601 Instant，例如 `2026-07-20T02:00:00Z`。服务端统一使用 UTC 比较，数据库保存毫秒精度时间。有效可见窗口为：

```text
publishAt <= now && (expiresAt == null || now < expiresAt)
```

`expiresAt` 必须严格晚于 `publishAt`。立即发布时 `publishAt` 是服务端捕获的当前 UTC 时间；客户端不能伪造实际发布时间。

## 公开接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/v1/announcements?page=1&size=20` | 当前可见公告分页；`page >= 1`，`1 <= size <= 100` |
| `GET` | `/api/v1/announcements/current?limit=5` | 首页/导航栏当前公告；`1 <= limit <= 20` |
| `GET` | `/api/v1/announcements/{id}` | 当前可见公告详情；草稿、未来排期、过期和归档均返回 404 |

公开列表先展示置顶公告；置顶公告按 `pinOrder` 升序，随后按发布时间和 ID 倒序。非置顶公告忽略 `pinOrder`，始终按最新发布时间排序。响应不暴露管理员用户 ID 或内部乐观锁版本。

## 管理接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/v1/admin/announcements?page=1&size=20&status=` | 全生命周期分页；状态可选 `DRAFT/SCHEDULED/PUBLISHED/EXPIRED/ARCHIVED` |
| `POST` | `/api/v1/admin/announcements` | 创建草稿 |
| `PUT` | `/api/v1/admin/announcements/{id}` | 更新非归档公告的内容和置顶顺序 |
| `POST` | `/api/v1/admin/announcements/{id}/schedule` | 设置未来发布时间和可选过期时间 |
| `POST` | `/api/v1/admin/announcements/{id}/publish` | 立即发布并设置可选过期时间 |
| `POST` | `/api/v1/admin/announcements/{id}/withdraw` | 把排期/已发布公告撤回草稿并清空发布窗口 |
| `POST` | `/api/v1/admin/announcements/{id}/archive` | 归档；归档后不可修改或重新发布 |

除创建和列表外，所有管理变更必须携带管理列表返回的 `version`：

```http
If-Match: "7"
```

服务端直接以该客户端版本执行 compare-and-set。若另一个管理员已经修改公告，旧版本请求返回 HTTP 409，客户端必须重新获取公告后再决定是否覆盖；服务端不会用刚读取的最新版本替客户端静默覆盖。

草稿请求：

```json
{
  "title": "系统维护通知",
  "contentMarkdown": "判题服务将在维护窗口内滚动升级。",
  "pinned": true,
  "pinOrder": 10
}
```

标题不能为空且最多 200 字符，Markdown 不能为空且最多 100,000 字符，`pinOrder` 范围为 `0..10000`。创建、更新、发布和归档管理员分别写入审计字段。

排期请求：

```json
{
  "publishAt": "2026-07-20T02:00:00Z",
  "expiresAt": "2026-07-21T02:00:00Z"
}
```

立即发布请求；永久有效时使用 `null`：

```json
{
  "expiresAt": null
}
```

## 错误语义

| HTTP | 业务码 | 场景 |
| --- | --- | --- |
| `400` | `40000` | Bean Validation、未知状态过滤器、非法分页类型、非法 JSON/Instant、缺少或错误的 `If-Match` |
| `401` | `40100` | 管理请求没有有效 JWT |
| `403` | `40300` | 登录用户不是管理员 |
| `404` | `40400` | 公告不存在，或公告对公开请求不可见 |
| `409` | `40900` | 乐观锁冲突，客户端应刷新后重试 |
| `422` | `42200` | 非法状态转换、排期不在未来、无效时间窗口 |

现有比赛公告 `/api/v1/contests/{id}/announcements` 保持原契约。V6 表保留 `scope/contest_id`，后续可以在不破坏本接口的情况下统一比赛公告生命周期。
