package likelion.flourishing.domain.notification.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import likelion.flourishing.domain.notification.crypto.PushSecretCipher;
import likelion.flourishing.domain.notification.entity.NotificationDelivery;
import likelion.flourishing.domain.notification.entity.NotificationType;
import likelion.flourishing.domain.notification.entity.PushSubscription;
import likelion.flourishing.domain.notification.repository.NotificationDeliveryRepository;
import likelion.flourishing.domain.notification.repository.NotificationTargetQueryRepository;
import likelion.flourishing.domain.notification.repository.PushSubscriptionRepository;
import likelion.flourishing.domain.notification.webpush.WebPushResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 발송 전후의 DB 작업만 담당한다. 외부 HTTP 호출은 여기서 하지 않는다.
 *
 * <p>발송을 트랜잭션 안에서 하면 응답이 느린 Push 서비스가 DB 커넥션을 오래 잡는다. 그래서
 * "자리 잡기 → 발송 → 결과 기록"을 세 단계로 끊고, 첫 단계와 마지막 단계만 트랜잭션으로 묶는다.
 */
@Service
public class NotificationDeliveryService {

    private final NotificationDeliveryRepository notificationDeliveryRepository;
    private final NotificationTargetQueryRepository notificationTargetQueryRepository;
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final PushSecretCipher pushSecretCipher;

    public NotificationDeliveryService(
            NotificationDeliveryRepository notificationDeliveryRepository,
            NotificationTargetQueryRepository notificationTargetQueryRepository,
            PushSubscriptionRepository pushSubscriptionRepository,
            PushSecretCipher pushSecretCipher
    ) {
        this.notificationDeliveryRepository = notificationDeliveryRepository;
        this.notificationTargetQueryRepository = notificationTargetQueryRepository;
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.pushSecretCipher = pushSecretCipher;
    }

    /**
     * 그날 그 사용자에게 보낼 것을 정하고 이력 행을 먼저 만든다.
     *
     * <p>우선순위는 미완료 경과가 먼저다. 경과는 입력 기한이 있어 놓치면 되돌릴 수 없고,
     * 피부 점호는 다음 날에도 할 수 있다. 미완료 경과가 없으면 피부 점호 알림을 보낸다.
     * 그날 점호를 이미 마쳤는지는 보지 않는다. 매일 같은 시각에 알림이 오는 것이 P0 정책이다.
     *
     * <p>보낼 구독이 없으면 SKIPPED 행을 남기고 빈 값을 돌려준다. 유니크 제약이 (사용자, 날짜)라서
     * SKIPPED 행도 그날 자리를 차지한다. 재실행 때 같은 판정을 반복하지 않게 하려는 의도다.
     */
    @Transactional
    public Optional<DispatchPlan> reserve(UUID userId, LocalDate notificationDate, LocalDateTime now) {
        if (notificationDeliveryRepository.findByUserIdAndNotificationDate(userId, notificationDate).isPresent()) {
            return Optional.empty();
        }

        List<PushTarget> targets = pushSubscriptionRepository.findAllByUserIdAndActiveIsTrue(userId).stream()
                .map(this::toTarget)
                .toList();
        if (targets.isEmpty()) {
            return skip(userId, notificationDate);
        }

        Optional<UUID> pendingReportId =
                notificationTargetQueryRepository.findOldestPendingFollowUpReportId(userId, now);
        if (pendingReportId.isPresent()) {
            return Optional.of(reservePending(
                    userId, pendingReportId.get(), notificationDate, NotificationType.FOLLOW_UP, now, targets
            ));
        }
        return Optional.of(reservePending(
                userId, null, notificationDate, NotificationType.DAILY_CHECK_IN, now, targets
        ));
    }

    /**
     * 발송 결과를 이력과 구독에 반영한다.
     *
     * <p>구독 하나라도 성공하면 사용자에게 알림이 도착했으므로 SENT로 본다. 모두 실패하면 FAILED로
     * 남기고 첫 오류 코드를 기록한다. 만료 응답이 온 구독만 비활성으로 내린다.
     */
    @Transactional
    public void complete(UUID deliveryId, List<SubscriptionOutcome> outcomes, LocalDateTime completedAt) {
        NotificationDelivery delivery = notificationDeliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new IllegalStateException("발송 이력을 찾을 수 없습니다."));

        boolean anySuccess = false;
        String firstErrorCode = null;
        for (SubscriptionOutcome outcome : outcomes) {
            WebPushResult result = outcome.result();
            if (result.isSuccess()) {
                anySuccess = true;
                pushSubscriptionRepository.findById(outcome.subscriptionId())
                        .ifPresent(subscription -> subscription.markSuccess(completedAt));
                continue;
            }
            if (firstErrorCode == null) {
                firstErrorCode = result.errorCode();
            }
            if (result.isExpired()) {
                pushSubscriptionRepository.findById(outcome.subscriptionId())
                        .ifPresent(PushSubscription::deactivate);
            }
        }

        if (anySuccess) {
            delivery.markSent(completedAt);
        } else {
            delivery.markFailed(firstErrorCode);
        }
        notificationDeliveryRepository.saveAndFlush(delivery);
    }

    private DispatchPlan reservePending(
            UUID userId,
            UUID targetReportId,
            LocalDate notificationDate,
            NotificationType notificationType,
            LocalDateTime attemptedAt,
            List<PushTarget> targets
    ) {
        NotificationDelivery delivery = notificationDeliveryRepository.saveAndFlush(NotificationDelivery.pending(
                userId, targetReportId, notificationDate, notificationType, attemptedAt
        ));
        return new DispatchPlan(delivery.getId(), notificationType, targetReportId, targets);
    }

    private Optional<DispatchPlan> skip(UUID userId, LocalDate notificationDate) {
        notificationDeliveryRepository.saveAndFlush(NotificationDelivery.skipped(userId, notificationDate));
        return Optional.empty();
    }

    private PushTarget toTarget(PushSubscription subscription) {
        return new PushTarget(
                subscription.getId(),
                pushSecretCipher.decryptText(subscription.getEndpointCiphertext()),
                pushSecretCipher.decrypt(subscription.getP256dhCiphertext()),
                pushSecretCipher.decrypt(subscription.getAuthCiphertext())
        );
    }
}
