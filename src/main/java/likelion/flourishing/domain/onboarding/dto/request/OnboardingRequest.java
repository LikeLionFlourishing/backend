package likelion.flourishing.domain.onboarding.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import likelion.flourishing.domain.onboarding.entity.NotificationPermission;

/**
 * 명세 OnboardingRequest. 정의되지 않은 필드는
 * spring.jackson.deserialization.fail-on-unknown-properties 설정으로 거부한다.
 *
 * <p>sensitiveDataConsent는 명세가 const true로 정의한 필수 동의라 false를 받지 않는다.
 * AssertTrue는 null을 통과시키므로 NotNull을 함께 붙인다.
 */
public record OnboardingRequest(

        @NotBlank
        @Size(max = 50)
        String consentVersion,

        @NotNull
        @AssertTrue(message = "민감정보 처리 동의가 있어야 온보딩을 완료할 수 있습니다.")
        Boolean sensitiveDataConsent,

        @NotNull
        Boolean notificationEnabled,

        @NotNull
        NotificationPermission notificationPermission
) {
}
