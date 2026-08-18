package likelion.flourishing.domain.onboarding.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import likelion.flourishing.domain.onboarding.entity.NotificationPermission;

/**
 * 명세 OnboardingRequest. 정의되지 않은 필드는
 * spring.jackson.deserialization.fail-on-unknown-properties 설정으로 거부한다.
 *
 * <p>sensitiveDataConsent는 명세가 const true로 정의한 필수 동의라 false를 받지 않는다.
 * AssertTrue는 null을 통과시키므로 NotNull을 함께 붙인다.
 *
 * <p>명세 v2_1에서 온보딩 마지막 화면이 시간 피커로 바뀌면서 갈래가 둘로 나뉜다.
 * 명세는 이것을 JSON Schema의 if/then으로 적었는데 Bean Validation에는 대응하는 표현이 없어
 * 아래 두 개의 파생 검증으로 옮겼다.
 *
 * <ul>
 *   <li>{@code 시작하기} — notificationEnabled = true. 피커 값(notificationTime)과
 *       알림 수신 동의 2/2(notificationConsent, notificationConsentVersion)가 모두 필수다.
 *   <li>{@code 알림을 받지 않을게요} — notificationEnabled = false. 셋 다 생략할 수 있고
 *       서버가 기본 시각을 저장한다.
 * </ul>
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
        NotificationPermission notificationPermission,

        @Pattern(
                regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$",
                message = "피부 점호 시각은 HH:mm 형식이어야 합니다."
        )
        String notificationTime,

        Boolean notificationConsent,

        @Size(max = 50)
        String notificationConsentVersion
) {

    /**
     * 알림을 켜는 요청은 알림 수신 동의가 함께 와야 한다. 명세가 then 갈래에서
     * notificationConsent를 const true로 못 박았으므로 false도 거절 대상이다.
     *
     * <p>알림을 켜 두고 동의는 받지 않은 상태가 저장되면, 나중에 무엇을 근거로 발송했는지
     * 설명할 수 없는 기록이 남는다.
     */
    @AssertTrue(message = "알림을 켜려면 알림 수신 동의와 동의 버전이 함께 있어야 합니다.")
    public boolean isNotificationConsentPresentWhenEnabled() {
        if (!Boolean.TRUE.equals(notificationEnabled)) {
            return true;
        }
        return Boolean.TRUE.equals(notificationConsent)
                && notificationConsentVersion != null
                && !notificationConsentVersion.isBlank();
    }

    /**
     * 알림을 켜는 요청은 피커 값이 함께 와야 한다.
     *
     * <p>없을 때 조용히 기본값을 저장하지 않는 이유는, 사용자가 21:00을 골랐는데 필드가 빠진
     * 요청과 기본값을 그대로 둔 요청을 서버가 구분할 수 없기 때문이다. 앞의 경우 사용자는
     * 고르지 않은 시각에 알림을 받게 된다.
     */
    @AssertTrue(message = "알림을 켜려면 피부 점호 시각을 함께 보내야 합니다.")
    public boolean isNotificationTimePresentWhenEnabled() {
        if (!Boolean.TRUE.equals(notificationEnabled)) {
            return true;
        }
        return notificationTime != null;
    }
}
