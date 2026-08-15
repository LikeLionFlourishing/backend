package likelion.flourishing.domain.notification.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Push 구독 등록 요청 본문. 브라우저 PushSubscription.toJSON()과 필드가 같다.
 *
 * <p>여기서는 형식만 본다. 키 길이와 곡선 위의 점인지까지는 서비스가 확인한다.
 * Bean Validation으로는 base64url 디코딩 결과를 검사할 수 없기 때문이다.
 *
 * <p>expirationTime은 브라우저가 만료를 알려 줄 때만 채워지는 epoch 밀리초다. 대부분 null이다.
 */
public record RegisterPushSubscriptionRequest(

        @NotBlank
        @Size(max = 2048)
        @Pattern(regexp = "^https://[^\\s]+$", message = "must be an absolute https URL")
        String endpoint,

        @Positive
        Long expirationTime,

        @NotNull
        @Valid
        PushSubscriptionKeysRequest keys
) {

    /** 브라우저가 생성한 구독 공개키와 인증 비밀. 둘 다 패딩 없는 base64url이다. */
    public record PushSubscriptionKeysRequest(

            @NotBlank
            @Size(max = 255)
            String p256dh,

            @NotBlank
            @Size(max = 255)
            String auth
    ) {
    }
}
