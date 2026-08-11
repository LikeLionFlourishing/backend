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

    private final OffsetDateTime completedAt;

    public static OnboardingResponse of(
            String consentVersion,
            OffsetDateTime consentedAt,
            boolean notificationEnabled,
            NotificationPermission notificationPermission,
            OffsetDateTime completedAt
    ) {
        return new OnboardingResponse(
                consentVersion,
                consentedAt,
                notificationEnabled,
                notificationPermission,
                completedAt
        );
    }

    public static OnboardingResponse from(
            UserConsent consent,
            NotificationSetting notificationSetting,
            LocalDateTime completedAt
    ) {
        return new OnboardingResponse(
                consent.getConsentVersion(),
                consent.getConsentedAt().atOffset(ZoneOffset.UTC),
                notificationSetting.isEnabled(),
                notificationSetting.getPermissionState(),
                completedAt.atOffset(ZoneOffset.UTC)
        );
    }
}
