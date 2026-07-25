package com.zephyr.croj.problem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.zephyr.croj.model.entity.TestBundle;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

@JdbcTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin-test-bundle;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(statements = {
        "DROP TABLE IF EXISTS t_test_bundle",
        "DROP TABLE IF EXISTS t_problem_version",
        "DROP TABLE IF EXISTS t_problem",
        "DROP TABLE IF EXISTS t_problem_tag_relation",
        "CREATE TABLE t_problem (id BIGINT PRIMARY KEY, status INT NOT NULL, published_version_id BIGINT, is_deleted INT NOT NULL DEFAULT 0)",
        "CREATE TABLE t_problem_version (id BIGINT PRIMARY KEY, problem_id BIGINT NOT NULL, version_no INT NOT NULL, state VARCHAR(20) NOT NULL, statement_json CLOB, limits_json CLOB, judge_config_json CLOB, published_at TIMESTAMP, projection_complete BOOLEAN NOT NULL)",
        "CREATE TABLE t_test_bundle (id BIGINT AUTO_INCREMENT PRIMARY KEY, problem_version_id BIGINT NOT NULL UNIQUE, object_key VARCHAR(512), sha256 CHAR(64), size_bytes BIGINT, manifest_json CLOB)",
        "CREATE TABLE t_problem_tag_relation (problem_id BIGINT NOT NULL, tag_id BIGINT NOT NULL, PRIMARY KEY(problem_id,tag_id))",
        "INSERT INTO t_problem VALUES (42,1,NULL,0)",
        "INSERT INTO t_problem_version VALUES (101,42,1,'DRAFT','{\"title\":\"A\",\"description\":\"D\",\"inputDescription\":\"I\",\"outputDescription\":\"O\",\"hints\":[],\"samples\":[],\"source\":null,\"tags\":[]}','{\"timeLimit\":1000,\"memoryLimit\":64,\"totalScore\":100}','{\"judgeMode\":0,\"specialJudge\":false,\"specialJudgeCode\":null,\"specialJudgeLanguage\":null,\"checker\":\"exact\",\"difficulty\":2}',NULL,TRUE)"
})
class AdminTestBundleServiceIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    private TestBundleService bundles;
    private ProblemVersionPublicationService publications;
    private AdminTestBundleService service;

    @BeforeEach
    void setUp() {
        bundles = mock(TestBundleService.class);
        publications = new ProblemVersionPublicationService(jdbc);
        service = new AdminTestBundleService(jdbc, bundles, publications);
    }

    @Test
    void attachesThenPublishesWithSuccessiveStrongEtags() {
        byte[] archive = {80, 75, 3, 4};
        doAnswer(invocation -> {
            jdbc.update(
                    "INSERT INTO t_test_bundle(problem_version_id,object_key,sha256,size_bytes,manifest_json) VALUES (101,'key',?,4,?)",
                    "a".repeat(64),
                    validManifest());
            TestBundle bundle = new TestBundle();
            bundle.setProblemVersionId(101L);
            bundle.setSha256("a".repeat(64));
            return bundle;
        }).when(bundles).attach(42, 101, archive);

        AdminTestBundleService.View initial = service.describe(42, 101);
        AdminTestBundleService.View attached = service.upload(42, 101, initial.etag(), archive);
        TestBundleApiException stale = assertThrows(
                TestBundleApiException.class,
                () -> service.upload(42, 101, initial.etag(), archive));
        AdminTestBundleService.View published = service.publish(42, 101, attached.etag());

        assertEquals(412, stale.getStatus().value());
        assertEquals("\"tb-v1-101-DRAFT-none\"", initial.etag());
        assertEquals("\"tb-v1-101-DRAFT-" + "a".repeat(64) + "\"", attached.etag());
        assertEquals("PUBLISHED", published.state());
        assertEquals(
                "\"tb-v1-101-PUBLISHED-" + "a".repeat(64) + "\"",
                published.etag());
        assertEquals(101L, jdbc.queryForObject(
                "SELECT published_version_id FROM t_problem WHERE id=42", Long.class));
        verify(bundles).attach(42, 101, archive);
    }

    @Test
    void rejectsAStaleEtagBeforeTouchingStorage() {
        TestBundleApiException exception = assertThrows(
                TestBundleApiException.class,
                () -> service.upload(42, 101, "\"stale\"", new byte[] {1}));

        assertEquals(412, exception.getStatus().value());
        org.mockito.Mockito.verifyNoInteractions(bundles);
    }

    @Test
    void refusesToPublishUntilATestBundleIsAttached() {
        AdminTestBundleService.View initial = service.describe(42, 101);

        TestBundleApiException exception = assertThrows(
                TestBundleApiException.class,
                () -> service.publish(42, 101, initial.etag()));

        assertEquals(409, exception.getStatus().value());
        assertEquals("a validated test bundle must be attached before publication", exception.getMessage());
    }

    @Test
    void returnsNotFoundWhenTheVersionDoesNotBelongToTheProblem() {
        TestBundleApiException exception = assertThrows(
                TestBundleApiException.class,
                () -> service.describe(999, 101));

        assertEquals(404, exception.getStatus().value());
    }

    @Test
    void mapsInvalidZipDetailsToAStableUnprocessableResponse() {
        doAnswer(invocation -> {
            throw new com.zephyr.croj.common.exception.BusinessException(
                    com.zephyr.croj.common.enums.ResultCodeEnum.PARAM_ERROR.getCode(),
                    "central directory secret detail");
        }).when(bundles).attach(anyLong(), anyLong(), any(byte[].class));
        AdminTestBundleService.View initial = service.describe(42, 101);

        TestBundleApiException exception = assertThrows(
                TestBundleApiException.class,
                () -> service.upload(42, 101, initial.etag(), new byte[] {1}));

        assertEquals(422, exception.getStatus().value());
        assertEquals("test bundle is invalid or exceeds contract limits", exception.getMessage());
    }

    @Test
    void listsDiscoverableProblemVersionsNewestFirstWithBundleStateAndEtags() {
        jdbc.update("UPDATE t_problem_version SET state='PUBLISHED' WHERE id=101");
        jdbc.update(
                "INSERT INTO t_test_bundle(problem_version_id,object_key,sha256,size_bytes,manifest_json) "
                        + "VALUES (101,'key',?,4,?)",
                "a".repeat(64),
                validManifest());
        jdbc.update("""
                INSERT INTO t_problem_version VALUES (
                  102,42,2,'DRAFT','{"tags":[]}',
                  '{"timeLimit":1000,"memoryLimit":64}',
                  '{"judgeMode":0,"specialJudge":false,"checker":"exact"}',
                  NULL,TRUE)
                """);

        List<AdminTestBundleService.View> versions = service.list(42);

        assertEquals(List.of(102L, 101L), versions.stream()
                .map(AdminTestBundleService.View::versionId)
                .toList());
        assertEquals("\"tb-v1-102-DRAFT-none\"", versions.get(0).etag());
        assertEquals(false, versions.get(0).attached());
        assertEquals("\"tb-v1-101-PUBLISHED-" + "a".repeat(64) + "\"", versions.get(1).etag());
        assertEquals(true, versions.get(1).attached());
    }

    @Test
    void versionListReturnsNotFoundForAnUnknownOrDeletedProblem() {
        TestBundleApiException unknown = assertThrows(
                TestBundleApiException.class,
                () -> service.list(999));
        jdbc.update("UPDATE t_problem SET is_deleted=1 WHERE id=42");
        TestBundleApiException deleted = assertThrows(
                TestBundleApiException.class,
                () -> service.list(42));

        assertEquals(404, unknown.getStatus().value());
        assertEquals(404, deleted.getStatus().value());
    }

    private String validManifest() {
        return """
                {"schemaVersion":1,"judgeMode":"ACM","checker":"exact",
                 "limits":{"timeLimitMillis":1000,"memoryLimitMiB":64},"cases":[
                  {"id":"1","input":"cases/1.in","output":"cases/1.out","weight":1}
                ]}
                """;
    }
}
