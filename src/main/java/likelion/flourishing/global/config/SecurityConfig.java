package likelion.flourishing.global.config;

import likelion.flourishing.domain.auth.security.ProblemAccessDeniedHandler;
import likelion.flourishing.domain.auth.security.ProblemAuthenticationEntryPoint;
import likelion.flourishing.domain.auth.security.SessionAuthenticationFilter;
import likelion.flourishing.domain.auth.service.SessionCookieFactory;
import likelion.flourishing.domain.auth.service.SessionService;
import likelion.flourishing.global.exception.ProblemResponseWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/health",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**"
    };

    /**
     * 인증은 세션 쿠키로만 하고, 상태 변경 요청의 CSRF 검증은 명세대로 X-CSRF-Token 헤더로 한다.
     * Spring Security 기본 CSRF 토큰 저장소는 쓰지 않으므로 끄고 {@link SessionAuthenticationFilter}가 대신 검증한다.
     *
     * <p>필터와 오류 처리기는 빈으로 만들지 않고 여기서 직접 조립한다. Filter 빈은 Boot가 서블릿 체인에도
     * 자동 등록해 두 번 실행되기 때문이다.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SessionService sessionService,
            SessionCookieFactory sessionCookieFactory,
            ProblemResponseWriter problemResponseWriter
    ) throws Exception {
        SessionAuthenticationFilter sessionAuthenticationFilter = new SessionAuthenticationFilter(
                sessionService,
                sessionCookieFactory,
                problemResponseWriter
        );

        return http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(new ProblemAuthenticationEntryPoint(problemResponseWriter))
                        .accessDeniedHandler(new ProblemAccessDeniedHandler(problemResponseWriter))
                )
                .addFilterBefore(sessionAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/users").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/sessions").permitAll()
                        .requestMatchers("/v1/sessions/current", "/v1/me").authenticated()
                        .anyRequest().denyAll()
                )
                .formLogin(formLogin -> formLogin.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .logout(logout -> logout.disable())
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
