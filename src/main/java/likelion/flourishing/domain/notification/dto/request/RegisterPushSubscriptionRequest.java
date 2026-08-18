package likelion.flourishing.domain.notification.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 명세 CreatePushSubscriptionRequest 요청 본문.
 *
 * <p>브라우저의 {@code PushSubscription.toJSON()}과 두 군데가 다르다. expirationTime을
 * DOMHighResTimeStamp 숫자가 아니라 date-time 문자열로 받고, userAgent를 본문에 함께 받는다.
 * 클라이언트가 구독 JSON을 그대로 보내는 것이 아니라 두 값을 변환해서 보내야 한다.
 *
 * <p>여기서는 형식만 본다. 키 길이와 곡선 위의 점인지, endpoint가 실제로 요청을 보낼 수 있는
 * 주소인지까지는 서비스가 확인한다. Bean Validation으로는 base64url 디코딩 결과를 검사할 수 없다.
 */
public record RegisterPushSubscriptionRequest(

        @NotBlank
        @Size(max = 2048)
        @Pattern(regexp = "^https://[^\\s]+$", message = "must be an absolute https URL")
        String endpoint,

        /**
         * 브라우저가 만료를 알려 줄 때만 값이 있다. 대부분 null이다.
         *
         * <p>명세가 date-time 문자열이라 문자열로 받고 서비스가 파싱한다. OffsetDateTime으로 바로
         * 받으면 Jackson이 숫자도 epoch 초로 조용히 받아들여, 브라우저가 주는 밀리초 값이
         * 엉뚱한 연도로 저장된다.
         */
        String expirationTime,

        @NotNull
        @Valid
        PushSubscriptionKeysRequest keys,

        @NotBlank
        @Size(max = 512)
        String userAgent
) {

    /** 브라우저가 생성한 구독 공개키와 인증 비밀. 둘 다 패딩 없는 base64url이다. */
    public record PushSubscriptionKeysRequest(

            @NotBlank
            @Size(min = 20, max = 512)
            String p256dh,

            @NotBlank
            @Size(min = 8, max = 256)
            String auth
    ) {
    }
}
