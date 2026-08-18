package likelion.flourishing.domain.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import likelion.flourishing.domain.notification.crypto.EndpointFingerprint;
import likelion.flourishing.domain.notification.crypto.PushSecretCipher;
import likelion.flourishing.domain.notification.entity.DeliveryStatus;
import likelion.flourishing.domain.notification.entity.NotificationDelivery;
import likelion.flourishing.domain.notification.entity.NotificationType;
import likelion.flourishing.domain.notification.entity.PushSubscription;
import likelion.flourishing.domain.notification.service.DispatchPlan;
import likelion.flourishing.domain.notification.service.NotificationDeliveryService;
import likelion.flourishing.domain.notification.service.SubscriptionOutcome;
import likelion.flourishing.domain.notification.webpush.WebPushResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/**
 * 실제 MySQL 스키마로 알림 저장과 대상 선정을 검증한다.
 *
 * <p>H2로는 확인할 수 없는 것들을 본다. BINARY(32) 지문과 BLOB 암호문 매핑이 DDL과 맞는지,
 * (user_id, notification_date) 유니크 제약이 중복 발송을 막는지, 상태와 시각·오류 코드의 짝을
 * 강제하는 CHECK를 엔티티 상태 전이가 만족하는지다.
 *
 * <p>ddl-auto = validate라서 컨텍스트가 뜨는 것만으로도 엔티티와 DDL이 일치한다는 뜻이다.
 */
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Testcontainers
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationMySqlIntegrationTest {

    private static final UUID ENABLED_USER_ID = UUID.fromString("0198a31f-f33f-7000-8000-000000000101");
    private static final UUID DISABLED_USER_ID = UUID.fromString("0198a31f-f33f-7000-8000-000000000102");
    private static final UUID NO_SUBSCRIPTION_USER_ID = UUID.fromString("0198a31f-f33f-7000-8000-000000000103");
    private static final UUID OLDEST_REPORT_ID = UUID.fromString("0198a31f-f33f-7000-8000-000000000011");
    private static final UUID NEWER_REPORT_ID = UUID.fromString("0198a31f-f33f-7000-8000-000000000012");
    private static final UUID EXPIRED_REPORT_ID = UUID.fromString("0198a31f-f33f-7000-8000-000000000013");
    private static final LocalDate DATE = LocalDate.of(2026, 8, 15);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 15, 8, 30);
    private static final String ENDPOINT = "https://push.example.net/push/JzLQ3raZJfFBR0aqvOMsLrt54w4rJUsV";
    private static final byte[] AUTH_SECRET = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);

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
    private PushSubscriptionRepository pushSubscriptionRepository;

    @Autowired
    private NotificationDeliveryRepository notificationDeliveryRepository;

    @Autowired
    private NotificationTargetQueryRepository notificationTargetQueryRepository;

    @Autowired
    private NotificationDeliveryService notificationDeliveryService;

    @Autowired
    private PushSecretCipher pushSecretCipher;

    @Autowired
    private EndpointFingerprint endpointFingerprint;

    @BeforeEach
    void setUpRows() {
        insertUser(ENABLED_USER_ID, "enabled@example.com");
        insertUser(DISABLED_USER_ID, "disabled@example.com");
        insertUser(NO_SUBSCRIPTION_USER_ID, "nosub@example.com");
        insertNotificationSetting(ENABLED_USER_ID, true);
        insertNotificationSetting(DISABLED_USER_ID, false);
        insertNotificationSetting(NO_SUBSCRIPTION_USER_ID, true);
        saveSubscription(ENABLED_USER_ID, ENDPOINT);
        saveSubscription(DISABLED_USER_ID, ENDPOINT + "/disabled");
    }

    @Test
    void subscriptionSecretsRoundTripThroughBinaryColumns() {
        PushSubscription saved = pushSubscriptionRepository
                .findByUserIdAndEndpointFingerprint(ENABLED_USER_ID, endpointFingerprint.of(ENDPOINT))
                .orElseThrow();

        assertThat(saved.getEndpointFingerprint()).hasSize(32);
        assertThat(pushSecretCipher.decryptText(saved.getEndpointCiphertext())).isEqualTo(ENDPOINT);
        assertThat(pushSecretCipher.decrypt(saved.getAuthCiphertext())).isEqualTo(AUTH_SECRET);
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    void sameUserCannotStoreTheSameEndpointTwice() {
        assertThatThrownBy(() -> saveSubscription(ENABLED_USER_ID, ENDPOINT))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void onlyEnabledUsersWithActiveSubscriptionAreEvaluated() {
        List<UUID> userIds = notificationTargetQueryRepository.findUserIdsToEvaluate(DATE, "17:30");

        assertThat(userIds).containsExactly(ENABLED_USER_ID);
    }

    /**
     * 발송 시각이 지난 사용자도 그날 아직 못 받았으면 대상이다.
     *
     * <p>등호로 두면 그 분에 실행이 빠진 사용자가 그날을 통째로 건너뛴다. 스케줄러 스레드가
     * 하나뿐이라 앞 실행이 길어지거나 배포가 그 분에 걸치면 실제로 일어난다.
     */
    @Test
    void userWhoseTimeAlreadyPassedIsStillPickedUp() {
        assertThat(notificationTargetQueryRepository.findUserIdsToEvaluate(DATE, "17:45"))
                .containsExactly(ENABLED_USER_ID);
        assertThat(notificationTargetQueryRepository.findUserIdsToEvaluate(DATE, "23:59"))
                .containsExactly(ENABLED_USER_ID);
    }

    /** 아직 발송 시각이 되지 않은 사용자는 고르지 않는다. */
    @Test
    void userWhoseTimeHasNotArrivedIsNotPicked() {
        assertThat(notificationTargetQueryRepository.findUserIdsToEvaluate(DATE, "17:29")).isEmpty();
        assertThat(notificationTargetQueryRepository.findUserIdsToEvaluate(DATE, "00:00")).isEmpty();
    }

    @Test
    void usersWithDeliveryForThatDayAreExcluded() {
        notificationDeliveryRepository.saveAndFlush(NotificationDelivery.skipped(ENABLED_USER_ID, DATE));

        assertThat(notificationTargetQueryRepository.findUserIdsToEvaluate(DATE, "17:30")).isEmpty();
    }

    @Test
    void oldestPendingFollowUpIsChosenAndExpiredOneIgnored() {
        insertReport(EXPIRED_REPORT_ID, ENABLED_USER_ID, "2026-08-10", "FOLLOW_UP_PENDING", NOW.minusDays(1));
        insertReport(OLDEST_REPORT_ID, ENABLED_USER_ID, "2026-08-13", "FOLLOW_UP_PENDING", NOW.plusDays(1));
        insertReport(NEWER_REPORT_ID, ENABLED_USER_ID, "2026-08-14", "FOLLOW_UP_PENDING", NOW.plusDays(2));

        Optional<UUID> reportId =
                notificationTargetQueryRepository.findOldestPendingFollowUpReportId(ENABLED_USER_ID, NOW);

        assertThat(reportId).contains(OLDEST_REPORT_ID);
    }

    @Test
    void completedReportIsNotAFollowUpTarget() {
        insertReport(OLDEST_REPORT_ID, ENABLED_USER_ID, "2026-08-13", "COMPLETED", NOW.plusDays(1));

        assertThat(notificationTargetQueryRepository.findOldestPendingFollowUpReportId(ENABLED_USER_ID, NOW))
                .isEmpty();
    }

    /** 그날 점호를 마쳤더라도 피부 점호 알림은 그대로 발송한다. */
    @Test
    void dailyCheckInIsReservedEvenWhenTodayCheckInExists() {
        insertCheckIn(ENABLED_USER_ID, DATE);

        DispatchPlan plan = notificationDeliveryService.reserve(ENABLED_USER_ID, DATE, NOW).orElseThrow();

        assertThat(plan.notificationType()).isEqualTo(NotificationType.DAILY_CHECK_IN);
        assertThat(notificationDeliveryRepository.findById(plan.deliveryId()).orElseThrow().getDeliveryStatus())
                .isEqualTo(DeliveryStatus.PENDING);
    }

    @Test
    void reserveStoresPendingRowThatSatisfiesCheckConstraints() {
        insertReport(OLDEST_REPORT_ID, ENABLED_USER_ID, "2026-08-13", "FOLLOW_UP_PENDING", NOW.plusDays(1));

        DispatchPlan plan = notificationDeliveryService.reserve(ENABLED_USER_ID, DATE, NOW).orElseThrow();

        NotificationDelivery stored = notificationDeliveryRepository.findById(plan.deliveryId()).orElseThrow();
        assertThat(plan.notificationType()).isEqualTo(NotificationType.FOLLOW_UP);
        assertThat(stored.getDeliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(stored.getTargetReportId()).isEqualTo(OLDEST_REPORT_ID);
        assertThat(stored.getSentAt()).isNull();
    }

    @Test
    void secondReserveOnTheSameDayReturnsNothing() {
        notificationDeliveryService.reserve(ENABLED_USER_ID, DATE, NOW);

        assertThat(notificationDeliveryService.reserve(ENABLED_USER_ID, DATE, NOW)).isEmpty();
        assertThat(countDeliveries(ENABLED_USER_ID, DATE)).isEqualTo(1);
    }

    @Test
    void duplicateDeliveryForOneDayIsRejectedByUniqueConstraint() {
        notificationDeliveryRepository.saveAndFlush(NotificationDelivery.pending(
                ENABLED_USER_ID, null, DATE, NotificationType.DAILY_CHECK_IN, NOW
        ));

        assertThatThrownBy(() -> notificationDeliveryRepository.saveAndFlush(NotificationDelivery.pending(
                ENABLED_USER_ID, null, DATE, NotificationType.DAILY_CHECK_IN, NOW
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sentDeliveryAndSubscriptionSuccessAreStored() {
        DispatchPlan plan = notificationDeliveryService.reserve(ENABLED_USER_ID, DATE, NOW).orElseThrow();
        UUID subscriptionId = plan.targets().get(0).subscriptionId();

        notificationDeliveryService.complete(
                plan.deliveryId(),
                List.of(new SubscriptionOutcome(subscriptionId, WebPushResult.success())),
                NOW
        );

        NotificationDelivery stored = notificationDeliveryRepository.findById(plan.deliveryId()).orElseThrow();
        assertThat(stored.getDeliveryStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(stored.getSentAt()).isEqualTo(NOW);
        assertThat(stored.getErrorCode()).isNull();
        assertThat(pushSubscriptionRepository.findById(subscriptionId).orElseThrow().getLastSuccessAt())
                .isEqualTo(NOW);
    }

    @Test
    void failedDeliveryKeepsErrorCodeAndDeactivatesExpiredSubscription() {
        DispatchPlan plan = notificationDeliveryService.reserve(ENABLED_USER_ID, DATE, NOW).orElseThrow();
        UUID subscriptionId = plan.targets().get(0).subscriptionId();

        notificationDeliveryService.complete(
                plan.deliveryId(),
                List.of(new SubscriptionOutcome(subscriptionId, WebPushResult.expired("HTTP_410"))),
                NOW
        );

        NotificationDelivery stored = notificationDeliveryRepository.findById(plan.deliveryId()).orElseThrow();
        assertThat(stored.getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(stored.getErrorCode()).isEqualTo("HTTP_410");
        assertThat(stored.getSentAt()).isNull();
        assertThat(pushSubscriptionRepository.findById(subscriptionId).orElseThrow().isActive()).isFalse();
        assertThat(notificationTargetQueryRepository.findUserIdsToEvaluate(DATE.plusDays(1), "17:30")).isEmpty();
    }

    private PushSubscription saveSubscription(UUID userId, String endpoint) {
        return pushSubscriptionRepository.saveAndFlush(PushSubscription.register(
                userId,
                endpointFingerprint.of(endpoint),
                pushSecretCipher.encryptText(endpoint),
                pushSecretCipher.encrypt(new byte[]{0x04}),
                pushSecretCipher.encrypt(AUTH_SECRET),
                "Chrome/130",
                null
        ));
    }

    private int countDeliveries(UUID userId, LocalDate notificationDate) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM notification_deliveries
                WHERE user_id = UUID_TO_BIN(?) AND notification_date = ?
                """, Integer.class, userId.toString(), notificationDate);
        return count == null ? 0 : count;
    }

    private void insertUser(UUID userId, String email) {
        jdbcTemplate.update("""
                INSERT INTO users (id, email, normalized_email, password_hash, created_at, updated_at)
                VALUES (UUID_TO_BIN(?), ?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, userId.toString(), email, email, "$2a$12$DUMMYHASHONLYFORINTEGRATIONTEST000000000000000000000");
    }

    private void insertNotificationSetting(UUID userId, boolean enabled) {
        jdbcTemplate.update("""
                INSERT INTO notification_settings (user_id, enabled, permission_state, created_at, updated_at)
                VALUES (UUID_TO_BIN(?), ?, 'GRANTED', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, userId.toString(), enabled);
    }

    private void insertReport(
            UUID reportId,
            UUID userId,
            String reportDate,
            String status,
            LocalDateTime followUpExpiresAt
    ) {
        jdbcTemplate.update("""
                INSERT INTO skin_reports (
                    id, user_id, report_date, raw_text_encrypted, primary_area,
                    care_availability, result_type, status,
                    follow_up_available_at, follow_up_expires_at, created_at, updated_at
                ) VALUES (
                    UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?, 'RIGHT_CHIN',
                    'ALREADY_WASHED', 'SELF_CARE_GUIDE', ?,
                    ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
                )
                """,
                reportId.toString(),
                userId.toString(),
                reportDate,
                new byte[]{1, 2, 3},
                status,
                Timestamp.valueOf(followUpExpiresAt.minusDays(2)),
                Timestamp.valueOf(followUpExpiresAt)
        );
    }

    private void insertCheckIn(UUID userId, LocalDate checkInDate) {
        jdbcTemplate.update("""
                INSERT INTO daily_check_ins (id, user_id, check_in_date, state, created_at, updated_at)
                VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?, 'NO_DISCOMFORT', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, UUID.randomUUID().toString(), userId.toString(), checkInDate);
    }
}
