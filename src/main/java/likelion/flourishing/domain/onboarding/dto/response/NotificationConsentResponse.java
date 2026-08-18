package likelion.flourishing.domain.onboarding.dto.response;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import likelion.flourishing.domain.onboarding.entity.UserConsent;

/**
 * 명세 NotificationConsent. 알림 수신 동의 2/2의 기록이다.
 *
 * <p>{@code agreedAt}은 명세가 필수이면서 nullable로 둔 필드라 동의하지 않은 경우에도
 * {@code null}로 내보내야 한다. 그래서 이 클래스에는 NON_NULL을 붙이지 않는다.
 *
 * <p>동의하지 않은 사용자에게도 {@code version}을 채워야 하는데 남은 행이 없다.
 * 이때는 서버가 지금 받고 있는 활성 버전을 그대로 쓴다. "이 문구에 대해 동의하지 않았다"는
 * 뜻이라 증빙으로도 어긋나지 않는다.
 */
public record NotificationConsentResponse(boolean agreed, String version, OffsetDateTime agreedAt) {

    public static NotificationConsentResponse agreed(UserConsent consent) {
        return new NotificationConsentResponse(
                true,
                consent.getConsentVersion(),
                consent.getConsentedAt().atOffset(ZoneOffset.UTC)
        );
    }

    public static NotificationConsentResponse notAgreed(String activeVersion) {
        return new NotificationConsentResponse(false, activeVersion, null);
    }

    public static NotificationConsentResponse of(boolean agreed, String version, LocalDateTime agreedAt) {
        return new NotificationConsentResponse(
                agreed,
                version,
                agreedAt == null ? null : agreedAt.atOffset(ZoneOffset.UTC)
        );
    }
}
