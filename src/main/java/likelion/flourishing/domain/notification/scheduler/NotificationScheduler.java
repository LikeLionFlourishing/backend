package likelion.flourishing.domain.notification.scheduler;

import likelion.flourishing.domain.notification.service.NotificationDispatchService;
import likelion.flourishing.domain.notification.service.NotificationSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 알림 발송 트리거. 매 분(Asia/Seoul) 돌면서 그 분을 발송 시각으로 고른 사용자를 찾는다.
 *
 * <p>명세 v2_1에서 발송 시각이 사용자마다 달라져 하루 한 번 실행으로는 성립하지 않는다.
 * 대상이 없는 분에는 조회 한 번으로 끝난다.
 *
 * <p>JVM 기본 시간대를 UTC로 고정해 두었으므로 zone을 반드시 지정한다.
 *
 * <p>인스턴스를 여러 대 띄우면 각 인스턴스가 같은 시각에 이 작업을 실행한다. 중복 발송은
 * notification_deliveries의 (user_id, notification_date) 유니크 제약이 막는다.
 *
 * <p>정각 도착은 보장하지 않는다. Push 서비스 전달 시점은 우리가 통제할 수 없다.
 */
@Component
public class NotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);

    private final NotificationDispatchService notificationDispatchService;

    public NotificationScheduler(NotificationDispatchService notificationDispatchService) {
        this.notificationDispatchService = notificationDispatchService;
    }

    @Scheduled(cron = NotificationSchedule.CRON, zone = NotificationSchedule.ZONE_TEXT)
    public void dispatchDueNotifications() {
        try {
            notificationDispatchService.dispatchNow();
        } catch (RuntimeException exception) {
            // 스케줄러 스레드로 예외가 나가면 다음 실행이 취소될 수 있어 여기서 끊는다.
            log.error("알림 발송 작업이 실패했습니다.", exception);
        }
    }
}
