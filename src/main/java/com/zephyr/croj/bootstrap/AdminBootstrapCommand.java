package com.zephyr.croj.bootstrap;

import java.io.PrintStream;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

public final class AdminBootstrapCommand {

    public static final String MODE = "bootstrap-admin";
    private static final Pattern MYSQL_TARGET = Pattern.compile(
            "[A-Za-z0-9._-]+(?::[0-9]{1,5})?(?:,[A-Za-z0-9._-]+(?::[0-9]{1,5})?)*/[A-Za-z0-9_$.-]+");
    private static final Pattern PROPERTY_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9]*");
    private static final Set<String> ALLOWED_DATABASE_PROPERTIES = Set.of(
            "allowpublickeyretrieval",
            "characterencoding",
            "connecttimeout",
            "enabledtlsprotocols",
            "requiressl",
            "servertimezone",
            "sockettimeout",
            "sslmode",
            "tcpkeepalive",
            "useunicode",
            "usessl",
            "verifyservercertificate");

    private AdminBootstrapCommand() {}

    public static boolean isRequested(String mode) {
        return MODE.equals(mode);
    }

    public static int runFromEnvironment() {
        Function<String, String> environment = System::getenv;
        return run(
                environment,
                request -> bootstrapDatabase(environment, request),
                System.out,
                System.err);
    }

    static int run(
            Function<String, String> environment,
            AdminBootstrapExecutor executor,
            PrintStream stdout,
            PrintStream stderr) {
        String databaseURL = environment.apply("DATABASE_URL");
        String databaseUsername = environment.apply("DATABASE_USERNAME");
        String databasePassword = environment.apply("DATABASE_PASSWORD");
        String username = environment.apply("BOOTSTRAP_ADMIN_USERNAME");
        String email = environment.apply("BOOTSTRAP_ADMIN_EMAIL");
        String password = environment.apply("BOOTSTRAP_ADMIN_PASSWORD");
        if (blank(databaseURL)
                || blank(databaseUsername)
                || blank(databasePassword)
                || blank(username)
                || blank(email)
                || blank(password)
                || isUnsafeDatabaseURL(databaseURL)) {
            stderr.println("super-admin bootstrap configuration is incomplete");
            return 2;
        }

        try {
            AdminBootstrapResult result = executor.bootstrap(
                    new AdminBootstrapRequest(username, email, password));
            stdout.println(result == AdminBootstrapResult.CREATED
                    ? "super-admin bootstrap created"
                    : "super-admin bootstrap already present");
            return 0;
        } catch (AdminBootstrapConflictException conflict) {
            stderr.println("super-admin bootstrap conflicts with an existing account");
            return 1;
        } catch (IllegalArgumentException invalid) {
            stderr.println("super-admin bootstrap configuration is invalid");
            return 2;
        } catch (RuntimeException failure) {
            stderr.println("super-admin bootstrap failed");
            return 1;
        }
    }

    private static AdminBootstrapResult bootstrapDatabase(
            Function<String, String> environment,
            AdminBootstrapRequest request) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(environment.apply("DATABASE_URL"));
        dataSource.setUsername(environment.apply("DATABASE_USERNAME"));
        dataSource.setPassword(environment.apply("DATABASE_PASSWORD"));
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        return new AdminBootstrapService(
                new JdbcTemplate(dataSource),
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                new BCryptPasswordEncoder())
                .bootstrap(request);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isUnsafeDatabaseURL(String databaseURL) {
        String prefix = "jdbc:mysql://";
        if (!databaseURL.startsWith(prefix)
                || databaseURL.indexOf('#') >= 0
                || databaseURL.indexOf(';') >= 0
                || databaseURL.chars().anyMatch(Character::isWhitespace)) {
            return true;
        }
        String remainder = databaseURL.substring(prefix.length());
        int queryStart = remainder.indexOf('?');
        String target = queryStart < 0 ? remainder : remainder.substring(0, queryStart);
        if (!MYSQL_TARGET.matcher(target).matches()) {
            return true;
        }
        if (queryStart < 0) {
            return false;
        }
        String query = remainder.substring(queryStart + 1);
        if (query.isEmpty() || query.indexOf('?') >= 0) {
            return true;
        }
        for (String property : query.split("&", -1)) {
            int separator = property.indexOf('=');
            if (separator <= 0 || separator == property.length() - 1) {
                return true;
            }
            String name = property.substring(0, separator);
            String value = property.substring(separator + 1);
            if (!PROPERTY_NAME.matcher(name).matches()
                    || !ALLOWED_DATABASE_PROPERTIES.contains(name.toLowerCase(java.util.Locale.ROOT))
                    || containsCredentialAssignment(value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsCredentialAssignment(String value) {
        String normalized = value.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("user=")
                || normalized.contains("username=")
                || normalized.contains("password=");
    }
}
