package likelion.flourishing.domain.notification.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import likelion.flourishing.domain.onboarding.dto.response.NotificationConsentResponse;
import likelion.flourishing.domain.onboarding.entity.NotificationPermission;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 명세 NotificationSettings 스키마.
 *
 * <p>필드 이름은 명세를 따른다. timezone은 여전히 Asia/Seoul 고정이지만, time은 명세 v2_1에서
 * 사용자가 온보딩에서 고르는 값이 되어 notification_settings에서 읽는다.
 *
 * <p>timeEditable은 P0에서 항상 false다. 설정 화면의 시각 변경은 기능명세서 5.2 기준 P1이고,
 * 이 값이 그 UI를 노출할지를 정한다. 최초 설정은 온보딩 시간 피커에서 한 번 한다.
 *
 * <p>consent는 알림 수신 동의 기록이다. 동의하지 않은 사용자에게도 명세가 이 필드를 필수로
 * 요구하므로, 그때는 agreed = false와 현재 활성 버전을 담고 agreedAt만 null로 둔다.
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

    private final boolean timeEditable;

    private final NotificationPermission permission;

    private final NotificationConsentResponse consent;

    private final long activeSubscriptionCount;

    public static NotificationSettingsResponse of(
            boolean enabled,
            String time,
            String timezone,
            boolean timeEditable,
            NotificationPermission permission,
            NotificationConsentResponse consent,
            long activeSubscriptionCount
    ) {
        return new NotificationSettingsResponse(
                enabled, time, timezone, timeEditable, permission, consent, activeSubscriptionCount
        );
    }
}
