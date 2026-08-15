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

    /**
     * 값이 비어 있으면 기동을 막는다. 소스에 박아 둔 옛 버전으로 조용히 되돌아가면, 운영자가
     * 환경변수를 비운 사고가 "아무 일도 없는 것"처럼 보이면서 실제로는 지난 문구에 대한 동의가
     * 계속 쌓인다. 기본값은 application.yml 한 곳에만 둔다.
     */
    public OnboardingProperties {
        if (consentVersion == null || consentVersion.isBlank()) {
            throw new IllegalStateException("ONBOARDING_CONSENT_VERSION must be configured");
        }
        consentVersion = consentVersion.trim();
    }

    /** 요청 값도 서버 값과 같은 방식으로 정규화해서 비교한다. 앞뒤 공백 때문에 거절하지 않는다. */
    public boolean isActive(String requested) {
        return requested != null && consentVersion.equals(requested.trim());
    }
}
