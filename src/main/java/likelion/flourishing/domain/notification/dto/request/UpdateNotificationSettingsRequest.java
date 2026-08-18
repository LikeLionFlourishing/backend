package likelion.flourishing.domain.notification.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * 알림 설정 부분 갱신 요청. 명세와 같이 필수 필드가 하나도 없다.
 *
 * <p>세 필드 모두 명세에서 nullable 이 아니다. 그래서 "보내지 않음"과 "명시적 null"은 뜻이 다르고,
 * 후자는 거부해야 한다. Jackson 은 둘을 구분하지 못하므로 본문을 JsonNode 로 먼저 받아 키가 실제로
 * 있었는지 확인한 뒤 이 타입으로 옮긴다({@code NotificationSettingsPatchReader}).
 *
 * <p>그 검사를 통과한 뒤에는 null 인 필드가 곧 "보내지 않음"이다.
 */
public record UpdateNotificationSettingsRequest(
        Boolean enabled,

        @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$")
        String time,

        @Valid NotificationConsentInput consent
) {

    public boolean requestsEnabledChange() {
        return enabled != null;
    }

    public boolean requestsTimeChange() {
        return time != null;
    }

    public boolean requestsConsentChange() {
        return consent != null;
    }
}
