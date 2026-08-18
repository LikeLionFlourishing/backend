package likelion.flourishing.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import likelion.flourishing.domain.notification.entity.NotificationType;
import likelion.flourishing.domain.notification.repository.NotificationTargetQueryRepository;
import likelion.flourishing.domain.notification.webpush.PushNotificationProperties;
import likelion.flourishing.domain.notification.webpush.PushNotificationProperties.Vapid;
import likelion.flourishing.domain.notification.webpush.WebPushGateway;
import likelion.flourishing.domain.notification.webpush.WebPushMessage;
import likelion.flourishing.domain.notification.webpush.WebPushResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationDispatchServiceTest {

    private static final UUID FIRST_USER = UUID.fromString("2c56fe08-ea1f-45fc-915d-c35b7c0bca39");
    private static final UUID SECOND_USER = UUID.fromString("3d67ff19-fb20-46fd-a26e-d46c8d1cdb40");
    private static final UUID DELIVERY_ID = UUID.fromString("0198a31f-f33f-7000-8000-0000000000d1");
    private static final UUID SUBSCRIPTION_ID = UUID.fromString("0198a31f-f33f-7000-8000-0000000000a1");
    private static final UUID REPORT_ID = UUID.fromString("0198a31f-f33f-7000-8000-000000000001");
    /** 한국 시간 2026-08-15 17:30. UTC로는 같은 날 08:30이다. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-15T08:30:00Z"), ZoneOffset.UTC);
    private static final LocalDate DATE = LocalDate.of(2026, 8, 15);

    @Mock
    private NotificationTargetQueryRepository notificationTargetQueryRepository;

    @Mock
    private NotificationDeliveryService notificationDeliveryService;

    @Mock
    private WebPushGateway webPushGateway;

    private NotificationDispatchService service;

    @BeforeEach
    void setUp() {
        service = newService(configuredProperties());
    }

    @Test
    void todayUsesSeoulDate() {
        when(notificationTargetQueryRepository.findUserIdsToEvaluate(DATE, "17:30")).thenReturn(List.of());

        service.dispatchNow();

        verify(notificationTargetQueryRepository).findUserIdsToEvaluate(DATE, "17:30");
    }

    @Test
    void missingVapidConfigurationSkipsEverything() {
        NotificationDispatchService unconfigured = newService(
                new PushNotificationProperties(new Vapid(null, null, null), null, null, null, null)
        );

        assertThat(unconfigured.dispatch(DATE, "17:30")).isZero();
        verifyNoInteractions(notificationTargetQueryRepository, notificationDeliveryService, webPushGateway);
    }

    @Test
    void sendsOnePushPerActiveSubscription() {
        when(notificationTargetQueryRepository.findUserIdsToEvaluate(DATE, "17:30")).thenReturn(List.of(FIRST_USER));
        when(notificationDeliveryService.reserve(eq(FIRST_USER), eq(DATE), any()))
                .thenReturn(Optional.of(plan(NotificationType.FOLLOW_UP, REPORT_ID, 2)));
        when(webPushGateway.send(any())).thenReturn(WebPushResult.success());

        assertThat(service.dispatch(DATE, "17:30")).isEqualTo(1);
        verify(webPushGateway, times(2)).send(any());
    }

    @Test
    void followUpPayloadCarriesNoSkinDetail() {
        when(notificationTargetQueryRepository.findUserIdsToEvaluate(DATE, "17:30")).thenReturn(List.of(FIRST_USER));
        when(notificationDeliveryService.reserve(eq(FIRST_USER), eq(DATE), any()))
                .thenReturn(Optional.of(plan(NotificationType.FOLLOW_UP, REPORT_ID, 1)));
        when(webPushGateway.send(any())).thenReturn(WebPushResult.success());

        service.dispatch(DATE, "17:30");

        ArgumentCaptor<WebPushMessage> captor = ArgumentCaptor.forClass(WebPushMessage.class);
        verify(webPushGateway).send(captor.capture());
        String payload = new String(captor.getValue().payload(), StandardCharsets.UTF_8);

        assertThat(payload).contains("\"type\":\"FOLLOW_UP\"");
        assertThat(payload).contains("/skin-reports/" + REPORT_ID + "/follow-up");
        assertThat(payload).doesNotContain("REDNESS").doesNotContain("RIGHT_CHIN");
    }

    @Test
    void resultsAreRecordedForEachSubscription() {
        when(notificationTargetQueryRepository.findUserIdsToEvaluate(DATE, "17:30")).thenReturn(List.of(FIRST_USER));
        when(notificationDeliveryService.reserve(eq(FIRST_USER), eq(DATE), any()))
                .thenReturn(Optional.of(plan(NotificationType.DAILY_CHECK_IN, null, 1)));
        when(webPushGateway.send(any())).thenReturn(WebPushResult.expired("HTTP_410"));

        service.dispatch(DATE, "17:30");

        ArgumentCaptor<List<SubscriptionOutcome>> captor = ArgumentCaptor.captor();
        verify(notificationDeliveryService).complete(eq(DELIVERY_ID), captor.capture(), any());

        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).result().isExpired()).isTrue();
    }

    @Test
    void skippedUserDoesNotReachGateway() {
        when(notificationTargetQueryRepository.findUserIdsToEvaluate(DATE, "17:30")).thenReturn(List.of(FIRST_USER));
        when(notificationDeliveryService.reserve(eq(FIRST_USER), eq(DATE), any())).thenReturn(Optional.empty());

        assertThat(service.dispatch(DATE, "17:30")).isZero();
        verify(webPushGateway, never()).send(any());
        verify(notificationDeliveryService, never()).complete(any(), anyList(), any());
    }

    @Test
    void duplicateReservationDoesNotStopRemainingUsers() {
        when(notificationTargetQueryRepository.findUserIdsToEvaluate(DATE, "17:30"))
                .thenReturn(List.of(FIRST_USER, SECOND_USER));
        when(notificationDeliveryService.reserve(eq(FIRST_USER), eq(DATE), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(notificationDeliveryService.reserve(eq(SECOND_USER), eq(DATE), any()))
                .thenReturn(Optional.of(plan(NotificationType.DAILY_CHECK_IN, null, 1)));
        when(webPushGateway.send(any())).thenReturn(WebPushResult.success());

        assertThat(service.dispatch(DATE, "17:30")).isEqualTo(1);
        verify(webPushGateway, times(1)).send(any());
    }

    @Test
    void failureToRecordResultDoesNotStopRemainingUsers() {
        when(notificationTargetQueryRepository.findUserIdsToEvaluate(DATE, "17:30"))
                .thenReturn(List.of(FIRST_USER, SECOND_USER));
        when(notificationDeliveryService.reserve(any(), eq(DATE), any()))
                .thenReturn(Optional.of(plan(NotificationType.DAILY_CHECK_IN, null, 1)));
        when(webPushGateway.send(any())).thenReturn(WebPushResult.success());
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("boom"))
                .when(notificationDeliveryService).complete(any(), anyList(), any());

        assertThat(service.dispatch(DATE, "17:30")).isEqualTo(2);
        verify(webPushGateway, times(2)).send(any());
    }

    private DispatchPlan plan(NotificationType type, UUID reportId, int subscriptions) {
        List<PushTarget> targets = java.util.stream.IntStream.range(0, subscriptions)
                .mapToObj(index -> new PushTarget(
                        index == 0 ? SUBSCRIPTION_ID : UUID.randomUUID(),
                        "https://push.example.net/push/" + index,
                        new byte[]{0x04},
                        "0123456789abcdef".getBytes(StandardCharsets.US_ASCII)
                ))
                .toList();
        return new DispatchPlan(DELIVERY_ID, type, reportId, targets);
    }

    private NotificationDispatchService newService(PushNotificationProperties properties) {
        return new NotificationDispatchService(
                notificationTargetQueryRepository,
                notificationDeliveryService,
                new NotificationPayloadFactory(new com.fasterxml.jackson.databind.ObjectMapper()),
                webPushGateway,
                properties,
                CLOCK
        );
    }

    private PushNotificationProperties configuredProperties() {
        return new PushNotificationProperties(
                new Vapid("public-key", "private-key", "mailto:ops@example.invalid"), null, null, null, null
        );
    }
}
