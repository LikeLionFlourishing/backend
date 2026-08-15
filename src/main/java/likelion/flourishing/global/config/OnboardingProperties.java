package likelion.flourishing.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 온보딩에서 받아야 하는 동의서 버전.
 *
 * <p>동의 이력은 "이 사용자가 어느 문구에 동의했는가"를 증명하는 기록이다. 클라이언트가 보낸
 * 문자열을 그대로 저장하면 서버가 알지 못하는 버전이 증빙으로 남아 아무것도 증명하지 못한다.
 * 그래서 서버가 활성 버전을 들고 있고, 요청 값이 그것과 같을 때만 이력을 남긴다.
 *
 * <p>문구가 바뀌면 이 값을 올린다. 그러면 예전 버전으로 오는 요청이 거절되어 프런트가 새 동의를
 * 다시 받게 된다. 배포 없이 바꿀 수 있도록 환경변수로 뺀다.
 */
@ConfigurationProperties(prefix = "app.onboarding")
public record OnboardingProperties(String consentVersion) {

    /** 설정이 비어 있으면 명세 예시와 같은 값을 쓴다. */
    private static final String DEFAULT_CONSENT_VERSION = "2026-08-09";

    public OnboardingProperties {
        consentVersion = consentVersion == null || consentVersion.isBlank()
                ? DEFAULT_CONSENT_VERSION
                : consentVersion.trim();
    }

    public boolean isActive(String requested) {
        return consentVersion.equals(requested);
    }
}
