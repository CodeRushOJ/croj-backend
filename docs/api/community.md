# 论坛与题解 API

本文是前后端共同遵循的 v1 契约。服务配置了 `/api` context path，因此控制器内部 `/v1/...` 对外表现为 `/api/v1/...`。响应统一为：

```json
{
  "code": 20000,
  "message": "操作成功",
  "data": {},
  "success": true
}
```

分页接口接受 `current`（从 1 开始）和 `size`。帖子、题解最大 50 条/页，评论最大 100 条/页；响应 `data` 使用 MyBatis-Plus 标准分页字段 `records`、`total`、`current`、`size`、`pages`。

## 鉴权与内容策略

- 所有 GET 接口可匿名读取，只返回 `PUBLISHED` 内容、公开资源的讨论和公开题目的题解。
- POST、DELETE 接口需要 `Authorization: Bearer <JWT>`。
- `authorId` 只从 JWT 获取，请求体不能指定作者。
- 作者可删除自己的帖子、评论和题解；管理员、超级管理员也可删除。
- 删除把状态更新为 `DELETED`，保留审计和恢复空间。
- Markdown 长度：帖子/题解最多 100,000 字符，评论最多 10,000 字符；标题最多 255 字符。
- `content_html` 由后端安全转义生成。前端展示 Markdown 时仍应使用禁用原始 HTML 的渲染器。

## 论坛

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/v1/forum/categories` | 否 | 分类列表 |
| GET | `/api/v1/forum/posts?resourceType=GENERAL&resourceId=&categoryId=&current=1&size=20` | 否 | 指定资源的帖子流，置顶优先 |
| POST | `/api/v1/forum/posts` | 是 | 发布帖子 |
| GET | `/api/v1/forum/posts/{postId}` | 否 | 帖子详情 |
| DELETE | `/api/v1/forum/posts/{postId}` | 是 | 作者或管理员软删除 |
| GET | `/api/v1/forum/posts/{postId}/comments?current=1&size=30` | 否 | 评论按时间正序 |
| POST | `/api/v1/forum/posts/{postId}/comments` | 是 | 评论或回复；锁帖禁止评论 |
| DELETE | `/api/v1/forum/comments/{commentId}` | 是 | 作者或管理员软删除 |

发布帖子：

```json
{
  "categoryId": 2,
  "resourceType": "PROBLEM",
  "resourceId": 1001,
  "title": "线段树懒标记的边界处理",
  "contentMarkdown": "## 思路\n讨论区间更新时的下推时机。"
}
```

`resourceType` 只能是 `GENERAL`、`PROBLEM` 或 `CONTEST`。`GENERAL` 必须省略
`resourceId`；`PROBLEM/CONTEST` 必须提供正整数 `resourceId`。为兼容早期客户端，省略
`resourceType` 等价于 `GENERAL`。题目讨论只允许关联已公开题目；比赛讨论只允许关联已发布且公开的比赛，私有比赛继续使用比赛澄清能力。列表、详情和评论都会重新校验资源可见性，不能通过已知帖子 ID 绕过题目或比赛权限。

发表评论；顶级评论省略 `parentId`，回复时它必须指向同一帖子的已发布评论：

```json
{
  "parentId": 18,
  "contentMarkdown": "这里还需要处理空区间。"
}
```

## 题解

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/v1/problems/{problemId}/solutions?current=1&size=20` | 否 | 已发布题解，精选优先 |
| POST | `/api/v1/problems/{problemId}/solutions` | 是 | 发布题解并绑定当前题目版本 |
| GET | `/api/v1/problems/{problemId}/solutions/{solutionId}` | 否 | 题解详情，校验题目归属 |
| DELETE | `/api/v1/problems/{problemId}/solutions/{solutionId}` | 是 | 作者或管理员软删除 |

发布题解：

```json
{
  "title": "用单调队列优化到 O(n)",
  "contentMarkdown": "## 不变量\n队列中的下标和值始终保持……"
}
```

题目必须公开且已有 `published_version_id`。题解返回该版本 ID，前端可以明确提示题解对应的历史题面版本。

## 错误语义

参数校验失败返回 HTTP 400 和业务码 `40000`。未携带有效 JWT 的写操作返回 HTTP 401。业务级不存在、越权等当前沿用统一响应格式，HTTP 200 下分别返回业务码 `40400`、`40300`；客户端应始终同时检查 HTTP 状态和 `success`。
