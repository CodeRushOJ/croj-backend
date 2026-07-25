# Contest Core 设计

## 目标与边界

本阶段交付免费版比赛核心：比赛管理、报名、赛题可见性、公告、澄清、ACM 榜单和比赛提交归属。继续使用 Spring Boot 模块化单体与 MySQL 真相源，不引入付费、邀请链接、虚拟参赛、hack、气球、OI 榜单或分布式榜单增量计算。

`rule_type` 接受 `ACM` 与 `OI`，但本阶段只实现 ACM 榜单。OI 比赛可以被创建和编排，查询榜单时明确返回“不支持”，不得用 ACM 规则伪装 OI。

## 生命周期与时间

`t_contest.lifecycle` 只持久化 `DRAFT`、`PUBLISHED`、`CANCELLED`，通过数据库 CAS 执行发布和取消。运行阶段由服务端时钟派生，避免定时任务和持久状态漂移：

- `DRAFT`：仅管理员可见和编辑。
- `CANCELLED`：已取消，不允许报名、提交或提问。
- `REGISTRATION`：已发布，当前时间位于报名窗口内且早于开赛。
- `SCHEDULED`：已发布，但不在报名窗口内且尚未开赛。
- `RUNNING`：`starts_at <= now < ends_at` 且未进入冻结。
- `FROZEN`：配置了 `freeze_at` 且 `freeze_at <= now < ends_at`。
- `ENDED`：`now >= ends_at`。

时间约束为 `registration_opens_at <= registration_closes_at <= starts_at < ends_at`。`freeze_at` 可空；非空时必须满足 `starts_at < freeze_at < ends_at`。发布后允许管理员修改描述与公告，但不能修改规则、比赛时间或赛题编排；如需这些变更必须在 DRAFT 完成。

## 数据模型 V5

保持 V1/V2/V3/V4 不变，新增 `V5__contest_core.sql`：

- 扩展 `t_contest`：`description_markdown`、`lifecycle`、`registration_opens_at`、`registration_closes_at`、`updated_at`，并增加公开列表与生命周期索引。现有行映射为 `DRAFT`，报名窗口默认使用 `created_at` 到 `starts_at`。
- 扩展 `t_submission`：nullable `contest_id`，增加 `(contest_id,user_id,problem_id,create_time,status)` 查询索引。应用层保证其引用有效比赛；不添加跨环境难升级的物理外键。
- `t_contest_registration`：`contest_id,user_id,status,registered_at,updated_at,managed_by`；唯一键 `(contest_id,user_id)`。`REGISTERED/CANCELLED` 使用 upsert/CAS，保证并发重复报名只形成一条记录。
- `t_contest_announcement`：比赛公告、发布时间、发布管理员。
- `t_contest_clarification`：提问者、题目可选引用、问题、状态与创建时间。
- `t_contest_clarification_reply`：官方回复、回复管理员、`is_public` 与时间。默认仅提问者和管理员可读；任一公开回复使该澄清对已报名参赛者可见。
- `t_contest_scoreboard_snapshot`：`contest_id`、`view_type`、`cutoff_at`、`source_version`、JSON payload 与生成时间；唯一键覆盖比赛、视图和 cutoff。它是可丢弃缓存，不参与正确性判断。

## 权限与可见性

- 匿名用户只能分页读取已发布的 PUBLIC 比赛、详情、已开始的赛题、公告、公开澄清和公共榜单。
- PUBLIC 比赛允许登录用户在报名窗口内自行报名或取消；开赛后不能取消。
- PRIVATE 比赛不开放普通用户自助报名。管理员通过管理 API 添加或移除参赛者；已报名者可读取详情和参赛内容。
- 赛题在开赛前仅管理员可见；比赛期间仅已报名者和管理员可见；比赛结束后 PUBLIC 比赛允许匿名读取。
- 提问仅允许比赛进行中的已报名者；回复与公开标记仅管理员可执行。
- 管理 API 使用 `ADMIN/SUPER_ADMIN` 方法级权限。分页 size 上限为 100，默认 20。

## API

公共与登录 API：

