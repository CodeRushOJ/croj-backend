package com.zephyr.croj.bootstrap;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

public final class AdminBootstrapService {

    static final String LOCK_NAME = "first-super-admin";
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_.-]{3,50}");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]{1,190}$");

    private final JdbcTemplate database;
    private final TransactionTemplate transaction;
    private final PasswordEncoder passwords;

    public AdminBootstrapService(
            JdbcTemplate database,
            TransactionTemplate transaction,
            PasswordEncoder passwords) {
        this.database = Objects.requireNonNull(database, "database");
        this.transaction = Objects.requireNonNull(transaction, "transaction");
        this.passwords = Objects.requireNonNull(passwords, "passwords");
    }

    public AdminBootstrapResult bootstrap(AdminBootstrapRequest request) {
        validate(request);
        return Objects.requireNonNull(transaction.execute(status -> bootstrapInTransaction(request)));
    }

    private AdminBootstrapResult bootstrapInTransaction(AdminBootstrapRequest request) {
        BootstrapClaim claim = database.queryForObject(
                """
                SELECT administrator_id, administrator_username, administrator_email
                  FROM t_system_bootstrap_lock
                 WHERE name = ?
                 FOR UPDATE
                """,
                (row, index) -> new BootstrapClaim(
                        row.getObject("administrator_id", Long.class),
                        row.getString("administrator_username"),
                        row.getString("administrator_email")),
                LOCK_NAME);

        if (claim != null && claim.isClaimed()) {
            return replayClaimedIdentity(request, claim);
        }

        List<ExistingAccount> accounts = database.query(
                """
                SELECT id, username, email, role, status, email_verified, is_deleted
                  FROM t_user
                 WHERE username = ? OR email = ? OR role = 2
                 FOR UPDATE
                """,
                (row, index) -> new ExistingAccount(
                        row.getLong("id"),
                        row.getString("username"),
                        row.getString("email"),
                        row.getInt("role"),
                        row.getInt("status"),
                        row.getInt("email_verified"),
                        row.getInt("is_deleted")),
                request.username(),
                request.email());

        if (!accounts.isEmpty()) {
            throw new AdminBootstrapConflictException();
        }

        database.update(
                """
                INSERT INTO t_user
                    (username, password, email, role, status, email_verified, is_deleted)
                VALUES (?, ?, ?, 2, 0, 1, 0)
                """,
                request.username(),
                passwords.encode(request.password()),
                request.email());
        Long administratorID = database.queryForObject(
                "SELECT id FROM t_user WHERE username = ?",
                Long.class,
                request.username());
        int claimed = database.update(
                """
                UPDATE t_system_bootstrap_lock
                   SET administrator_id = ?,
                       administrator_username = ?,
                       administrator_email = ?,
                       claimed_at = CURRENT_TIMESTAMP(3)
                 WHERE name = ?
                   AND administrator_id IS NULL
                """,
                administratorID,
                request.username(),
                request.email(),
                LOCK_NAME);
        if (claimed != 1) {
            throw new IllegalStateException("the bootstrap guard could not be claimed");
        }
        database.update(
                """
                INSERT INTO t_audit_log
                    (actor_id, action, resource_type, resource_id, metadata)
                VALUES (?, 'SYSTEM_BOOTSTRAP_SUPER_ADMIN', 'USER', ?, NULL)
                """,
                administratorID,
                String.valueOf(administratorID));
        return AdminBootstrapResult.CREATED;
    }

    private AdminBootstrapResult replayClaimedIdentity(
            AdminBootstrapRequest request,
            BootstrapClaim claim) {
        if (!claim.matches(request)) {
            throw new AdminBootstrapConflictException();
        }
        List<ExistingAccount> accounts = database.query(
                """
                SELECT id, username, email, role, status, email_verified, is_deleted
                  FROM t_user
                 WHERE id = ?
                 FOR UPDATE
                """,
                (row, index) -> new ExistingAccount(
                        row.getLong("id"),
                        row.getString("username"),
                        row.getString("email"),
                        row.getInt("role"),
                        row.getInt("status"),
                        row.getInt("email_verified"),
                        row.getInt("is_deleted")),
                claim.administratorID());
        if (accounts.size() == 1 && accounts.get(0).isSameActiveSuperAdmin(request)) {
            return AdminBootstrapResult.ALREADY_PRESENT;
        }
        throw new AdminBootstrapConflictException();
    }

    private static void validate(AdminBootstrapRequest request) {
        if (request == null
                || request.username() == null
                || !USERNAME.matcher(request.username()).matches()
                || request.email() == null
                || request.email().length() > 100
                || !EMAIL.matcher(request.email()).matches()
                || request.password() == null
                || request.password().codePointCount(0, request.password().length()) < 12
                || request.password().codePointCount(0, request.password().length()) > 128
                || request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("bootstrap administrator values are invalid");
        }
    }

    private record BootstrapClaim(
            Long administratorID,
            String administratorUsername,
            String administratorEmail) {

        boolean isClaimed() {
            return administratorID != null;
        }

        boolean matches(AdminBootstrapRequest request) {
            return administratorUsername != null
                    && administratorUsername.equals(request.username())
                    && administratorEmail != null
                    && administratorEmail.equals(request.email());
        }
    }

    private record ExistingAccount(
            long id,
            String username,
            String email,
            int role,
            int status,
            int emailVerified,
            int deleted) {

        boolean isSameActiveSuperAdmin(AdminBootstrapRequest request) {
            return username.equals(request.username())
                    && email.equals(request.email())
                    && role == 2
                    && status == 0
                    && emailVerified == 1
                    && deleted == 0;
        }
    }
}
