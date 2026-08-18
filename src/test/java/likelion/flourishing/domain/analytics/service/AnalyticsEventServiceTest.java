package likelion.flourishing.domain.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import likelion.flourishing.domain.analytics.dto.request.AnalyticsEventBatchRequest;
import likelion.flourishing.domain.analytics.dto.request.AnalyticsEventPropertiesRequest;
import likelion.flourishing.domain.analytics.dto.request.AnalyticsEventRequest;
import likelion.flourishing.domain.analytics.repository.AnalyticsEventRepository;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalyticsEventServiceTest {

    private static final UUID USER_ID = UUID.fromString("2c56fe08-ea1f-45fc-915d-c35b7c0bca39");
    private static final UUID SESSION_ID = UUID.fromString("5ecb88d8-6a21-4a54-8967-72599f078963");
    private static final UUID EVENT_ID = UUID.fromString("0198a31f-f33f-7000-8000-000000000001");
    private static final LocalDateTime RECEIVED_AT = LocalDateTime.of(2026, 8, 15, 3, 0);

    @Mock
    private AnalyticsEventRepository analyticsEventRepository;

    private AnalyticsEventService analyticsEventService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-15T03:00:00Z"), ZoneId.of("UTC"));
        analyticsEventService = new AnalyticsEventService(analyticsEventRepository, new ObjectMapper(), clock);
    }

    @Test
    void collectsAllowedEventsAndNormalizesOccurredAtToUtc() {
        AnalyticsEventPropertiesRequest properties = new AnalyticsEventPropertiesRequest(
                1_500L,
                true,
                "SELF_CARE_GUIDE",
                true,
                null,
                null
        );
        AnalyticsEventRequest event = new AnalyticsEventRequest(
                EVENT_ID,
                "REPORT_SUBMITTED",
                properties,
                OffsetDateTime.parse("2026-08-15T12:10:00+09:00")
        );

        int accepted = analyticsEventService.collect(principal(), new AnalyticsEventBatchRequest(List.of(event)));

        assertThat(accepted).isEqualTo(1);
        verify(analyticsEventRepository).insertIdempotently(
                EVENT_ID,
                USER_ID,
                "REPORT_SUBMITTED",
                "{\"durationMs\":1500,\"inputAssistUsed\":true,\"resultType\":\"SELF_CARE_GUIDE\",\"aiSucceeded\":true}",
                LocalDateTime.of(2026, 8, 15, 3, 10),
                RECEIVED_AT
        );
    }

    @Test
    void rejectsWholeBatchBeforeSavingWhenLaterEventNameIsInvalid() {
        AnalyticsEventRequest valid = new AnalyticsEventRequest(
                EVENT_ID,
                "REPORT_STARTED",
                null,
                OffsetDateTime.parse("2026-08-15T03:00:00Z")
        );
        AnalyticsEventRequest invalid = new AnalyticsEventRequest(
                UUID.fromString("0198a31f-f33f-7000-8000-000000000002"),
                "SKIN_RAW_TEXT_SAVED",
                null,
                OffsetDateTime.parse("2026-08-15T03:01:00Z")
        );

        assertThatThrownBy(() -> analyticsEventService.collect(
                principal(),
                new AnalyticsEventBatchRequest(List.of(valid, invalid))
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        verify(analyticsEventRepository, never()).insertIdempotently(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void rejectsSensitivePropertyBeforeSaving() {
        AnalyticsEventPropertiesRequest properties = new AnalyticsEventPropertiesRequest();
        properties.rejectUnknownField("rawText", "얼굴이 붉고 따가워요");
        AnalyticsEventRequest event = new AnalyticsEventRequest(
                EVENT_ID,
                "REPORT_STARTED",
                properties,
                OffsetDateTime.parse("2026-08-15T03:00:00Z")
        );

        assertThatThrownBy(() -> analyticsEventService.collect(
                principal(),
                new AnalyticsEventBatchRequest(List.of(event))
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        verify(analyticsEventRepository, never()).insertIdempotently(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void countsOneEventWhenBatchRepeatsTheSameEventId() {
        AnalyticsEventRequest first = new AnalyticsEventRequest(
                EVENT_ID,
                "REPORT_STARTED",
                null,
                OffsetDateTime.parse("2026-08-15T03:00:00Z")
        );
        AnalyticsEventRequest duplicate = new AnalyticsEventRequest(
                EVENT_ID,
                "REPORT_SUBMITTED",
                null,
                OffsetDateTime.parse("2026-08-15T03:00:01Z")
        );

        int accepted = analyticsEventService.collect(
                principal(),
                new AnalyticsEventBatchRequest(List.of(first, duplicate))
        );

        // 같은 eventId라 행은 하나만 생긴다. 응답도 그 사실과 맞아야 한다.
        assertThat(accepted).isEqualTo(1);
        verify(analyticsEventRepository, times(1)).insertIdempotently(
                org.mockito.ArgumentMatchers.eq(EVENT_ID),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("REPORT_STARTED"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void rejectsOccurredAtFarInTheFutureInsteadOfFailingAtJdbc() {
        // MySQL DATETIME 범위를 넘는 값은 OffsetDateTime 파싱은 통과하고 저장에서 터진다.
        AnalyticsEventRequest event = new AnalyticsEventRequest(
                EVENT_ID,
                "REPORT_STARTED",
                null,
                OffsetDateTime.parse("+10000-01-01T00:00:00Z")
        );

        assertThatThrownBy(() -> analyticsEventService.collect(
                principal(),
                new AnalyticsEventBatchRequest(List.of(event))
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        verify(analyticsEventRepository, never()).insertIdempotently(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void rejectsOccurredAtOlderThanTheBackfillWindow() {
        AnalyticsEventRequest event = new AnalyticsEventRequest(
                EVENT_ID,
                "REPORT_STARTED",
                null,
                OffsetDateTime.parse("2026-07-01T03:00:00Z")
        );

        assertThatThrownBy(() -> analyticsEventService.collect(
                principal(),
                new AnalyticsEventBatchRequest(List.of(event))
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void acceptsOccurredAtWithinTheClockSkewAllowance() {
        AnalyticsEventRequest event = new AnalyticsEventRequest(
                EVENT_ID,
                "REPORT_STARTED",
                null,
                OffsetDateTime.parse("2026-08-15T05:00:00Z")
        );

        assertThat(analyticsEventService.collect(
                principal(),
                new AnalyticsEventBatchRequest(List.of(event))
        )).isEqualTo(1);
    }

    @Test
    void storesEventNamesAndPropertiesAddedInSpecV2() {
        AnalyticsEventPropertiesRequest properties =
                new AnalyticsEventPropertiesRequest(null, null, null, null, 3, true);
        AnalyticsEventRequest ingredients = new AnalyticsEventRequest(
                EVENT_ID,
                "RECOMMENDED_INGREDIENTS_VIEWED",
                properties,
                OffsetDateTime.parse("2026-08-15T03:00:00Z")
        );

        int accepted = analyticsEventService.collect(
                principal(),
                new AnalyticsEventBatchRequest(List.of(ingredients))
        );

        assertThat(accepted).isEqualTo(1);
        verify(analyticsEventRepository).insertIdempotently(
                EVENT_ID,
                USER_ID,
                "RECOMMENDED_INGREDIENTS_VIEWED",
                "{\"ingredientCount\":3,\"usedDefaultTime\":true}",
                LocalDateTime.of(2026, 8, 15, 3, 0),
                RECEIVED_AT
        );
    }

    @Test
    void rejectsIngredientCountAboveTheDeclaredMaximum() {
        AnalyticsEventPropertiesRequest properties =
                new AnalyticsEventPropertiesRequest(null, null, null, null, 4, null);
        AnalyticsEventRequest event = new AnalyticsEventRequest(
                EVENT_ID,
                "RECOMMENDED_INGREDIENTS_VIEWED",
                properties,
                OffsetDateTime.parse("2026-08-15T03:00:00Z")
        );

        assertThatThrownBy(() -> analyticsEventService.collect(
                principal(),
                new AnalyticsEventBatchRequest(List.of(event))
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    private AuthenticatedUser principal() {
        return new AuthenticatedUser(
                USER_ID,
                SESSION_ID,
                LocalDateTime.of(2026, 8, 24, 0, 0),
                "csrf-token-value-that-is-long-enough"
        );
    }
}