- `GET /v1/contests?page=1&size=20`：公开比赛分页。
- `GET /v1/contests/{id}`：比赛详情和服务端派生 phase。
- `GET /v1/contests/{id}/me`：个人报名状态。
- `POST /v1/contests/{id}/registrations`、`DELETE /v1/contests/{id}/registrations/me`：PUBLIC 自助报名/取消。
- `GET /v1/contests/{id}/problems`：按 label 返回可见赛题。
- `GET /v1/contests/{id}/announcements`：公告。
- `GET/POST /v1/contests/{id}/clarifications`：可见澄清/提问。
- `GET /v1/contests/{id}/scoreboard`：ACM 公共榜。

管理 API：

- `POST/PUT/DELETE /v1/admin/contests`：创建、更新草稿、取消比赛。
- `PUT /v1/admin/contests/{id}/problems`：原子替换赛题编排，校验 label 唯一及题目版本。
- `POST /v1/admin/contests/{id}/publish`：校验时间、规则和至少一道赛题后 CAS 发布。
- `POST/DELETE /v1/admin/contests/{id}/registrations/{userId}`：PRIVATE/PUBLIC 管理报名。
- `POST /v1/admin/contests/{id}/announcements`：发布公告。
- `POST /v1/admin/contests/{id}/clarifications/{clarificationId}/replies`：官方回复及公开标记。
- `GET /v1/admin/contests/{id}/scoreboard`：不受冻结 cutoff 影响的实时榜。

所有响应继续使用现有 `Result<T>` 包装。资源缺失、权限不足、时间窗口冲突和重复状态迁移使用现有业务异常体系，并保持 HTTP 安全层语义。

## 比赛提交与判题边界

`SubmissionDTO` 增加 nullable `contestId`。设置时，后端必须在同一事务前验证比赛已发布、当前处于 `RUNNING/FROZEN`、用户已报名、题目属于比赛，并把对应 `problem_version_id` 与 `contest_id` 写入提交。客户端不能指定题目版本或计分数据。

`SubmissionRequested` schema v1 和 `/internal/v1/judge-results` v1 保持不变。判题器只需要 submission ID；结果落入 `t_submission` 后，榜单从数据库终态重建。未来 OI 分数回传需要独立 Issue 和版本化协议，不在 v1 请求中偷偷增加 `score`。

## ACM 榜单

数据源仅为比赛时间范围内、带对应 `contest_id` 的提交：

- 首个 ACCEPTED 之前的选手错误状态 `COMPILE_ERROR/WRONG_ANSWER/TIME_LIMIT_EXCEEDED/MEMORY_LIMIT_EXCEEDED/RUNTIME_ERROR` 每次罚 20 分钟。
- `PENDING` 与 `SYSTEM_ERROR` 不计罚时；首 AC 后的提交完全忽略。
- 单题罚时为首 AC 距开赛的整分钟数加错误罚时；未 AC 题只有错误次数，不进入总罚时。
- 排名依次按 solved 降序、penalty 升序、lastAcceptedAt 升序、userId 升序。
- 每题全场最早 AC 标记 `firstAccepted=true`，相同毫秒用 submission ID 决定唯一首 AC。
- 冻结期公共榜以 `freeze_at` 为 cutoff；管理员榜始终实时。比赛结束后公共榜自动解冻并使用 `ends_at` cutoff。

查询可以用 snapshot 命中，但必须校验 cutoff 和 source version；任何不确定性都回退到从 `t_submission` 重建。

## 测试与验收

- 单元测试覆盖 phase 派生、时间校验、ACM 计分、首 AC、错误罚时和冻结 cutoff。
- H2 集成覆盖匿名/登录/管理员权限、重复并发报名、PRIVATE 管理报名、赛题时间边界、比赛提交校验、澄清可见性与分页上限。
- MySQL 8.4 验证全新 V1→V5，以及已有 V1→V4 数据升级 V5；验证索引、现有比赛迁移和报名唯一键。
- 全量 Maven 测试保持通过；README、API 文档和 CHANGELOG 记录真实能力、部署变量与协议边界。
