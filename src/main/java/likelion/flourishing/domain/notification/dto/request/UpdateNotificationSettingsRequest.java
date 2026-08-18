package likelion.flourishing.domain.notification.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * 명세 updateNotificationSettings 요청 본문. 정의된 필드는 enabled 하나뿐이다.
 *
 * <p>P0에서 발송 시각과 시간대는 고정이라 요청으로 바꿀 수 없고, 브라우저 권한 상태도 이 요청으로는
 * 바꾸지 않는다. 권한은 온보딩(PUT /v1/me/onboarding)에서 받는다. 명세가
 * additionalProperties: false라 그 밖의 필드는 400으로 거부된다.
 */
public record UpdateNotificationSettingsRequest(

        @NotNull
        Boolean enabled
) {
}
