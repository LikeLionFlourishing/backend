package likelion.flourishing.domain.notification.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 알림 수신 동의 입력. 명세 NotificationConsentInput 과 같다.
 *
 * <p>agreed 와 version 이 모두 필수다. 어느 문구에 동의했는지 없이 동의만 기록하면 문구가 바뀐 뒤
 * 그 동의가 무엇에 대한 것이었는지 증명할 수 없다.
 */
public record NotificationConsentInput(
        @NotNull Boolean agreed,
        @NotBlank @Size(min = 1, max = 50) String version
) {
}
