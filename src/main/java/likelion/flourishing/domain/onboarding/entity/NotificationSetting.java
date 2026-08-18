package likelion.flourishing.domain.onboarding.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import likelion.flourishing.global.entity.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자별 알림 설정. 사용자당 한 행이라 user_id가 그대로 기본키다.
 *
 * <p>시간대는 Asia/Seoul 고정이라 DDL의 기본값과 CHECK에 맡기고 매핑하지 않는다.
 * 매핑하지 않은 컬럼은 INSERT에서 빠져 기본값이 들어가고, 이후 UPDATE도 건드리지 않는다.
 *
 * <p>발송 시각은 명세 v2_1에서 17:30 고정이 아니라 온보딩 시간 피커에서 정하는 값이 되어
 * 매핑 대상이 됐다. 설정 화면에서 나중에 바꾸는 기능은 P1이라 여기서는 온보딩만 이 값을 쓴다.
 */
@Entity
@Getter
@Table(name = "notification_settings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSetting extends BaseTimeEntity {

    /**
     * 명세 NotificationTime의 기본값. 알림을 받지 않겠다고 한 사용자도 이 값을 가진다.
     * 다음 날 경과 입력 가능 시점(PendingFollowUp.availableFrom)을 이 값으로 계산하기 때문이다.
     */
    public static final String DEFAULT_TIME = "17:30";

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    /**
     * Asia/Seoul 기준 24시간 표기 HH:mm. LocalTime이 아니라 문자열로 두는 이유는 명세가
     * 초 단위 없는 HH:mm 문자열이라 변환 없이 그대로 오가기 때문이다.
     */
    @Column(name = "notification_time", nullable = false, length = 5)
    private String notificationTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission_state", nullable = false, length = 20)
    private NotificationPermission permissionState;

    private NotificationSetting(
            UUID userId,
            boolean enabled,
            String notificationTime,
            NotificationPermission permissionState
    ) {
        this.userId = userId;
        this.enabled = enabled;
        this.notificationTime = notificationTime;
        this.permissionState = permissionState;
    }

    public static NotificationSetting create(
            UUID userId,
            boolean enabled,
            String notificationTime,
            NotificationPermission permissionState
    ) {
        return new NotificationSetting(userId, enabled, notificationTime, permissionState);
    }

    /**
     * 온보딩을 다시 완료하면 최신 선택으로 덮어쓴다.
     *
     * <p>알림을 켜겠다는 의사와 브라우저 권한은 별개라 enabled = true, permissionState = DENIED
     * 조합도 그대로 저장한다. 실제 발송은 활성 구독 유무로 걸러진다.
     */
    public void update(boolean enabled, String notificationTime, NotificationPermission permissionState) {
        this.enabled = enabled;
        this.notificationTime = notificationTime;
        this.permissionState = permissionState;
    }
}
