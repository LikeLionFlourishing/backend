package likelion.flourishing.global.config;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 브라우저에서 다른 출처의 프론트엔드가 이 API를 부를 수 있게 하는 CORS 설정.
 *
 * <p>세션을 쿠키로 주고받으므로 allowCredentials가 true다. 그러면 브라우저 규칙상
 * 허용 출처에 와일드카드를 쓸 수 없어서 프론트 주소를 설정값으로 하나씩 지정한다.
 * 목록이 비어 있으면 매핑 자체를 등록하지 않아 교차 출처 요청이 모두 막힌다.
 *
 * <p>allowedHeaders는 프론트가 보낼 수 있는 헤더, exposedHeaders는 프론트가 읽을 수 있는
 * 응답 헤더다. X-CSRF-Token을 보내야 상태 변경이 되고, Location과 X-RateLimit-*를 읽어야
 * 생성된 리소스 위치와 남은 요청 수를 알 수 있어서 각각 열어 뒀다.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final CorsProperties corsProperties;

    public CorsConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> allowedOrigins = corsProperties.allowedOrigins();
        if (allowedOrigins.isEmpty()) {
            return;
        }

        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods(
                        HttpMethod.GET.name(),
                        HttpMethod.POST.name(),
                        HttpMethod.PUT.name(),
                        HttpMethod.PATCH.name(),
                        HttpMethod.DELETE.name(),
                        HttpMethod.OPTIONS.name()
                )
                .allowedHeaders(
                        HttpHeaders.CONTENT_TYPE,
                        HttpHeaders.ACCEPT,
                        "X-CSRF-Token",
                        "Idempotency-Key",
                        "X-Confirm-Deletion"
                )
                .exposedHeaders(
                        HttpHeaders.LOCATION,
                        HttpHeaders.RETRY_AFTER,
                        "X-RateLimit-Limit",
                        "X-RateLimit-Remaining",
                        "X-RateLimit-Reset"
                )
                .allowCredentials(true)
                .maxAge(3600);
    }
}
