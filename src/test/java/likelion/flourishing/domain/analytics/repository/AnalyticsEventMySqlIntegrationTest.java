package likelion.flourishing.domain.analytics.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import likelion.flourishing.domain.analytics.entity.AnalyticsEvent;
import likelion.flourishing.domain.analytics.dto.request.AnalyticsEventBatchRequest;
import likelion.flourishing.domain.analytics.dto.request.AnalyticsEventPropertiesRequest;
import likelion.flourishing.domain.analytics.dto.request.AnalyticsEventRequest;
import likelion.flourishing.domain.analytics.service.AnalyticsEventService;
import likelion.flourishing.domain.auth.entity.User;
import likelion.flourishing.domain.auth.repository.UserRepository;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Testcontainers
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AnalyticsEventMySqlIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("flourishing")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        // 이 테스트가 쓰지 않는 도메인의 키다. @SpringBootTest 가 전체 컨텍스트를 띄우므로
        // 기록 암호화와 푸시 비밀 암호화 빈이 함께 만들어지고, 키가 없으면 생성자에서 막힌다.
        registry.add("app.records.crypto.master-key",
                () -> "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        registry.add("app.notifications.crypto.master-key",
                () -> "OTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTBmZWRjYmE=");
    }

    @Autowired
    private AnalyticsEventService analyticsEventService;

    @Autowired
    private AnalyticsEventRepository analyticsEventRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void duplicateEventIdIsStoredOnlyOnceInMySql() {
        User user = userRepository.saveAndFlush(User.register(
                "analytics@example.com",
                "$2a$12$DUMMYHASHONLYFORINTEGRATIONTEST000000000000000000000"
        ));
        AuthenticatedUser principal = new AuthenticatedUser(
                user.getId(),
                java.util.UUID.fromString("5ecb88d8-6a21-4a54-8967-72599f078963"),
                LocalDateTime.of(2026, 8, 24, 0, 0),
                "csrf-token-value-that-is-long-enough"
        );
        java.util.UUID eventId = java.util.UUID.fromString("0198a31f-f33f-7000-8000-000000000001");
        AnalyticsEventBatchRequest request = new AnalyticsEventBatchRequest(List.of(
                new AnalyticsEventRequest(
                        eventId,
                        "REPORT_SUBMITTED",
                        new AnalyticsEventPropertiesRequest(18_000L, true, "SELF_CARE_GUIDE", true, null, null),
                        OffsetDateTime.parse("2026-08-15T12:00:18+09:00")
                )
        ));

        int firstAcceptedCount = analyticsEventService.collect(principal, request);
        int retryAcceptedCount = analyticsEventService.collect(principal, request);

        assertThat(firstAcceptedCount).isEqualTo(1);
        assertThat(retryAcceptedCount).isEqualTo(1);
        assertThat(analyticsEventRepository.count()).isEqualTo(1);

        AnalyticsEvent stored = analyticsEventRepository.findById(eventId).orElseThrow();
        assertThat(stored.getUserId()).isEqualTo(user.getId());
        assertThat(stored.getOccurredAt()).isEqualTo(LocalDateTime.of(2026, 8, 15, 3, 0, 18));
        assertThat(((Number) stored.getAllowedProperties().get("durationMs")).longValue()).isEqualTo(18_000L);
        assertThat(stored.getAllowedProperties())
                .containsEntry("inputAssistUsed", true)
                .containsEntry("resultType", "SELF_CARE_GUIDE")
                .containsEntry("aiSucceeded", true);
    }
}
