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
 * <p>P0에서 발송 시각과 시간대는 Asia/Seoul 17:30으로 고정이다. 두 컬럼은 DDL의 기본값과
 * CHECK가 값을 보장하므로 엔티티에 매핑하지 않는다. 매핑하지 않은 컬럼은 INSERT에서 빠져
 * 기본값이 들어가고, 이후 UPDATE도 건드리지 않는다.
 */
@Entity
@Getter
@Table(name = "notification_settings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSetting extends BaseTimeEntity {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission_state", nullable = false, length = 20)
    private NotificationPermission permissionState;

    private NotificationSetting(UUID userId, boolean enabled, NotificationPermission permissionState) {
        this.userId = userId;
        this.enabled = enabled;
        this.permissionState = permissionState;
    }

    public static NotificationSetting create(UUID userId, boolean enabled, NotificationPermission permissionState) {
        return new NotificationSetting(userId, enabled, permissionState);
    }

    /**
     * 온보딩을 다시 완료하면 최신 선택으로 덮어쓴다.
     *
     * <p>알림을 켜겠다는 의사와 브라우저 권한은 별개라 enabled = true, permissionState = DENIED
     * 조합도 그대로 저장한다. 실제 발송은 활성 구독 유무로 걸러진다.
     */
    public void update(boolean enabled, NotificationPermission permissionState) {
        this.enabled = enabled;
        this.permissionState = permissionState;
    }
}
