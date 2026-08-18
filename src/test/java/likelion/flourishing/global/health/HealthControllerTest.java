package likelion.flourishing.global.health;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import likelion.flourishing.domain.auth.service.SessionCookieFactory;
import likelion.flourishing.domain.auth.service.SessionService;
import likelion.flourishing.global.config.AuthProperties;
import likelion.flourishing.global.config.CorsProperties;
import likelion.flourishing.global.config.ProblemProperties;
import likelion.flourishing.global.config.SecurityConfig;
import likelion.flourishing.global.exception.ProblemFactory;
import likelion.flourishing.global.exception.ProblemResponseWriter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HealthController.class)
@Import({SecurityConfig.class, ProblemFactory.class})
@EnableConfigurationProperties({CorsProperties.class, AuthProperties.class, ProblemProperties.class})
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SessionService sessionService;

    @MockitoBean
    private SessionCookieFactory sessionCookieFactory;

    @MockitoBean
    private ProblemResponseWriter problemResponseWriter;

    @Test
    void healthReturnsOk() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }
}
