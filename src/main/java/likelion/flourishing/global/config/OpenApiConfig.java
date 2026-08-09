package likelion.flourishing.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI flourishingOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("관리하는 행보관 API")
                        .version("v1")
                        .description("현역 장병용 피부 셀프케어 PWA API"));
    }
}
