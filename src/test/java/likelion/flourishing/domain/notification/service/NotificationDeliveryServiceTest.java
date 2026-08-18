package likelion.flourishing.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import likelion.flourishing.domain.notification.crypto.EndpointFingerprint;
import likelion.flourishing.domain.notification.crypto.NotificationCryptoProperties;
import likelion.flourishing.domain.notification.crypto.PushSecretCipher;
import likelion.flourishing.domain.notification.entity.DeliveryStatus;
import likelion.flourishing.domain.notification.entity.NotificationDelivery;
import likelion.flourishing.domain.notification.entity.NotificationType;
import likelion.flourishing.domain.notification.entity.PushSubscription;
import likelion.flourishing.domain.notification.repository.NotificationDeliveryRepository;
import likelion.flourishing.domain.notification.repository.NotificationTargetQueryRepository;
import likelion.flourishing.domain.notification.repository.PushSubscriptionRepository;
import likelion.flourishing.domain.notification.webpush.WebPushResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** 발송 대상 판정과 결과 기록을 확인한다. 외부 호출은 이 클래스가 하지 않는다. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationDeliveryServiceTest {

    private static final String TEST_KEY = "OTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTBmZWRjYmE=";
    private static final UUID USER_ID = UUID.fromString("2c56fe08-ea1f-45fc-915d-c35b7c0bca39");
    private static final UUID REPORT_ID = UUID.fromString("0198a31f-f33f-7000-8000-000000000001");
    private static final LocalDate DATE = LocalDate.of(2026, 8, 15);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 15, 8, 30);
    private static final String ENDPOINT = "https://push.example.net/push/JzLQ3raZJfFBR0aqvOMsLrt54w4rJUsV";

    @Mock
    private NotificationDeliveryRepository notificationDeliveryRepository;

    @Mock
    private NotificationTargetQueryRepository notificationTargetQueryRepository;

    @Mock
    private PushSubscriptionRepository pushSubscriptionRepository;

    private PushSecretCipher pushSecretCipher;
    private NotificationDeliveryService service;

    @BeforeEach
    void setUp() {
        NotificationCryptoProperties properties = new NotificationCryptoProperties(TEST_KEY);
        pushSecretCipher = new PushSecretCipher(properties);
        service = new NotificationDeliveryService(
                notificationDeliveryRepository,
                notificationTargetQueryRepository,
                pushSubscriptionRepository,
                pushSecretCipher
        );
        when(notificationDeliveryRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(notificationDeliveryRepository.findByUserIdAndNotificationDate(USER_ID, DATE))
                .thenReturn(Optional.empty());
    }

    @Test
    void pendingFollowUpWinsOverDailyCheckIn() {
        givenActiveSubscription();
        when(notificationTargetQueryRepository.findOldestPendingFollowUpReportId(USER_ID, NOW))
                .thenReturn(Optional.of(REPORT_ID));

        Optional<DispatchPlan> plan = service.reserve(USER_ID, DATE, NOW);

        assertThat(plan).isPresent();
        assertThat(plan.get().notificationType()).isEqualTo(NotificationType.FOLLOW_UP);
        assertThat(plan.get().targetReportId()).isEqualTo(REPORT_ID);
    }

    @Test
    void reservedRowIsPendingWithTargetReportAndAttemptTime() {
        givenActiveSubscription();
        when(notificationTargetQueryRepository.findOldestPendingFollowUpReportId(USER_ID, NOW))
                .thenReturn(Optional.of(REPORT_ID));

        service.reserve(USER_ID, DATE, NOW);

        NotificationDelivery saved = captureSavedDelivery();
        assertThat(saved.getDeliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(saved.getNotificationType()).isEqualTo(NotificationType.FOLLOW_UP);
        assertThat(saved.getTargetReportId()).isEqualTo(REPORT_ID);
        assertThat(saved.getNotificationDate()).isEqualTo(DATE);
        assertThat(saved.getAttemptedAt()).isEqualTo(NOW);
        assertThat(saved.getSentAt()).isNull();
        assertThat(saved.getErrorCode()).isNull();
    }

    @Test
    void withoutPendingFollowUpSendsDailyCheckIn() {
        givenActiveSubscription();
        when(notificationTargetQueryRepository.findOldestPendingFollowUpReportId(USER_ID, NOW))
                .thenReturn(Optional.empty());

        Optional<DispatchPlan> plan = service.reserve(USER_ID, DATE, NOW);

        assertThat(plan).isPresent();
        assertThat(plan.get().notificationType()).isEqualTo(NotificationType.DAILY_CHECK_IN);
        assertThat(plan.get().targetReportId()).isNull();
    }

    /** 매일 같은 시각에 알림이 오는 것이 P0 정책이라 그날 점호를 마쳤는지는 보지 않는다. */
    @Test
    void dailyCheckInIsSentEvenAfterTodayCheckInWasRecorded() {
        givenActiveSubscription();
        when(notificationTargetQueryRepository.findOldestPendingFollowUpReportId(USER_ID, NOW))
                .thenReturn(Optional.empty());

        Optional<DispatchPlan> plan = service.reserve(USER_ID, DATE, NOW);

        assertThat(plan).isPresent();
        assertThat(plan.get().notificationType()).isEqualTo(NotificationType.DAILY_CHECK_IN);
        assertThat(captureSavedDelivery().getDeliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
    }

    @Test
    void userWithoutActiveSubscriptionIsSkipped() {
        when(pushSubscriptionRepository.findAllByUserIdAndActiveIsTrue(USER_ID)).thenReturn(List.of());

        Optional<DispatchPlan> plan = service.reserve(USER_ID, DATE, NOW);

        assertThat(plan).isEmpty();
        assertThat(captureSavedDelivery().getDeliveryStatus()).isEqualTo(DeliveryStatus.SKIPPED);
    }

    @Test
    void dayThatAlreadyHasDeliveryIsNotReservedAgain() {
        when(notificationDeliveryRepository.findByUserIdAndNotificationDate(USER_ID, DATE))
                .thenReturn(Optional.of(NotificationDelivery.skipped(USER_ID, DATE)));

        Optional<DispatchPlan> plan = service.reserve(USER_ID, DATE, NOW);

        assertThat(plan).isEmpty();
        verify(notificationDeliveryRepository, never()).saveAndFlush(any());
    }

    @Test
    void planCarriesDecryptedSubscriptionSecrets() {
        PushSubscription subscription = givenActiveSubscription();
        when(notificationTargetQueryRepository.findOldestPendingFollowUpReportId(USER_ID, NOW))
                .thenReturn(Optional.of(REPORT_ID));

        DispatchPlan plan = service.reserve(USER_ID, DATE, NOW).orElseThrow();

        assertThat(plan.targets()).hasSize(1);
        PushTarget target = plan.targets().get(0);
        assertThat(target.subscriptionId()).isEqualTo(subscription.getId());
        assertThat(target.endpoint()).isEqualTo(ENDPOINT);
        assertThat(target.authSecret()).isEqualTo("0123456789abcdef".getBytes(StandardCharsets.US_ASCII));
        assertThat(target.toString()).doesNotContain(ENDPOINT);
    }

    @Test
    void oneSuccessMarksDeliverySentAndRecordsSubscriptionSuccess() {
        PushSubscription subscription = givenActiveSubscription();
        NotificationDelivery delivery = pendingDelivery();
        when(notificationDeliveryRepository.findById(delivery.getId())).thenReturn(Optional.of(delivery));
        when(pushSubscriptionRepository.findById(subscription.getId())).thenReturn(Optional.of(subscription));

        service.complete(
                delivery.getId(),
                List.of(
                        new SubscriptionOutcome(subscription.getId(), WebPushResult.failed("HTTP_500")),
                        new SubscriptionOutcome(subscription.getId(), WebPushResult.success())
                ),
                NOW
        );

        assertThat(delivery.getDeliveryStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(delivery.getSentAt()).isEqualTo(NOW);
        assertThat(delivery.getErrorCode()).isNull();
        assertThat(subscription.getLastSuccessAt()).isEqualTo(NOW);
    }

    @Test
    void allFailuresMarkDeliveryFailedWithFirstErrorCode() {
        PushSubscription subscription = givenActiveSubscription();
        NotificationDelivery delivery = pendingDelivery();
        when(notificationDeliveryRepository.findById(delivery.getId())).thenReturn(Optional.of(delivery));
        when(pushSubscriptionRepository.findById(subscription.getId())).thenReturn(Optional.of(subscription));

        service.complete(
                delivery.getId(),
                List.of(new SubscriptionOutcome(subscription.getId(), WebPushResult.failed("HTTP_500"))),
                NOW
        );

        assertThat(delivery.getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(delivery.getErrorCode()).isEqualTo("HTTP_500");
        assertThat(delivery.getSentAt()).isNull();
        assertThat(subscription.isActive()).isTrue();
    }

    @Test
    void expiredSubscriptionIsDeactivated() {
        PushSubscription subscription = givenActiveSubscription();
        NotificationDelivery delivery = pendingDelivery();
        when(notificationDeliveryRepository.findById(delivery.getId())).thenReturn(Optional.of(delivery));
        when(pushSubscriptionRepository.findById(subscription.getId())).thenReturn(Optional.of(subscription));

        service.complete(
                delivery.getId(),
                List.of(new SubscriptionOutcome(subscription.getId(), WebPushResult.expired("HTTP_410"))),
                NOW
        );

        assertThat(subscription.isActive()).isFalse();
        assertThat(delivery.getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(delivery.getErrorCode()).isEqualTo("HTTP_410");
    }

    private NotificationDelivery pendingDelivery() {
        return NotificationDelivery.pending(USER_ID, REPORT_ID, DATE, NotificationType.FOLLOW_UP, NOW);
    }

    private PushSubscription givenActiveSubscription() {
        EndpointFingerprint fingerprint = new EndpointFingerprint(new NotificationCryptoProperties(TEST_KEY));
        PushSubscription subscription = PushSubscription.register(
                USER_ID,
                fingerprint.of(ENDPOINT),
                pushSecretCipher.encryptText(ENDPOINT),
                pushSecretCipher.encrypt(new byte[]{0x04}),
                pushSecretCipher.encrypt("0123456789abcdef".getBytes(StandardCharsets.US_ASCII)),
                "Chrome/130",
                null
        );
        when(pushSubscriptionRepository.findAllByUserIdAndActiveIsTrue(eq(USER_ID)))
                .thenReturn(List.of(subscription));
        return subscription;
    }

    private NotificationDelivery captureSavedDelivery() {
        ArgumentCaptor<NotificationDelivery> captor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(notificationDeliveryRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }
}
