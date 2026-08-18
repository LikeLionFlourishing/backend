package likelion.flourishing.domain.onboarding.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import likelion.flourishing.domain.onboarding.entity.NotificationPermission;
import likelion.flourishing.domain.onboarding.entity.NotificationSetting;
import likelion.flourishing.domain.onboarding.entity.UserConsent;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 명세 Onboarding 스키마. */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class OnboardingResponse {

    private final String consentVersion;

    private final OffsetDateTime consentedAt;

    private final boolean notificationEnabled;

    private final NotificationPermission notificationPermission;

    /** 온보딩 시간 피커에서 정한 기본 피부 점호 시각. 알림을 끈 사용자도 값을 가진다. */
    private final String notificationTime;

    private final NotificationConsentResponse notificationConsent;

    private final OffsetDateTime completedAt;

    public static OnboardingResponse of(
            String consentVersion,
            OffsetDateTime consentedAt,
            boolean notificationEnabled,
            NotificationPermission notificationPermission,
            String notificationTime,
            NotificationConsentResponse notificationConsent,
            OffsetDateTime completedAt
    ) {
        return new OnboardingResponse(
                consentVersion,
                consentedAt,
                notificationEnabled,
                notificationPermission,
                notificationTime,
                notificationConsent,
                completedAt
        );
    }

    public static OnboardingResponse from(
            UserConsent consent,
            NotificationSetting notificationSetting,
            NotificationConsentResponse notificationConsent,
            LocalDateTime completedAt
    ) {
        return new OnboardingResponse(
                consent.getConsentVersion(),
                consent.getConsentedAt().atOffset(ZoneOffset.UTC),
                notificationSetting.isEnabled(),
                notificationSetting.getPermissionState(),
                notificationSetting.getNotificationTime(),
                notificationConsent,
                completedAt.atOffset(ZoneOffset.UTC)
        );
    }
}
