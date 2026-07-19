package com.zephyr.croj.announcement;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AnnouncementRepository {
    private final JdbcTemplate jdbc;

    public long create(CreateCommand command) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO t_announcement
                      (scope,title,content_markdown,lifecycle,is_pinned,pin_order,created_by,updated_by)
                    VALUES ('GLOBAL',?,?,'DRAFT',?,?,?,?)
                    """,
                    new String[] {"id"});
            statement.setString(1, command.title());
            statement.setString(2, command.contentMarkdown());
            statement.setBoolean(3, command.pinned());
            statement.setInt(4, command.pinOrder());
            statement.setLong(5, command.administratorId());
            statement.setLong(6, command.administratorId());
            return statement;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    public Optional<AnnouncementRecord> findById(long announcementId) {
        return jdbc.query(BASE_SELECT + " WHERE id=? AND scope='GLOBAL'", this::map, announcementId)
                .stream()
                .findFirst();
    }

    public Optional<AnnouncementRecord> findVisibleById(long announcementId, Instant now) {
        return jdbc.query(
                        BASE_SELECT + """
                         WHERE id=? AND scope='GLOBAL'
                           AND lifecycle IN ('SCHEDULED','PUBLISHED')
                           AND publish_at<=? AND (expires_at IS NULL OR expires_at>?)
                        """,
                        this::map,
                        announcementId,
                        Timestamp.from(now),
                        Timestamp.from(now))
                .stream()
                .findFirst();
    }

    public List<AnnouncementRecord> listVisible(Instant now, long offset, int size) {
        return jdbc.query(
                BASE_SELECT + """
                 WHERE scope='GLOBAL'
                   AND lifecycle IN ('SCHEDULED','PUBLISHED')
                   AND publish_at<=? AND (expires_at IS NULL OR expires_at>?)
                 ORDER BY is_pinned DESC,
                          CASE WHEN is_pinned=1 THEN pin_order ELSE 0 END ASC,
                          publish_at DESC,id DESC
                 LIMIT ? OFFSET ?
                """,
                this::map,
                Timestamp.from(now),
                Timestamp.from(now),
                size,
                offset);
    }

    public long countVisible(Instant now) {
        Long count = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM t_announcement
                WHERE scope='GLOBAL' AND lifecycle IN ('SCHEDULED','PUBLISHED')
                  AND publish_at<=? AND (expires_at IS NULL OR expires_at>?)
                """,
                Long.class,
                Timestamp.from(now),
                Timestamp.from(now));
        return count == null ? 0 : count;
    }

    public List<AnnouncementRecord> listAdmin(
            Instant now, AnnouncementLifecycle status, long offset, int size) {
        SqlFilter filter = adminFilter(status);
        List<Object> parameters = new ArrayList<>(filter.parameters(now));
        parameters.add(size);
        parameters.add(offset);
        return jdbc.query(
                BASE_SELECT + " WHERE scope='GLOBAL' " + filter.sql()
                        + " ORDER BY updated_at DESC,id DESC LIMIT ? OFFSET ?",
                this::map,
                parameters.toArray());
    }

    public long countAdmin(Instant now, AnnouncementLifecycle status) {
        SqlFilter filter = adminFilter(status);
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_announcement WHERE scope='GLOBAL' " + filter.sql(),
                Long.class,
                filter.parameters(now).toArray());
        return count == null ? 0 : count;
    }

    public int updateContent(
            long id, long version, AnnouncementRequests.Draft request, long administratorId) {
        return jdbc.update(
                """
                UPDATE t_announcement
                SET title=?,content_markdown=?,is_pinned=?,pin_order=?,updated_by=?,version=version+1
                WHERE id=? AND scope='GLOBAL' AND version=? AND lifecycle<>'ARCHIVED'
                """,
                request.title().trim(),
                request.contentMarkdown().trim(),
                request.pinned(),
                request.pinOrder(),
                administratorId,
                id,
                version);
    }

    public int schedule(long id, long version, Instant publishAt, Instant expiresAt, long administratorId) {
        return jdbc.update(
                """
                UPDATE t_announcement
                SET lifecycle='SCHEDULED',publish_at=?,expires_at=?,published_by=?,
                    updated_by=?,archived_at=NULL,version=version+1
                WHERE id=? AND scope='GLOBAL' AND version=? AND lifecycle<>'ARCHIVED'
                """,
                Timestamp.from(publishAt),
                timestamp(expiresAt),
                administratorId,
                administratorId,
                id,
                version);
    }

    public int publish(long id, long version, Instant publishAt, Instant expiresAt, long administratorId) {
        return jdbc.update(
                """
                UPDATE t_announcement
                SET lifecycle='PUBLISHED',publish_at=?,expires_at=?,published_by=?,
                    updated_by=?,archived_at=NULL,version=version+1
                WHERE id=? AND scope='GLOBAL' AND version=? AND lifecycle<>'ARCHIVED'
                """,
                Timestamp.from(publishAt),
                timestamp(expiresAt),
                administratorId,
                administratorId,
                id,
                version);
    }

    public int withdraw(long id, long version, long administratorId) {
        return jdbc.update(
                """
                UPDATE t_announcement
                SET lifecycle='DRAFT',publish_at=NULL,expires_at=NULL,published_by=NULL,
                    updated_by=?,version=version+1
                WHERE id=? AND scope='GLOBAL' AND version=?
                  AND lifecycle IN ('SCHEDULED','PUBLISHED')
                """,
                administratorId,
                id,
                version);
    }

    public int archive(long id, long version, Instant archivedAt, long administratorId) {
        return jdbc.update(
                """
                UPDATE t_announcement
                SET lifecycle='ARCHIVED',archived_at=?,updated_by=?,version=version+1
                WHERE id=? AND scope='GLOBAL' AND version=? AND lifecycle<>'ARCHIVED'
                """,
                Timestamp.from(archivedAt),
                administratorId,
                id,
                version);
    }

    private AnnouncementRecord map(ResultSet result, int row) throws SQLException {
        return new AnnouncementRecord(
                result.getLong("id"),
                result.getString("scope"),
                nullableLong(result, "contest_id"),
                result.getString("title"),
                result.getString("content_markdown"),
                AnnouncementLifecycle.valueOf(result.getString("lifecycle")),
                result.getBoolean("is_pinned"),
                result.getInt("pin_order"),
                instant(result, "publish_at"),
                instant(result, "expires_at"),
                result.getLong("created_by"),
                result.getLong("updated_by"),
                nullableLong(result, "published_by"),
                result.getTimestamp("created_at").toInstant(),
                result.getTimestamp("updated_at").toInstant(),
                instant(result, "archived_at"),
                result.getLong("version"));
    }

    private SqlFilter adminFilter(AnnouncementLifecycle status) {
        if (status == null) return new SqlFilter("", 0);
        return switch (status) {
            case DRAFT -> new SqlFilter("AND lifecycle='DRAFT'", 0);
            case ARCHIVED -> new SqlFilter("AND lifecycle='ARCHIVED'", 0);
            case EXPIRED -> new SqlFilter(
                    "AND lifecycle IN ('SCHEDULED','PUBLISHED') AND expires_at IS NOT NULL AND expires_at<=?",
                    1);
            case SCHEDULED -> new SqlFilter("AND lifecycle='SCHEDULED' AND publish_at>?", 1);
            case PUBLISHED -> new SqlFilter(
                    "AND lifecycle IN ('SCHEDULED','PUBLISHED') AND publish_at<=? "
                            + "AND (expires_at IS NULL OR expires_at>?)",
                    2);
        };
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Long nullableLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        return result.wasNull() ? null : value;
    }

    private static final String BASE_SELECT = """
            SELECT id,scope,contest_id,title,content_markdown,lifecycle,is_pinned,pin_order,
                   publish_at,expires_at,created_by,updated_by,published_by,created_at,updated_at,
                   archived_at,version
            FROM t_announcement
            """;

    public record CreateCommand(
            String title, String contentMarkdown, boolean pinned, int pinOrder, long administratorId) {}

    public record AnnouncementRecord(
            long id,
            String scope,
            Long contestId,
            String title,
            String contentMarkdown,
            AnnouncementLifecycle lifecycle,
            boolean pinned,
            int pinOrder,
            Instant publishAt,
            Instant expiresAt,
            long createdBy,
            long updatedBy,
            Long publishedBy,
            Instant createdAt,
            Instant updatedAt,
            Instant archivedAt,
            long version) {}

    private record SqlFilter(String sql, int instantParameters) {
        List<Object> parameters(Instant now) {
            List<Object> values = new ArrayList<>();
            for (int index = 0; index < instantParameters; index++) {
                values.add(Timestamp.from(now));
            }
            return values;
        }
    }
}
