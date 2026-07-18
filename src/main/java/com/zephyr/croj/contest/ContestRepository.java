package com.zephyr.croj.contest;

import com.zephyr.croj.model.dto.contest.ContestRequests;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ContestRepository {
    private final JdbcTemplate jdbc;

    public Optional<ContestRecord> findById(long contestId) {
        return jdbc.query(
                        """
                        SELECT id,title,description_markdown,rule_type,visibility,lifecycle,
                               registration_opens_at,registration_closes_at,starts_at,freeze_at,ends_at,
                               created_by,created_at,updated_at
                        FROM t_contest WHERE id=?
                        """,
                        this::mapContest,
                        contestId)
                .stream()
                .findFirst();
    }

    /** Locks the contest aggregate so problem arrangement and publication cannot cross. */
    public Optional<ContestRecord> findByIdForUpdate(long contestId) {
        return jdbc.query(
                        """
                        SELECT id,title,description_markdown,rule_type,visibility,lifecycle,
                               registration_opens_at,registration_closes_at,starts_at,freeze_at,ends_at,
                               created_by,created_at,updated_at
                        FROM t_contest WHERE id=? FOR UPDATE
                        """,
                        this::mapContest,
                        contestId)
                .stream()
                .findFirst();
    }

    public List<ContestRecord> listPublishedPublic(long offset, int size) {
        return jdbc.query(
                """
                SELECT id,title,description_markdown,rule_type,visibility,lifecycle,
                       registration_opens_at,registration_closes_at,starts_at,freeze_at,ends_at,
                       created_by,created_at,updated_at
                FROM t_contest
                WHERE visibility='PUBLIC' AND lifecycle='PUBLISHED'
                ORDER BY starts_at DESC,id DESC LIMIT ? OFFSET ?
                """,
                this::mapContest,
                size,
                offset);
    }

    public long countPublishedPublic() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_contest WHERE visibility='PUBLIC' AND lifecycle='PUBLISHED'",
                Long.class);
    }

    public void register(long contestId, long userId, Long managedBy) {
        jdbc.update(
                """
                INSERT INTO t_contest_registration
                  (contest_id,user_id,status,registered_at,updated_at,managed_by)
                VALUES (?,?,'REGISTERED',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3),?)
                ON DUPLICATE KEY UPDATE status='REGISTERED',updated_at=CURRENT_TIMESTAMP(3),managed_by=VALUES(managed_by)
                """,
                contestId,
                userId,
                managedBy);
    }

    public int cancelRegistration(long contestId, long userId, Long managedBy) {
        return jdbc.update(
                """
                UPDATE t_contest_registration
                SET status='CANCELLED',updated_at=CURRENT_TIMESTAMP(3),managed_by=?
                WHERE contest_id=? AND user_id=? AND status='REGISTERED'
                """,
                managedBy,
                contestId,
                userId);
    }

    public boolean isRegistered(long contestId, long userId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_contest_registration WHERE contest_id=? AND user_id=? AND status='REGISTERED'",
                Integer.class,
                contestId,
                userId);
        return count != null && count > 0;
    }

    public long create(ContestRequests.Upsert request, long createdBy) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO t_contest
                      (title,description_markdown,rule_type,visibility,lifecycle,
                       registration_opens_at,registration_closes_at,starts_at,freeze_at,ends_at,created_by)
                    VALUES (?,?,?,?,'DRAFT',?,?,?,?,?,?)
                    """,
                    new String[] {"id"});
            statement.setString(1, request.title().trim());
            statement.setString(2, request.descriptionMarkdown());
            statement.setString(3, request.ruleType().trim().toUpperCase());
            statement.setString(4, request.visibility().trim().toUpperCase());
            statement.setTimestamp(5, Timestamp.from(request.registrationOpensAt()));
            statement.setTimestamp(6, Timestamp.from(request.registrationClosesAt()));
            statement.setTimestamp(7, Timestamp.from(request.startsAt()));
            statement.setTimestamp(8, request.freezeAt() == null ? null : Timestamp.from(request.freezeAt()));
            statement.setTimestamp(9, Timestamp.from(request.endsAt()));
            statement.setLong(10, createdBy);
            return statement;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    public int updateDraft(long contestId, ContestRequests.Upsert request) {
        return jdbc.update(
                """
                UPDATE t_contest SET title=?,description_markdown=?,rule_type=?,visibility=?,
                  registration_opens_at=?,registration_closes_at=?,starts_at=?,freeze_at=?,ends_at=?
                WHERE id=? AND lifecycle='DRAFT'
                """,
                request.title().trim(),
                request.descriptionMarkdown(),
                request.ruleType().trim().toUpperCase(),
                request.visibility().trim().toUpperCase(),
                Timestamp.from(request.registrationOpensAt()),
                Timestamp.from(request.registrationClosesAt()),
                Timestamp.from(request.startsAt()),
                request.freezeAt() == null ? null : Timestamp.from(request.freezeAt()),
                Timestamp.from(request.endsAt()),
                contestId);
    }

    public int transitionLifecycle(long contestId, String expected, String target) {
        return jdbc.update(
                "UPDATE t_contest SET lifecycle=? WHERE id=? AND lifecycle=?",
                target,
                contestId,
                expected);
    }

    public int cancel(long contestId) {
        return jdbc.update(
                "UPDATE t_contest SET lifecycle='CANCELLED' WHERE id=? AND lifecycle IN ('DRAFT','PUBLISHED')",
                contestId);
    }

    public void replaceProblems(long contestId, List<ContestRequests.ProblemItem> problems) {
        jdbc.update("DELETE FROM t_contest_problem WHERE contest_id=?", contestId);
        jdbc.batchUpdate(
                "INSERT INTO t_contest_problem(contest_id,problem_id,problem_version_id,label,score) VALUES (?,?,?,?,?)",
                problems,
                problems.size(),
                (statement, item) -> {
                    statement.setLong(1, contestId);
                    statement.setLong(2, item.problemId());
                    statement.setLong(3, item.problemVersionId());
                    statement.setString(4, item.label().trim().toUpperCase());
                    statement.setInt(5, item.score());
                });
    }

    public int problemCount(long contestId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_contest_problem WHERE contest_id=?", Integer.class, contestId);
    }

    public List<ContestProblem> listProblems(long contestId) {
        return jdbc.query(
                """
                SELECT contest_id,problem_id,problem_version_id,label,score
                FROM t_contest_problem WHERE contest_id=? ORDER BY label,problem_id
                """,
                (result, row) -> new ContestProblem(
                        result.getLong("problem_id"),
                        result.getLong("problem_version_id"),
                        result.getString("label"),
                        result.getInt("score")),
                contestId);
    }

    public Optional<Long> problemVersion(long contestId, long problemId) {
        return jdbc.query(
                        "SELECT problem_version_id FROM t_contest_problem WHERE contest_id=? AND problem_id=?",
                        (result, row) -> result.getLong(1),
                        contestId,
                        problemId)
                .stream()
                .findFirst();
    }

    public boolean isAvailableProblemVersion(long problemId, long problemVersionId) {
        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM t_problem p
                JOIN t_problem_version pv ON pv.problem_id=p.id
                JOIN t_test_bundle tb ON tb.problem_version_id=pv.id
                WHERE p.id=? AND pv.id=? AND pv.state='PUBLISHED' AND p.is_deleted=0
                """,
                Integer.class,
                problemId,
                problemVersionId);
        return count != null && count == 1;
    }

    public long addAnnouncement(long contestId, ContestRequests.Announcement request, long administratorId) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO t_contest_announcement(contest_id,title,content_markdown,published_by) VALUES (?,?,?,?)",
                    new String[] {"id"});
            statement.setLong(1, contestId);
            statement.setString(2, request.title().trim());
            statement.setString(3, request.contentMarkdown());
            statement.setLong(4, administratorId);
            return statement;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    public List<AnnouncementView> listAnnouncements(long contestId) {
        return jdbc.query(
                """
                SELECT id,title,content_markdown,published_by,published_at
                FROM t_contest_announcement WHERE contest_id=? ORDER BY published_at DESC,id DESC
                """,
                (result, row) -> new AnnouncementView(
                        result.getLong("id"),
                        result.getString("title"),
                        result.getString("content_markdown"),
                        result.getLong("published_by"),
                        result.getTimestamp("published_at").toInstant()),
                contestId);
    }

    public long addClarification(long contestId, long userId, ContestRequests.Clarification request) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO t_contest_clarification(contest_id,problem_id,asked_by,question_markdown) VALUES (?,?,?,?)",
                    new String[] {"id"});
            statement.setLong(1, contestId);
            if (request.problemId() == null) statement.setNull(2, java.sql.Types.BIGINT);
            else statement.setLong(2, request.problemId());
            statement.setLong(3, userId);
            statement.setString(4, request.questionMarkdown());
            return statement;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    public long addClarificationReply(
            long clarificationId, ContestRequests.ClarificationReply request, long administratorId) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO t_contest_clarification_reply(clarification_id,reply_markdown,replied_by,is_public) VALUES (?,?,?,?)",
                    new String[] {"id"});
            statement.setLong(1, clarificationId);
            statement.setString(2, request.replyMarkdown());
            statement.setLong(3, administratorId);
            statement.setBoolean(4, request.publicReply());
            return statement;
        }, keys);
        jdbc.update("UPDATE t_contest_clarification SET status='ANSWERED' WHERE id=?", clarificationId);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    public boolean clarificationBelongsToContest(long clarificationId, long contestId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_contest_clarification WHERE id=? AND contest_id=?",
                Integer.class,
                clarificationId,
                contestId);
        return count != null && count == 1;
    }

    public boolean activeUserExists(long userId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_user WHERE id=? AND status=0 AND is_deleted=0",
                Integer.class,
                userId);
        return count != null && count == 1;
    }

    public List<ClarificationView> listClarifications(
            long contestId, Long viewerId, boolean administrator, boolean mayReadPublicReplies) {
        List<ClarificationView> questions = jdbc.query(
                """
                SELECT c.id,c.problem_id,c.asked_by,c.question_markdown,c.status,c.created_at
                FROM t_contest_clarification c
                WHERE c.contest_id=? AND (
                  ?=TRUE OR c.asked_by=? OR (?=TRUE AND EXISTS (
                    SELECT 1 FROM t_contest_clarification_reply r
                    WHERE r.clarification_id=c.id AND r.is_public=1)))
                ORDER BY c.created_at DESC,c.id DESC
                """,
                (result, row) -> new ClarificationView(
                        result.getLong("id"),
                        nullableLong(result, "problem_id"),
                        result.getLong("asked_by"),
                        result.getString("question_markdown"),
                        result.getString("status"),
                        result.getTimestamp("created_at").toInstant(),
                        List.of()),
                contestId,
                administrator,
                viewerId == null ? -1L : viewerId,
                mayReadPublicReplies);
        return questions.stream()
                .map(question -> new ClarificationView(
                        question.id(),
                        question.problemId(),
                        question.askedBy(),
                        question.questionMarkdown(),
                        question.status(),
                        question.createdAt(),
                        listReplies(question.id(), administrator || question.askedBy() == (viewerId == null ? -1L : viewerId))))
                .toList();
    }

    private List<ReplyView> listReplies(long clarificationId, boolean mayReadPrivate) {
        return jdbc.query(
                """
                SELECT id,reply_markdown,replied_by,is_public,created_at
                FROM t_contest_clarification_reply
                WHERE clarification_id=? AND (?=TRUE OR is_public=1)
                ORDER BY created_at,id
                """,
                (result, row) -> new ReplyView(
                        result.getLong("id"),
                        result.getString("reply_markdown"),
                        result.getLong("replied_by"),
                        result.getBoolean("is_public"),
                        result.getTimestamp("created_at").toInstant()),
                clarificationId,
                mayReadPrivate);
    }

    public List<Long> registeredUsers(long contestId) {
        return jdbc.query(
                "SELECT user_id FROM t_contest_registration WHERE contest_id=? AND status='REGISTERED' ORDER BY user_id",
                (result, row) -> result.getLong(1),
                contestId);
    }

    public List<AcmScoreboardCalculator.SubmissionFact> submissionFacts(long contestId, Instant cutoffExclusive) {
        return jdbc.query(
                """
                SELECT id,user_id,problem_id,status,create_time FROM t_submission
                WHERE contest_id=? AND create_time<? ORDER BY create_time,id
                """,
                (result, row) -> new AcmScoreboardCalculator.SubmissionFact(
                        result.getLong("id"),
                        result.getLong("user_id"),
                        result.getLong("problem_id"),
                        result.getInt("status"),
                        result.getTimestamp("create_time").toInstant()),
                contestId,
                Timestamp.from(cutoffExclusive));
    }

    public String scoreboardSourceVersion(long contestId, Instant cutoffExclusive) {
        String submissions = jdbc.queryForObject(
                """
                SELECT COUNT(*) AS row_count,MAX(update_time) AS max_updated_at,
                       COALESCE(SUM(id*17+status*31),0) AS status_checksum
                FROM t_submission WHERE contest_id=? AND create_time<? AND status BETWEEN 1 AND 7
                """,
                (result, row) -> {
                    Timestamp updatedAt = result.getTimestamp("max_updated_at");
                    long updatedMillis = updatedAt == null ? 0L : updatedAt.toInstant().toEpochMilli();
                    return result.getLong("row_count") + ":" + updatedMillis + ":"
                            + result.getLong("status_checksum");
                },
                contestId,
                Timestamp.from(cutoffExclusive));
        String registrations = jdbc.queryForObject(
                """
                SELECT COUNT(*) AS row_count,MAX(updated_at) AS max_updated_at,
                       COALESCE(SUM(user_id*17 + CASE WHEN status='REGISTERED' THEN 31 ELSE 47 END),0)
                         AS roster_checksum
                FROM t_contest_registration WHERE contest_id=?
                """,
                (result, row) -> {
                    Timestamp updatedAt = result.getTimestamp("max_updated_at");
                    long updatedMillis = updatedAt == null ? 0L : updatedAt.toInstant().toEpochMilli();
                    return result.getLong("row_count") + ":" + updatedMillis + ":"
                            + result.getLong("roster_checksum");
                },
                contestId);
        return submissions + ":r:" + registrations;
    }

    public Optional<String> findScoreboardSnapshot(
            long contestId, String viewType, Instant cutoffExclusive, String sourceVersion) {
        return jdbc.query(
                        """
                        SELECT payload FROM t_contest_scoreboard_snapshot
                        WHERE contest_id=? AND view_type=? AND cutoff_at=? AND source_version=?
                        """,
                        (result, row) -> result.getString("payload"),
                        contestId,
                        viewType,
                        Timestamp.from(cutoffExclusive),
                        sourceVersion)
                .stream()
                .findFirst();
    }

    public void saveScoreboardSnapshot(
            long contestId,
            String viewType,
            Instant cutoffExclusive,
            String sourceVersion,
            String payload) {
        jdbc.update(
                """
                INSERT INTO t_contest_scoreboard_snapshot
                  (contest_id,view_type,cutoff_at,source_version,payload,generated_at)
                VALUES (?,?,?,?,?,CURRENT_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE source_version=VALUES(source_version),payload=VALUES(payload),
                  generated_at=CURRENT_TIMESTAMP(3)
                """,
                contestId,
                viewType,
                Timestamp.from(cutoffExclusive),
                sourceVersion,
                payload);
    }

    private ContestRecord mapContest(ResultSet result, int rowNum) throws SQLException {
        Timestamp freezeAt = result.getTimestamp("freeze_at");
        return new ContestRecord(
                result.getLong("id"),
                result.getString("title"),
                result.getString("description_markdown"),
                result.getString("rule_type"),
                result.getString("visibility"),
                result.getString("lifecycle"),
                instant(result, "registration_opens_at"),
                instant(result, "registration_closes_at"),
                instant(result, "starts_at"),
                freezeAt == null ? null : freezeAt.toInstant(),
                instant(result, "ends_at"),
                result.getLong("created_by"),
                instant(result, "created_at"),
                instant(result, "updated_at"));
    }

    private Instant instant(ResultSet result, String column) throws SQLException {
        return result.getTimestamp(column).toInstant();
    }

    private Long nullableLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        return result.wasNull() ? null : value;
    }

    public record ContestRecord(
            long id,
            String title,
            String descriptionMarkdown,
            String ruleType,
            String visibility,
            String lifecycle,
            Instant registrationOpensAt,
            Instant registrationClosesAt,
            Instant startsAt,
            Instant freezeAt,
            Instant endsAt,
            long createdBy,
            Instant createdAt,
            Instant updatedAt) {
        public ContestPhase phase(Instant now) {
            return ContestPolicy.phase(
                    lifecycle,
                    registrationOpensAt,
                    registrationClosesAt,
                    startsAt,
                    freezeAt,
                    endsAt,
                    now);
        }
    }

    public record ContestProblem(long problemId, long problemVersionId, String label, int score) {}
    public record AnnouncementView(
            long id, String title, String contentMarkdown, long publishedBy, Instant publishedAt) {}
    public record ClarificationView(
            long id,
            Long problemId,
            long askedBy,
            String questionMarkdown,
            String status,
            Instant createdAt,
            List<ReplyView> replies) {}
    public record ReplyView(
            long id, String replyMarkdown, long repliedBy, boolean publicReply, Instant createdAt) {}
}
