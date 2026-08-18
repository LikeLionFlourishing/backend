package likelion.flourishing.domain.notification.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import likelion.flourishing.domain.onboarding.entity.NotificationPermission;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 명세 NotificationSettings 스키마.
 *
 * <p>필드 이름은 명세를 따른다. time과 timezone은 P0에서 고정값이라 DB 컬럼이 아니라 상수로
 * 내보낸다. 클라이언트가 화면에 그대로 표시할 수 있게 문자열로 준다.
 *
 * <p>activeSubscriptionCount는 알림을 켜 두고도 구독이 없어 아무것도 못 받는 상태를
 * 클라이언트가 알아차릴 수 있게 넣는다.
 *
 * <p>명세가 additionalProperties: false라 정의되지 않은 필드는 내보내지 않는다. 설정이 언제
 * 바뀌었는지는 이 응답의 쓰임새가 아니라 updatedAt을 담지 않는다.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.ALWAYS)
public class NotificationSettingsResponse {

    private final boolean enabled;

    private final String time;

    private final String timezone;

    private final NotificationPermission permission;

    private final long activeSubscriptionCount;

    public static NotificationSettingsResponse of(
            boolean enabled,
            String time,
            String timezone,
            NotificationPermission permission,
            long activeSubscriptionCount
    ) {
        return new NotificationSettingsResponse(enabled, time, timezone, permission, activeSubscriptionCount);
    }
}
