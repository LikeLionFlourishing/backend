package likelion.flourishing.domain.notification.dto.request;

import jakarta.validation.constraints.NotNull;
import likelion.flourishing.domain.onboarding.entity.NotificationPermission;

/**
 * 알림 설정 변경 요청 본문. 정의되지 않은 필드는
 * spring.jackson.deserialization.fail-on-unknown-properties 설정으로 거부한다.
 *
 * <p>P0에서 발송 시각과 시간대는 고정이라 요청으로 바꿀 수 없다. 바꿀 수 있는 값은
 * 사용 여부와 브라우저 권한 상태뿐이다.
 *
 * <p>permissionState는 보내지 않아도 된다. 알림만 끄고 켜는 요청과 브라우저 권한이 바뀐 사실을
 * 알리는 요청이 따로 오기 때문이다. 값이 없으면 저장된 권한 상태를 그대로 둔다.
 */
public record UpdateNotificationSettingsRequest(

        @NotNull
        Boolean enabled,

        NotificationPermission permissionState
) {
}
