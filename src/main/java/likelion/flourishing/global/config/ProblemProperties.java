package likelion.flourishing.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Problem 응답의 type URI 접두사. 명세 예시는 {@code https://api.example.invalid/problems} 형식이다.
 */
@ConfigurationProperties(prefix = "app.problem")
public record ProblemProperties(String baseUri) {

    public ProblemProperties {
        baseUri = baseUri == null || baseUri.isBlank()
                ? "https://api.example.invalid/problems"
                : baseUri.replaceAll("/+$", "");
    }
}
