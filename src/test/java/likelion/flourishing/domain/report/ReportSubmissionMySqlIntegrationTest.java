package likelion.flourishing.domain.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.report.ai.AiFailureCode;
import likelion.flourishing.domain.report.ai.CareGuideNarrationPort;
import likelion.flourishing.domain.report.ai.NarrationOutcome;
import likelion.flourishing.domain.report.ai.SkinReportStructuringPort;
import likelion.flourishing.domain.report.dto.request.ConfirmedSelectionsRequest;
import likelion.flourishing.domain.report.dto.request.CreateSkinReportRequest;
import likelion.flourishing.domain.report.entity.Appearance;
import likelion.flourishing.domain.report.entity.BodyArea;
import likelion.flourishing.domain.report.entity.CareAvailability;
import likelion.flourishing.domain.report.entity.PreCareCheck;
import likelion.flourishing.domain.report.entity.Sensation;
import likelion.flourishing.domain.report.entity.Situation;
import likelion.flourishing.domain.report.idempotency.IdempotentResponse;
import likelion.flourishing.domain.report.service.CareGuideRegenerationService;
import likelion.flourishing.domain.report.service.SkinReportSubmissionService;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/**
 * 실제 MySQL에서 보고 생성과 관리 설명 재생성의 저장 결과를 확인한다.
 *
 * <p>DDL은 {@code db/schema.sql}을 컨테이너 초기화 스크립트로 그대로 먹인다. 유니크 제약, CHECK,
 * 외래키가 살아 있는 상태에서만 확인할 수 있는 것들이 이 기능의 핵심이기 때문이다.
 *
 * <p>테스트에 {@code @Transactional}을 붙이지 않는다. 규칙이 준비되지 않아 503이 날 때 보고까지
 * 함께 되돌려지는지를 봐야 하는데, 테스트가 트랜잭션을 열고 있으면 서비스가 그 안에 들어와
 * 롤백 여부를 확인할 수 없다. 대신 각 테스트 시작 시 표를 비우고 fixture를 다시 넣는다.
 *
 * <p>규칙 데이터는 테스트 전용 fixture다. 전문가 검토가 끝난 관리 규칙 최종본을 아직 받지 못했으므로
 * 운영 데이터를 쓰지 않는다. AI 호출은 Port를 대역으로 바꿔 실제 외부 호출 없이 성공과 실패를
 * 모두 재현한다.
 */
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReportSubmissionMySqlIntegrationTest {

    private static final UUID USER_ID = UUID.fromString("0198a31f-f33f-7000-8000-000000000100");
    private static final UUID SESSION_ID = UUID.fromString("0198a31f-f33f-7000-8000-000000000101");
    private static final UUID RULE_SET_ID = UUID.fromString("0198a31f-f33f-7000-8000-000000000200");

    private static final UUID COMMON_RULE_ID = UUID.fromString("0198a31f-f33f-7000-8000-000000000210");
    private static final UUID STATE_RULE_ID = UUID.fromString("0198a31f-f33f-7000-8000-000000000211");
    private static final UUID SAFETY_RULE_ID = UUID.fromString("0198a31f-f33f-7000-8000-000000000212");
    private static final UUID COMMON_VERSION_ID = UUID.fromString("0198a31f-f33f-7000-8000-000000000220");
    private static final UUID STATE_VERSION_ID = UUID.fromString("0198a31f-f33f-7000-8000-000000000221");
    private static final UUID SAFETY_VERSION_ID = UUID.fromString("0198a31f-f33f-7000-8000-000000000222");

    private static final String COMMON_FALLBACK = "오늘은 자극을 줄이고 상태를 지켜봐 주세요.";
    private static final String STATE_FALLBACK = "붉은 자리를 건드리지 않고 진정에 집중해 주세요.";
    private static final String CLINICIAN_MESSAGE = "부대 의무실이나 가까운 의료기관에서 확인해 주세요.";

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
        registry.add("app.notifications.crypto.master-key",
                () -> "OTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTBmZWRjYmE=");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SkinReportSubmissionService submissionService;

    @Autowired
    private CareGuideRegenerationService regenerationService;

    @MockitoBean
    private CareGuideNarrationPort narrationPort;

    @MockitoBean
    private SkinReportStructuringPort structuringPort;

    @BeforeEach
    void resetFixtures() {
        clearTables();
        insertUserWithConsent();
        insertRuleSet("ACTIVE");
        insertRules();
    }

    @Test
    void submissionStoresReportResultRulesItemsAndCheckIn() {
        when(narrationPort.narrate(any())).thenReturn(NarrationOutcome.succeeded(
                "붉은 자리를 진정시키는 데 집중해 주세요.",
                List.of("찬 물수건으로 진정하기"),
                List.of("손으로 만지지 않기"),
                List.of("붉은 범위가 넓어졌는지 보기")
        ));
        UUID key = UUID.randomUUID();

        IdempotentResponse response = submissionService.submit(principal(), key, selfCareRequest());

        assertThat(response.status()).isEqualTo(201);
        UUID reportId = response.resourceId();

        Map<String, Object> report = queryOne("""
                SELECT BIN_TO_UUID(id) AS id, report_date, result_type, status,
                       raw_text_encrypted, follow_up_available_at, follow_up_expires_at
                FROM skin_reports WHERE user_id = UUID_TO_BIN(?)
                """, USER_ID.toString());
        assertThat(report.get("result_type")).isEqualTo("SELF_CARE_GUIDE");
        assertThat(report.get("status")).isEqualTo("FOLLOW_UP_PENDING");
        assertThat(report.get("report_date")).hasToString(todayInSeoul().toString());
        assertThat(new String((byte[]) report.get("raw_text_encrypted"))).doesNotContain("턱");

        assertThat(codesOf("report_appearances", "appearance_code", reportId)).containsExactly("APP_REDNESS");
        assertThat(codesOf("report_pre_care_checks", "check_code", reportId)).containsExactly("NONE");

        Map<String, Object> careResult = queryOne("""
                SELECT BIN_TO_UUID(id) AS id, ai_generation_status, summary, clinician_message,
                       retry_used, BIN_TO_UUID(rule_set_id) AS rule_set_id, similarity_score
                FROM care_results WHERE report_id = UUID_TO_BIN(?)
                """, reportId.toString());
        assertThat(careResult.get("ai_generation_status")).isEqualTo("GENERATED");
        assertThat(careResult.get("summary")).isEqualTo("붉은 자리를 진정시키는 데 집중해 주세요.");
        assertThat(careResult.get("clinician_message")).isNull();
        assertThat(careResult.get("similarity_score")).isNull();
        assertThat(careResult.get("rule_set_id").toString()).isEqualToIgnoringCase(RULE_SET_ID.toString());

        UUID careResultId = UUID.fromString((String) careResult.get("id"));
        assertThat(jdbcTemplate.queryForList("""
                SELECT application_order, match_reason FROM care_result_rules
                WHERE care_result_id = UUID_TO_BIN(?) ORDER BY application_order
                """, careResultId.toString()))
                .extracting(row -> row.get("match_reason"))
                .containsExactly("CURRENT_STATE", "COMMON");
        assertThat(jdbcTemplate.queryForList("""
                SELECT item_type, content_snapshot, source_rule_action_id FROM care_result_items
                WHERE care_result_id = UUID_TO_BIN(?) ORDER BY item_type, display_order
                """, careResultId.toString()))
                .extracting(row -> row.get("content_snapshot"))
                .containsExactlyInAnyOrder(
                        "찬 물수건으로 진정하기", "손으로 만지지 않기", "붉은 범위가 넓어졌는지 보기"
                );

        Map<String, Object> checkIn = queryOne("""
                SELECT state, BIN_TO_UUID(report_id) AS report_id FROM daily_check_ins
                WHERE user_id = UUID_TO_BIN(?)
                """, USER_ID.toString());
        assertThat(checkIn.get("state")).isEqualTo("SKIN_REPORT");
        assertThat(checkIn.get("report_id").toString()).isEqualToIgnoringCase(reportId.toString());

        Map<String, Object> idempotency = queryOne("""
                SELECT operation_id, response_status, response_body_encrypted,
                       BIN_TO_UUID(resource_id) AS resource_id
                FROM idempotency_records WHERE idempotency_key = UUID_TO_BIN(?)
                """, key.toString());
        assertThat(idempotency.get("operation_id")).isEqualTo(SkinReportSubmissionService.OPERATION_ID);
        assertThat(idempotency.get("response_status")).hasToString("201");
        assertThat(new String((byte[]) idempotency.get("response_body_encrypted")))
                .doesNotContain("resultType");
    }

    @Test
    void sameKeyAndBodyReplaysTheStoredResponseWithoutAnotherReport() {
        stubFallbackNarration();
        UUID key = UUID.randomUUID();

        IdempotentResponse first = submissionService.submit(principal(), key, selfCareRequest());
        IdempotentResponse second = submissionService.submit(principal(), key, selfCareRequest());

        assertThat(second.replayed()).isTrue();
        assertThat(second.status()).isEqualTo(first.status());
        assertThat(second.jsonBody()).isEqualTo(first.jsonBody());
        assertThat(countOf("skin_reports")).isEqualTo(1);
        assertThat(countOf("care_results")).isEqualTo(1);
    }

    @Test
    void sameKeyWithDifferentBodyIsRejected() {
        stubFallbackNarration();
        UUID key = UUID.randomUUID();
        submissionService.submit(principal(), key, selfCareRequest());

        CreateSkinReportRequest changed = new CreateSkinReportRequest(
                todayInSeoul(),
                "왼쪽 볼이 따가워요.",
                new ConfirmedSelectionsRequest(
                        BodyArea.LEFT_CHEEK,
                        null,
                        List.of(Appearance.APP_REDNESS),
                        List.of(Sensation.REDNESS),
                        List.of(Situation.SHAVING),
                        CareAvailability.ALREADY_WASHED
                ),
                List.of(PreCareCheck.NONE)
        );

        assertThatThrownBy(() -> submissionService.submit(principal(), key, changed))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED);
    }

    @Test
    void differentKeyOnTheSameDayIsConflict() {
        stubFallbackNarration();
        submissionService.submit(principal(), UUID.randomUUID(), selfCareRequest());

        assertThatThrownBy(() -> submissionService.submit(principal(), UUID.randomUUID(), selfCareRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.REPORT_ALREADY_EXISTS);
        assertThat(countOf("skin_reports")).isEqualTo(1);
    }

    /** 503이 나면 보고도 남지 않아야 한다. 결과 없는 보고가 남으면 그날 다시 보고할 수 없다. */
    @Test
    void missingActiveRuleSetRollsBackTheWholeSubmission() {
        jdbcTemplate.update("UPDATE rule_sets SET status = 'APPROVED', activated_at = NULL WHERE id = UUID_TO_BIN(?)",
                RULE_SET_ID.toString());

        assertThatThrownBy(() -> submissionService.submit(
                principal(), UUID.randomUUID(), selfCareRequest()
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RULE_ENGINE_UNAVAILABLE);

        assertThat(countOf("skin_reports")).isZero();
        assertThat(countOf("care_results")).isZero();
        assertThat(countOf("daily_check_ins")).isZero();
        assertThat(countOf("idempotency_records")).isZero();
    }

    @Test
    void unapprovedRuleVersionsAreNotUsed() {
        jdbcTemplate.update("UPDATE care_rule_versions SET review_status = 'REVIEW_REQUIRED'");

        assertThatThrownBy(() -> submissionService.submit(
                principal(), UUID.randomUUID(), selfCareRequest()
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RULE_ENGINE_UNAVAILABLE);
        assertThat(countOf("skin_reports")).isZero();
    }

    @Test
    void narrationFailureStoresApprovedFallbackText() {
        stubFallbackNarration();

        IdempotentResponse response = submissionService.submit(
                principal(), UUID.randomUUID(), selfCareRequest()
        );

        Map<String, Object> careResult = queryOne("""
                SELECT ai_generation_status, summary, retry_used FROM care_results
                WHERE report_id = UUID_TO_BIN(?)
                """, response.resourceId().toString());
        assertThat(careResult.get("ai_generation_status")).isEqualTo("FALLBACK");
        assertThat(careResult.get("summary")).isEqualTo(STATE_FALLBACK);
        assertThat(careResult.get("retry_used")).isEqualTo(false);
    }

    @Test
    void riskSignalStoresClinicianCheckWithApprovedMessage() {
        IdempotentResponse response = submissionService.submit(
                principal(), UUID.randomUUID(), clinicianCheckRequest()
        );

        Map<String, Object> careResult = queryOne("""
                SELECT BIN_TO_UUID(id) AS id, result_type, ai_generation_status, clinician_message, retry_used
                FROM care_results WHERE report_id = UUID_TO_BIN(?)
                """, response.resourceId().toString());
        assertThat(careResult.get("result_type")).isEqualTo("CLINICIAN_CHECK");
        assertThat(careResult.get("ai_generation_status")).isEqualTo("NOT_APPLICABLE");
        assertThat(careResult.get("clinician_message")).isEqualTo(CLINICIAN_MESSAGE);
        assertThat(careResult.get("retry_used")).isEqualTo(false);
        assertThat(jdbcTemplate.queryForList("""
                SELECT content_snapshot FROM care_result_items
                WHERE care_result_id = UUID_TO_BIN(?) AND item_type = 'CLINICIAN_MESSAGE'
                """, careResult.get("id").toString()))
                .extracting(row -> row.get("content_snapshot"))
                .containsExactly(CLINICIAN_MESSAGE);
    }

    @Test
    void regenerationReplacesItemsOnceAndThenIsRejected() {
        stubFallbackNarration();
        UUID reportId = submissionService
                .submit(principal(), UUID.randomUUID(), selfCareRequest())
                .resourceId();

        when(narrationPort.narrate(any())).thenReturn(NarrationOutcome.succeeded(
                "찬 물수건으로 진정하고 오늘은 만지지 마세요.",
                List.of("찬 물수건으로 진정하기"),
                List.of("손으로 만지지 않기"),
                List.of()
        ));

        IdempotentResponse regenerated = regenerationService.regenerate(principal(), reportId, null);

        assertThat(regenerated.status()).isEqualTo(200);
        Map<String, Object> careResult = queryOne("""
                SELECT BIN_TO_UUID(id) AS id, ai_generation_status, summary, retry_used
                FROM care_results WHERE report_id = UUID_TO_BIN(?)
                """, reportId.toString());
        assertThat(careResult.get("ai_generation_status")).isEqualTo("GENERATED");
        assertThat(careResult.get("summary")).isEqualTo("찬 물수건으로 진정하고 오늘은 만지지 마세요.");
        assertThat(careResult.get("retry_used")).isEqualTo(true);
        assertThat(jdbcTemplate.queryForList("""
                SELECT content_snapshot FROM care_result_items WHERE care_result_id = UUID_TO_BIN(?)
                """, careResult.get("id").toString()))
                .extracting(row -> row.get("content_snapshot"))
                .containsExactlyInAnyOrder("찬 물수건으로 진정하기", "손으로 만지지 않기");

        assertThatThrownBy(() -> regenerationService.regenerate(principal(), reportId, null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AI_RETRY_ALREADY_USED);
    }

    @Test
    void regenerationOfAnotherUsersReportIsNotFound() {
        stubFallbackNarration();
        UUID reportId = submissionService
                .submit(principal(), UUID.randomUUID(), selfCareRequest())
                .resourceId();
        UUID otherUserId = UUID.fromString("0198a31f-f33f-7000-8000-000000000900");
        insertUser(otherUserId, "other@example.com");

        AuthenticatedUser otherPrincipal = new AuthenticatedUser(
                otherUserId, SESSION_ID, LocalDateTime.now().plusDays(1), "csrf-token-value-that-is-long-enough"
        );
        insertConsent(otherUserId);

        assertThatThrownBy(() -> regenerationService.regenerate(otherPrincipal, reportId, null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    /** 하루 한 건 검사와 저장 사이에 다른 요청이 끼어도 보고는 하나만 남아야 한다. */
    @Test
    void concurrentSubmissionsStoreOnlyOneReport() throws Exception {
        stubFallbackNarration();

        List<Outcome> outcomes = runConcurrently(
                () -> submissionService.submit(principal(), UUID.randomUUID(), selfCareRequest()),
                () -> submissionService.submit(principal(), UUID.randomUUID(), selfCareRequest())
        );

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .allSatisfy(outcome -> assertThat(outcome.errorCode())
                        .isEqualTo(ErrorCode.REPORT_ALREADY_EXISTS));
        assertThat(countOf("skin_reports")).isEqualTo(1);
        assertThat(countOf("care_results")).isEqualTo(1);
        assertThat(countOf("daily_check_ins")).isEqualTo(1);
    }

    /** 재생성 기회는 결과당 하나다. 동시 호출이 둘 다 통과하면 항목 삽입이 서로 엉킨다. */
    @Test
    void concurrentRegenerationsConsumeTheRetryOnce() throws Exception {
        stubFallbackNarration();
        UUID reportId = submissionService
                .submit(principal(), UUID.randomUUID(), selfCareRequest())
                .resourceId();
        when(narrationPort.narrate(any())).thenReturn(NarrationOutcome.succeeded(
                "찬 물수건으로 진정하고 오늘은 만지지 마세요.",
                List.of("찬 물수건으로 진정하기"),
                List.of("손으로 만지지 않기"),
                List.of()
        ));

        List<Outcome> outcomes = runConcurrently(
                () -> regenerationService.regenerate(principal(), reportId, null),
                () -> regenerationService.regenerate(principal(), reportId, null)
        );

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .allSatisfy(outcome -> assertThat(outcome.errorCode())
                        .isEqualTo(ErrorCode.AI_RETRY_ALREADY_USED));

        Map<String, Object> careResult = queryOne("""
                SELECT BIN_TO_UUID(id) AS id, retry_used, ai_generation_status FROM care_results
                WHERE report_id = UUID_TO_BIN(?)
                """, reportId.toString());
        assertThat(careResult.get("retry_used")).isEqualTo(true);
        assertThat(careResult.get("ai_generation_status")).isEqualTo("GENERATED");
        assertThat(jdbcTemplate.queryForList("""
                SELECT content_snapshot FROM care_result_items WHERE care_result_id = UUID_TO_BIN(?)
                """, careResult.get("id").toString()))
                .extracting(row -> row.get("content_snapshot"))
                .containsExactlyInAnyOrder("찬 물수건으로 진정하기", "손으로 만지지 않기");
    }

    @Test
    void submissionWithoutConsentIsRefused() {
        jdbcTemplate.update("DELETE FROM user_consents WHERE user_id = UUID_TO_BIN(?)", USER_ID.toString());

        assertThatThrownBy(() -> submissionService.submit(
                principal(), UUID.randomUUID(), selfCareRequest()
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CONSENT_REQUIRED);
        assertThat(countOf("skin_reports")).isZero();
    }

    private void stubFallbackNarration() {
        when(narrationPort.narrate(any())).thenReturn(NarrationOutcome.failed(AiFailureCode.AI_TIMEOUT));
    }

    /**
     * 두 요청을 최대한 같은 순간에 보낸다.
     *
     * <p>래치로 출발을 맞춰 검사와 저장 사이에 상대가 끼어들 창을 만든다. 완전히 동시임을 보장할 수는
     * 없지만, 순차 실행에서는 절대 나오지 않는 경합을 반복 실행에서 잡아낸다.
     */
    private List<Outcome> runConcurrently(Callable<IdempotentResponse> first, Callable<IdempotentResponse> second)
            throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Outcome>> futures = List.of(
                    executor.submit(() -> attempt(start, first)),
                    executor.submit(() -> attempt(start, second))
            );
            start.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }

    private Outcome attempt(CountDownLatch start, Callable<IdempotentResponse> call) throws Exception {
        start.await();
        try {
            return new Outcome(call.call(), null);
        } catch (BusinessException exception) {
            return new Outcome(null, exception.getErrorCode());
        }
    }

    /** 동시 실행 결과. 성공하면 응답이, 업무 규칙에 막히면 오류 코드가 담긴다. */
    private record Outcome(IdempotentResponse response, ErrorCode errorCode) {

        private boolean succeeded() {
            return response != null;
        }
    }

    private CreateSkinReportRequest selfCareRequest() {
        return new CreateSkinReportRequest(
                todayInSeoul(),
                "오른쪽 턱이 빨갛고 따가워요.",
                new ConfirmedSelectionsRequest(
                        BodyArea.RIGHT_CHIN,
                        null,
                        List.of(Appearance.APP_REDNESS),
                        List.of(Sensation.REDNESS),
                        List.of(Situation.SHAVING),
                        CareAvailability.ALREADY_WASHED
                ),
                List.of(PreCareCheck.NONE)
        );
    }

    private CreateSkinReportRequest clinicianCheckRequest() {
        return new CreateSkinReportRequest(
                todayInSeoul(),
                "턱에 고름이 잡히고 아파요.",
                new ConfirmedSelectionsRequest(
                        BodyArea.RIGHT_CHIN,
                        null,
                        List.of(Appearance.APP_REDNESS),
                        List.of(Sensation.BREAKOUT),
                        List.of(Situation.SHAVING),
                        CareAvailability.ALREADY_WASHED
                ),
                List.of(PreCareCheck.PUS_OOZING_BLISTER)
        );
    }

    private AuthenticatedUser principal() {
        return new AuthenticatedUser(
                USER_ID, SESSION_ID, LocalDateTime.now().plusDays(1), "csrf-token-value-that-is-long-enough"
        );
    }

    private LocalDate todayInSeoul() {
        return LocalDate.now(ZoneId.of("Asia/Seoul"));
    }

    private List<String> codesOf(String table, String column, UUID reportId) {
        return jdbcTemplate.queryForList(
                "SELECT " + column + " FROM " + table + " WHERE report_id = UUID_TO_BIN(?)",
                String.class,
                reportId.toString()
        );
    }

    private Map<String, Object> queryOne(String sql, Object... arguments) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, arguments);
        assertThat(rows).hasSize(1);
        return rows.getFirst();
    }

    private int countOf(String table) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }

    private void clearTables() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        List.of(
                "idempotency_records",
                "care_result_items",
                "care_result_rules",
                "care_results",
                "follow_ups",
                "daily_check_ins",
                "report_appearances",
                "report_sensations",
                "report_situations",
                "report_pre_care_checks",
                "skin_reports",
                "rule_actions",
                "rule_conditions",
                "care_rule_versions",
                "care_rules",
                "rule_sets",
                "user_consents",
                "users"
        ).forEach(table -> jdbcTemplate.execute("TRUNCATE TABLE " + table));
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
    }

    private void insertUserWithConsent() {
        insertUser(USER_ID, "reports@example.com");
        insertConsent(USER_ID);
    }

    private void insertUser(UUID userId, String email) {
        jdbcTemplate.update("""
                INSERT INTO users (id, email, normalized_email, password_hash, signup_completed_at,
                                   created_at, updated_at)
                VALUES (UUID_TO_BIN(?), ?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, userId.toString(), email, email,
                "$2a$12$DUMMYHASHONLYFORINTEGRATIONTEST000000000000000000000");
    }

    private void insertConsent(UUID userId) {
        jdbcTemplate.update("""
                INSERT INTO user_consents (id, user_id, consent_version, consent_type, accepted,
                                           consented_at, created_at)
                VALUES (UUID_TO_BIN(UUID()), UUID_TO_BIN(?), '2026-08-01', 'SENSITIVE_DATA', TRUE,
                        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, userId.toString());
    }

    private void insertRuleSet(String status) {
        jdbcTemplate.update("""
                INSERT INTO rule_sets (id, version_code, status, approved_at, approved_by_user_id,
                                       activated_at, created_at, updated_at)
                VALUES (UUID_TO_BIN(?), '2026-08-15-v1', ?, UTC_TIMESTAMP(6), UUID_TO_BIN(UUID()),
                        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, RULE_SET_ID.toString(), status);
    }

    /**
     * 테스트 전용 규칙 세 개.
     *
     * <p>공통 규칙은 조건이 없어 항상 걸리고, 현재 상태 규칙은 겉모습에 붉음이 있을 때, 안전 규칙은
     * 관리 전 확인에서 위험 신호를 골랐을 때 걸린다.
     */
    private void insertRules() {
        insertRule(COMMON_RULE_ID, "GEN-001", "COMMON", "공통 관리");
        insertRule(STATE_RULE_ID, "STA-001", "CURRENT_STATE", "붉음 관리");
        insertRule(SAFETY_RULE_ID, "SAF-001", "SAFETY", "위험 신호 안내");

        insertRuleVersion(COMMON_VERSION_ID, COMMON_RULE_ID, "모든 보고에 적용", COMMON_FALLBACK, 500);
        insertRuleVersion(STATE_VERSION_ID, STATE_RULE_ID, "붉음이 있을 때 적용", STATE_FALLBACK, 200);
        insertRuleVersion(SAFETY_VERSION_ID, SAFETY_RULE_ID, "위험 신호가 있을 때 적용", CLINICIAN_MESSAGE, 10);

        insertCondition(STATE_VERSION_ID, "appearances", "CONTAINS", "APP_REDNESS");
        insertCondition(SAFETY_VERSION_ID, "preCareChecks", "NOT_EQUALS", "NONE");

        insertAction(COMMON_VERSION_ID, "DO_TODAY", "미지근한 물로 씻기", 100, 1);
        insertAction(COMMON_VERSION_ID, "AVOID_TODAY", "손으로 만지지 않기", 100, 1);
        insertAction(COMMON_VERSION_ID, "CHECK_NEXT", "붉은 범위가 넓어졌는지 보기", 100, 1);
        insertAction(STATE_VERSION_ID, "DO_TODAY", "찬 물수건으로 진정하기", 50, 1);
        insertAction(SAFETY_VERSION_ID, "AVOID_TODAY", "짜거나 뜯지 않기", 10, 1);
        insertAction(SAFETY_VERSION_ID, "CLINICIAN_MESSAGE", CLINICIAN_MESSAGE, 10, 1);
    }

    private void insertRule(UUID id, String ruleCode, String category, String name) {
        jdbcTemplate.update("""
                INSERT INTO care_rules (id, rule_code, category, name, created_at)
                VALUES (UUID_TO_BIN(?), ?, ?, ?, UTC_TIMESTAMP(6))
                """, id.toString(), ruleCode, category, name);
    }

    private void insertRuleVersion(
            UUID id,
            UUID ruleId,
            String applicationSummary,
            String fallbackText,
            int priority
    ) {
        jdbcTemplate.update("""
                INSERT INTO care_rule_versions (id, rule_id, rule_set_id, version_code, review_status,
                                                application_summary, fallback_text, priority,
                                                created_at, updated_at)
                VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?), 'v1', 'APPROVED', ?, ?, ?,
                        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, id.toString(), ruleId.toString(), RULE_SET_ID.toString(),
                applicationSummary, fallbackText, priority);
    }

    private void insertCondition(UUID ruleVersionId, String fieldCode, String operatorCode, String valueCode) {
        jdbcTemplate.update("""
                INSERT INTO rule_conditions (id, rule_version_id, condition_group, field_code,
                                             operator_code, value_code, negated, display_order, created_at)
                VALUES (UUID_TO_BIN(UUID()), UUID_TO_BIN(?), 1, ?, ?, ?, FALSE, 1, UTC_TIMESTAMP(6))
                """, ruleVersionId.toString(), fieldCode, operatorCode, valueCode);
    }

    private void insertAction(
            UUID ruleVersionId,
            String actionType,
            String content,
            int priority,
            int displayOrder
    ) {
        jdbcTemplate.update("""
                INSERT INTO rule_actions (id, rule_version_id, action_type, content, priority,
                                          display_order, active, created_at)
                VALUES (UUID_TO_BIN(UUID()), UUID_TO_BIN(?), ?, ?, ?, ?, TRUE, UTC_TIMESTAMP(6))
                """, ruleVersionId.toString(), actionType, content, priority, displayOrder);
    }
}
