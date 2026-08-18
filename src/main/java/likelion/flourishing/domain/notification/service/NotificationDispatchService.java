package likelion.flourishing.domain.notification.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import likelion.flourishing.domain.notification.repository.NotificationTargetQueryRepository;
import likelion.flourishing.domain.notification.webpush.PushNotificationProperties;
import likelion.flourishing.domain.notification.webpush.WebPushGateway;
import likelion.flourishing.domain.notification.webpush.WebPushMessage;
import likelion.flourishing.domain.notification.webpush.WebPushResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * 17:30 알림 발송 흐름을 조립한다.
 *
 * <p>트랜잭션을 걸지 않는다. 사용자 한 명의 DB 작업은 {@link NotificationDeliveryService}가
 * 각자 짧은 트랜잭션으로 처리하고, 그 사이의 외부 호출은 트랜잭션 밖에서 한다. 한 사용자에게
 * 실패해도 나머지 사용자 발송은 계속 진행된다.
 */
@Service
public class NotificationDispatchService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchService.class);

    private final NotificationTargetQueryRepository notificationTargetQueryRepository;
    private final NotificationDeliveryService notificationDeliveryService;
    private final NotificationPayloadFactory notificationPayloadFactory;
    private final WebPushGateway webPushGateway;
    private final PushNotificationProperties properties;
    private final Clock clock;

    public NotificationDispatchService(
            NotificationTargetQueryRepository notificationTargetQueryRepository,
            NotificationDeliveryService notificationDeliveryService,
            NotificationPayloadFactory notificationPayloadFactory,
            WebPushGateway webPushGateway,
            PushNotificationProperties properties,
            Clock clock
    ) {
        this.notificationTargetQueryRepository = notificationTargetQueryRepository;
        this.notificationDeliveryService = notificationDeliveryService;
        this.notificationPayloadFactory = notificationPayloadFactory;
        this.webPushGateway = webPushGateway;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 지금 이 분에 발송할 몫을 보낸다. 실제로 발송한 사용자 수를 돌려준다.
     *
     * <p>명세 v2_1에서 발송 시각이 사용자마다 달라져, 하루 한 번이 아니라 매 분 돌면서 그 분을
     * 고른 사용자만 고른다.
     */
    public int dispatchNow() {
        return dispatch(NotificationSchedule.today(clock), NotificationSchedule.currentTimeText(clock));
    }

    public int dispatch(LocalDate notificationDate, String notificationTime) {
        if (!properties.vapidConfigured()) {
            log.warn("VAPID 키가 없어 {} {} 알림 발송을 건너뜁니다.", notificationDate, notificationTime);
            return 0;
        }

        List<UUID> userIds = notificationTargetQueryRepository
                .findUserIdsToEvaluate(notificationDate, notificationTime);
        int sent = 0;
        for (UUID userId : userIds) {
            if (dispatchToUser(userId, notificationDate)) {
                sent++;
            }
        }
        if (!userIds.isEmpty()) {
            log.info(
                    "{} {} 알림 발송을 마쳤습니다. 대상 {}명 중 {}명 발송",
                    notificationDate, notificationTime, userIds.size(), sent
            );
        }
        return sent;
    }

    /**
     * 사용자 한 명 처리. 발송을 시도했으면 참을 돌려준다.
     *
     * <p>어떤 예외도 밖으로 내지 않는다. 한 사람 때문에 그날 전체 발송이 멈추면 안 된다.
     * 이미 다른 실행이 그날 자리를 잡았다면 유니크 제약 위반이 나는데, 그것도 정상 흐름으로 본다.
     */
    private boolean dispatchToUser(UUID userId, LocalDate notificationDate) {
        LocalDateTime now = LocalDateTime.now(clock);
        Optional<DispatchPlan> plan;
        try {
            plan = notificationDeliveryService.reserve(userId, notificationDate, now);
        } catch (DataAccessException exception) {
            // (user_id, notification_date) 유니크 제약이 중복 발송을 막은 경우가 대부분이다.
            log.info("이미 처리된 사용자여서 건너뜁니다. userId={}", userId);
            return false;
        }
        if (plan.isEmpty()) {
            return false;
        }

        DispatchPlan dispatchPlan = plan.get();
        byte[] payload = notificationPayloadFactory.create(
                dispatchPlan.notificationType(), dispatchPlan.targetReportId()
        );

        List<SubscriptionOutcome> outcomes = new ArrayList<>();
        for (PushTarget target : dispatchPlan.targets()) {
            WebPushResult result = webPushGateway.send(new WebPushMessage(
                    target.endpoint(), target.userAgentPublicKey(), target.authSecret(), payload
            ));
            outcomes.add(new SubscriptionOutcome(target.subscriptionId(), result));
        }

        try {
            notificationDeliveryService.complete(dispatchPlan.deliveryId(), outcomes, LocalDateTime.now(clock));
        } catch (RuntimeException exception) {
            log.error("발송 결과를 기록하지 못했습니다. userId={} deliveryId={}",
                    userId, dispatchPlan.deliveryId(), exception);
        }
        return true;
    }
}
