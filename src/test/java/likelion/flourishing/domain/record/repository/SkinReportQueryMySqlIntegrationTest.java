package likelion.flourishing.domain.record.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import likelion.flourishing.domain.record.cursor.SkinReportCursor;
import likelion.flourishing.domain.report.entity.ReportStatus;
import likelion.flourishing.domain.report.entity.ResultType;
import likelion.flourishing.domain.report.entity.SkinReport;
import likelion.flourishing.domain.report.repository.SkinReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Testcontainers
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SkinReportQueryMySqlIntegrationTest {

    private static final UUID USER_ID = UUID.fromString("0198a31f-f33f-7000-8000-000000000100");
    private static final UUID OTHER_USER_ID = UUID.fromString("0198a31f-f33f-7000-8000-000000000200");
    private static final UUID NEWEST_ID = UUID.fromString("0198a31f-f33f-7000-8000-000000000003");
    private static final UUID SAME_TIME_HIGH_ID = UUID.fromString("0198a31f-f33f-7000-8000-000000000002");
    private static final UUID SAME_TIME_LOW_ID = UUID.fromString("0198a31f-f33f-7000-8000-000000000001");
    private static final LocalDateTime SAME_CREATED_AT = LocalDateTime.of(2026, 8, 14, 3, 0);

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("flourishing")
            .withUsername("test")
            .withPassword("test")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(Path.of("db/schema.sql").toAbsolutePath()),
                    "/docker-entrypoint-initdb.d/001-schema.sql"
            );

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("app.records.crypto.master-key",
                () -> "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        // 알림 도메인 빈도 컨텍스트에 함께 뜨고, 마스터 키가 없으면 기동을 막는다.
        registry.add("app.notifications.crypto.master-key",
                () -> "OTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTBmZWRjYmE=");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SkinReportRepository skinReportRepository;

    @BeforeEach
    void setUpRows() {
        insertUser(USER_ID, "records@example.com");
        insertUser(OTHER_USER_ID, "other@example.com");
        insertReport(
                SAME_TIME_LOW_ID, USER_ID, "2026-08-12", "FOLLOW_UP_PENDING", "SELF_CARE_GUIDE",
                SAME_CREATED_AT
        );
        insertReport(
                SAME_TIME_HIGH_ID, USER_ID, "2026-08-13", "COMPLETED", "SELF_CARE_GUIDE",
                SAME_CREATED_AT
        );
        insertReport(
                NEWEST_ID, USER_ID, "2026-08-14", "COMPLETED", "CLINICIAN_CHECK",
                LocalDateTime.of(2026, 8, 15, 3, 0)
        );
        insertReport(
                UUID.fromString("0198a31f-f33f-7000-8000-000000000004"), OTHER_USER_ID,
                "2026-08-15", "COMPLETED", "SELF_CARE_GUIDE", LocalDateTime.of(2026, 8, 16, 3, 0)
        );
    }

    @Test
    void cursorUsesCreatedAtThenIdWithoutLeakingAnotherUser() {
        List<SkinReport> firstPage = skinReportRepository.findOwnedPage(USER_ID, null, null, null, 2);

        assertThat(firstPage).extracting(SkinReport::getId)
                .containsExactly(NEWEST_ID, SAME_TIME_HIGH_ID);

        SkinReportCursor cursor = new SkinReportCursor(
                firstPage.getLast().getCreatedAt(), firstPage.getLast().getId()
        );
        List<SkinReport> secondPage = skinReportRepository.findOwnedPage(USER_ID, null, null, cursor, 2);

        assertThat(secondPage).extracting(SkinReport::getId)
                .containsExactly(SAME_TIME_LOW_ID);
    }

    @Test
    void statusAndResultTypeFiltersAreAppliedTogether() {
        List<SkinReport> reports = skinReportRepository.findOwnedPage(
                USER_ID, ReportStatus.COMPLETED, ResultType.SELF_CARE_GUIDE, null, 10
        );

        assertThat(reports).extracting(SkinReport::getId)
                .containsExactly(SAME_TIME_HIGH_ID);
    }

    private void insertUser(UUID userId, String email) {
        jdbcTemplate.update("""
                INSERT INTO users (id, email, normalized_email, password_hash, created_at, updated_at)
                VALUES (UUID_TO_BIN(?), ?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, userId.toString(), email, email, "$2a$12$DUMMYHASHONLYFORINTEGRATIONTEST000000000000000000000");
    }

    private void insertReport(
            UUID reportId,
            UUID userId,
            String reportDate,
            String status,
            String resultType,
            LocalDateTime createdAt
    ) {
        jdbcTemplate.update("""
                INSERT INTO skin_reports (
                    id, user_id, report_date, raw_text_encrypted, primary_area,
                    care_availability, result_type, status,
                    follow_up_available_at, follow_up_expires_at, created_at, updated_at
                ) VALUES (
                    UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?, 'RIGHT_CHIN',
                    'ALREADY_WASHED', ?, ?,
                    ?, ?, ?, ?
                )
                """,
                reportId.toString(),
                userId.toString(),
                reportDate,
                new byte[]{1, 2, 3},
                resultType,
                status,
                Timestamp.valueOf(createdAt.plusDays(1)),
                Timestamp.valueOf(createdAt.plusDays(3)),
                Timestamp.valueOf(createdAt),
                Timestamp.valueOf(createdAt)
        );
    }
}
