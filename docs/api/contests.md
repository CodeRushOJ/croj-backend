# 竞赛核心 API

竞赛接口使用 `/v1` 前缀，响应沿用统一的 `Result<T>` 包装。公开比赛列表、详情、赛时题目、公告、公开澄清回复和允许展示的榜单可以匿名读取；报名、个人状态、提问和提交需要 JWT；`/v1/admin/contests/**` 仅允许管理员或超级管理员。

## 状态与时间边界

数据库只持久化可审计的生命周期 `DRAFT`、`PUBLISHED`、`CANCELLED`。`REGISTRATION`、`SCHEDULED`、`RUNNING`、`FROZEN`、`ENDED` 均由当前时间和赛程即时推导，避免定时任务漏跑造成状态漂移。

赛程必须满足：

```text
registrationOpensAt <= registrationClosesAt <= startsAt
startsAt < endsAt
startsAt < freezeAt < endsAt       # freezeAt 可省略
```

计分提交采用左闭右开区间 `[startsAt, cutoffAt)`。因此恰好发生在封榜时间的提交不会进入公开冻结榜，但管理员实时榜仍可看到。公开榜只在 `RUNNING`、`FROZEN`、`ENDED` 可读，赛前不会泄漏参赛者或题目。

## 用户接口

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/v1/contests?page=1&size=20` | 匿名 | 公开且已发布的比赛；`size` 最大 100 |
| `GET` | `/v1/contests/{id}` | 按可见性 | 比赛详情和派生阶段 |
| `GET` | `/v1/contests/{id}/me` | JWT | 当前用户报名状态 |
| `POST` | `/v1/contests/{id}/registrations` | JWT | 公开比赛在报名期自助报名，幂等 |
| `DELETE` | `/v1/contests/{id}/registrations/me` | JWT | 开赛前取消报名 |
| `GET` | `/v1/contests/{id}/problems` | 按赛时 | 赛时题目及固定题目版本 |
| `GET` | `/v1/contests/{id}/announcements` | 按可见性 | 公告列表 |
| `GET` | `/v1/contests/{id}/clarifications` | 按可见性 | 本人问题、管理员视图或带公开回复的问题 |
| `POST` | `/v1/contests/{id}/clarifications` | JWT+报名 | 赛中提问 |
| `GET` | `/v1/contests/{id}/scoreboard` | 按赛时 | ACM/OI 公开、冻结或最终榜 |

`PRIVATE` 比赛不允许普通用户自助报名，只能由管理员维护名单。澄清默认只对提问者和管理员可见；管理员可以把某条回复标为公开，此时其他符合比赛可见性要求的用户能看到问题和公开回复，私密回复仍不会泄漏。

提交接口沿用现有提交端点，请求增加可选字段：

```json
{
  "problemId": 42,
  "contestId": 5,
  "language": "java17",
  "code": "class Main {}"
}
```

存在 `contestId` 时，后端校验比赛正在进行、用户仍在有效名单、题目属于该比赛，并把编排时的不可变 `problemVersionId` 固定到提交记录。普通题库提交同样会固定题目当前的 `published_version_id`；该版本必须为 `PUBLISHED` 且已经存在 `t_test_bundle`，否则以业务错误拒绝，避免把无法判题的任务投递出去。向判题器投递的 `SubmissionRequested` v1 事件结构保持不变。

## 管理接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/v1/admin/contests` | 创建草稿 |
| `PUT` | `/v1/admin/contests/{id}` | 更新草稿及赛程 |
| `PUT` | `/v1/admin/contests/{id}/problems` | 编排最多 100 道已发布题目版本 |
| `POST` | `/v1/admin/contests/{id}/publish` | 至少有一道题后发布 |
| `DELETE` | `/v1/admin/contests/{id}` | 取消比赛 |
| `POST/DELETE` | `/v1/admin/contests/{id}/registrations/{userId}` | 维护有效用户名单 |
| `POST` | `/v1/admin/contests/{id}/announcements` | 发布公告 |
| `POST` | `/v1/admin/contests/{id}/clarifications/{clarificationId}/replies` | 私密或公开回复 |
| `GET` | `/v1/admin/contests/{id}/scoreboard` | 不封榜的实时管理榜 |

题目编排和发布在同一个比赛聚合行上加数据库行锁，防止发布与 `DELETE/INSERT` 编排交叉。题目 ID、版本 ID 和标签均须唯一；版本必须属于对应题目、处于 `PUBLISHED` 不可变状态且已经绑定测试数据包。自助和托管报名都只接受未禁用、未删除的真实用户。

## ACM 与 OI 计分

ACM 榜按已解题数降序、罚时升序、最后一次 AC 时间升序排序。每道题首次 AC 前的编译错误、答案错误、超时、内存超限和运行错误各罚 20 分钟；pending、系统错误、AC 后提交和非参赛者提交不计入榜单。全场 first AC 按提交时间和提交 ID 确定。

OI 榜对每位参赛者、每道题取截止时刻前的历史最高分；相同最高分保留最早达到该分数的提交。单次分数必须在 `0..contest_problem.score` 内，否则榜单拒绝不一致的数据。排名依次比较总分降序、获得正分的题数降序、最后一次达到各题最高分的时间升序，最后以用户 ID 保证确定性。公开冻结榜不包含恰好发生在 `freezeAt` 或之后的提分，管理员榜继续实时显示。

响应顶层总是包含 `ruleType`。每行只公开 `userId` 和当前 `username`，不包含邮箱等私有资料。ACM 行使用 `solved`、`penaltyMinutes` 和 `lastAcceptedAt`；OI 行使用 `totalScore`、`scoredProblems` 和 `lastImprovedAt`，顶层 `maximumScore` 是比赛各题固定分值之和。每道 OI 题同时返回 `maximumScore`、`score`、产生当前最高分的 `submissionId` 与 `achievedAt`。另一赛制不适用的字段为 `null`，客户端不得把 OI 分数伪装成 ACM 解题数。

提交事实是唯一真相源。冻结榜和最终榜可以写入 `t_contest_scoreboard_snapshot` 作为可丢弃缓存；命中条件同时包含固定截止时间和由有效报名、未删除提交的状态与分数、固定题目版本及分值计算的 `sourceVersion`。版本不一致或 JSON 无法读取时直接重算。实时公开榜和管理员榜不缓存。
