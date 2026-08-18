package likelion.flourishing.domain.notification.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * 명세 updateNotificationSettings 요청 본문.
 *
 * <p>time은 명세에 정의된 필드이지만 기능명세서 5.2 기준 P1이라 P0에서는 받지 않는다. 그래도
 * 필드로 선언해 두는 이유는, 명세대로 보낸 클라이언트에게 "정의되지 않은 필드"라는 400 대신
 * "아직 제공하지 않는 기능"이라는 422를 주기 위해서다. 명세가 그 동작을 지정하고 있다.
 *
 * <p>브라우저 권한 상태는 이 요청으로 바꾸지 않는다. 권한은 온보딩(PUT /v1/me/onboarding)에서
 * 받는다. 명세가 additionalProperties: false라 그 밖의 필드는 400으로 거부된다.
 */
public record UpdateNotificationSettingsRequest(

        @NotNull
        Boolean enabled,

        String time
) {

    /** P0에서 시각 변경은 제공하지 않는다. 값이 실려 오면 서비스가 422로 돌린다. */
    public boolean requestsTimeChange() {
        return time != null;
    }
}
