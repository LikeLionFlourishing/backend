package likelion.flourishing.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String COOKIE_AUTH = "cookieAuth";
    private static final String CSRF_TOKEN = "csrfToken";

    @Bean
    public OpenAPI flourishingOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("관리하는 행보관 API")
                        .version("v1")
                        .description("현역 장병용 피부 셀프케어 PWA API"))
                .components(new Components()
                        .addSecuritySchemes(COOKIE_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("__Host-session")
                                .description("Secure, HttpOnly, SameSite=Lax 세션 쿠키"))
                        .addSecuritySchemes(CSRF_TOKEN, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-CSRF-Token")
                                .description("현재 세션 응답에서 받은 CSRF 토큰")))
                .addSecurityItem(new SecurityRequirement().addList(COOKIE_AUTH));
    }
}
