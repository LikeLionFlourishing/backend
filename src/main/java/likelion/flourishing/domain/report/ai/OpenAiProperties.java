package likelion.flourishing.domain.report.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenAI Responses API 호출 설정.
 *
 * <p>키나 모델이 비어 있으면 애플리케이션은 그대로 뜨고 AI 호출만 실패로 처리한다. 로컬 개발과
 * 테스트에서 키 없이 나머지 기능을 쓸 수 있어야 하고, 명세도 AI 실패를 정상 흐름으로 다룬다.
 *
 * <p>readTimeout을 짧게 두는 이유는 구조화가 사용자를 기다리게 하는 화면 앞단 작업이기 때문이다.
 * 오래 붙잡고 있는 것보다 실패로 돌려 직접 선택하게 하는 편이 낫다.
 */
@ConfigurationProperties(prefix = "app.ai.openai")
public record OpenAiProperties(
        String baseUrl,
        String apiKey,
        String model,
        Duration connectTimeout,
        Duration readTimeout,
        int maxOutputTokens,
        RateLimit rateLimit
) {

    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private static final int DEFAULT_MAX_OUTPUT_TOKENS = 800;

    public OpenAiProperties {
        baseUrl = hasText(baseUrl) ? stripTrailingSlash(baseUrl) : DEFAULT_BASE_URL;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(8) : readTimeout;
        maxOutputTokens = maxOutputTokens <= 0 ? DEFAULT_MAX_OUTPUT_TOKENS : maxOutputTokens;
        rateLimit = rateLimit == null ? RateLimit.defaults() : rateLimit;
    }

    /**
     * 구조화 요청 제한.
     *
     * <p>구조화는 사용자가 한 화면에서 여러 번 부를 수 있고 호출마다 외부 비용이 든다. 보고 생성은
     * 하루 한 건, 설명 재생성은 결과당 한 번으로 자연히 묶이는데 이 경로만 상한이 없다.
     */
    public record RateLimit(int limit, Duration window) {

        public RateLimit {
            limit = limit <= 0 ? 30 : limit;
            window = window == null ? Duration.ofHours(1) : window;
        }

        private static RateLimit defaults() {
            return new RateLimit(30, Duration.ofHours(1));
        }
    }

    /** 키와 모델이 모두 있어야 호출을 시도한다. 없으면 곧바로 실패로 처리한다. */
    public boolean configured() {
        return hasText(apiKey) && hasText(model);
    }

    public String responsesEndpoint() {
        return baseUrl + "/responses";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String stripTrailingSlash(String value) {
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
