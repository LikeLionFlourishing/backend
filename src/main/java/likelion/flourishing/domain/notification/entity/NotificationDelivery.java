package likelion.flourishing.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import likelion.flourishing.global.entity.BaseTimeEntity;
import likelion.flourishing.support.UuidV7;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자·날짜당 한 건만 남는 발송 이력. (user_id, notification_date)에 유니크 제약이 있어
 * 스케줄러가 두 번 돌아도 두 번 발송되지 않는다.
 *
 * <p>중복 차단을 애플리케이션 조회에만 맡기지 않는다. 먼저 PENDING 행을 넣어 자리를 잡고,
 * 유니크 제약 위반이 나면 이미 처리된 날로 보고 넘긴다. 그래서 조회와 발송 사이에 다른 실행이
 * 끼어들어도 두 건이 나가지 않는다.
 *
 * <p>DDL의 CHECK가 상태와 컬럼의 짝을 강제한다. SENT면 sent_at이 있어야 하고 error_code는 없어야
 * 하며, FAILED는 그 반대다. 잘못된 조합이 만들어지지 않도록 생성자를 막고 상태 전이 메서드만 열어 둔다.
 */
@Entity
@Getter
@Table(name = "notification_deliveries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationDelivery extends BaseTimeEntity {

    /** error_code 컬럼 길이. 공급자 메시지가 길어도 잘라서 넣는다. */
    private static final int MAX_ERROR_CODE_LENGTH = 100;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "target_report_id", updatable = false)
    private UUID targetReportId;

    @Column(name = "notification_date", nullable = false, updatable = false)
    private LocalDate notificationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 30)
    private NotificationType notificationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 20)
    private DeliveryStatus deliveryStatus;

    @Column(name = "error_code", length = MAX_ERROR_CODE_LENGTH)
    private String errorCode;

    @Column(name = "attempted_at")
    private LocalDateTime attemptedAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    private NotificationDelivery(
            UUID userId,
            UUID targetReportId,
            LocalDate notificationDate,
            NotificationType notificationType,
            DeliveryStatus deliveryStatus,
            LocalDateTime attemptedAt
    ) {
        this.id = UuidV7.generate();
        this.userId = userId;
        this.targetReportId = targetReportId;
        this.notificationDate = notificationDate;
        this.notificationType = notificationType;
        this.deliveryStatus = deliveryStatus;
        this.attemptedAt = attemptedAt;
    }

    /** 발송을 시작하기 전에 자리를 잡는다. targetReportId는 경과 알림일 때만 채운다. */
    public static NotificationDelivery pending(
            UUID userId,
            UUID targetReportId,
            LocalDate notificationDate,
            NotificationType notificationType,
            LocalDateTime attemptedAt
    ) {
        return new NotificationDelivery(
                userId,
                targetReportId,
                notificationDate,
                notificationType,
                DeliveryStatus.PENDING,
                attemptedAt
        );
    }

    /**
     * 보낼 곳이 없어 건너뛴 날.
     *
     * <p>보낼 구독이 남아 있지 않으면 발송할 대상이 없다. 그래도 행을 남기는 이유는
     * 같은 날 스케줄러가 다시 돌 때 같은 판정을 반복하지 않게 하려는 것이다.
     */
    public static NotificationDelivery skipped(UUID userId, LocalDate notificationDate) {
        return new NotificationDelivery(
                userId,
                null,
                notificationDate,
                NotificationType.DAILY_CHECK_IN,
                DeliveryStatus.SKIPPED,
                null
        );
    }

    public void markSent(LocalDateTime sentAt) {
        this.deliveryStatus = DeliveryStatus.SENT;
        this.sentAt = sentAt;
        this.errorCode = null;
    }

    public void markFailed(String errorCode) {
        this.deliveryStatus = DeliveryStatus.FAILED;
        this.sentAt = null;
        this.errorCode = truncate(errorCode);
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) {
            return "UNKNOWN";
        }
        return value.length() <= MAX_ERROR_CODE_LENGTH
                ? value
                : value.substring(0, MAX_ERROR_CODE_LENGTH);
    }
}
