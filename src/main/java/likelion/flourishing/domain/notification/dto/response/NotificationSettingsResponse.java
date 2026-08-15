package likelion.flourishing.domain.notification.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import likelion.flourishing.domain.onboarding.entity.NotificationPermission;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 알림 설정 응답.
 *
 * <p>notificationTime과 timezone은 P0에서 고정값이라 DB 컬럼이 아니라 상수로 내보낸다.
 * 클라이언트가 화면에 그대로 표시할 수 있게 문자열로 준다.
 *
 * <p>activeSubscriptionCount는 알림을 켜 두고도 구독이 없어 아무것도 못 받는 상태를
 * 클라이언트가 알아차릴 수 있게 넣는다.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.ALWAYS)
public class NotificationSettingsResponse {

    private final boolean enabled;

    private final String notificationTime;

    private final String timezone;

    private final NotificationPermission permissionState;

    private final long activeSubscriptionCount;

    private final OffsetDateTime updatedAt;

    public static NotificationSettingsResponse of(
            boolean enabled,
            String notificationTime,
            String timezone,
            NotificationPermission permissionState,
            long activeSubscriptionCount,
            OffsetDateTime updatedAt
    ) {
        return new NotificationSettingsResponse(
                enabled,
                notificationTime,
                timezone,
                permissionState,
                activeSubscriptionCount,
                updatedAt
        );
    }
}
