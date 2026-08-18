package likelion.flourishing.global.config;

import jakarta.servlet.DispatcherType;
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

/**
 * Spring Security 설정. 어떤 경로를 누가 부를 수 있는지와 인증 실패 응답 형식을 정한다.
 *
 * <p>접근 정책은 화이트리스트 방식이다. 마지막이 anyRequest().denyAll()이라 위에 적지 않은
 * 경로는 전부 막힌다. 새 엔드포인트를 만들면 여기에 한 줄 추가해야 하고, 빠뜨리면 403이 난다.
 * requestMatchers에 적은 경로는 정확히 일치할 때만 걸리므로 상위 경로를 적어도 그 하위 경로까지
 * 함께 열리지는 않는다. 하위 경로가 필요하면 따로 적어야 한다.
 *
 * <p>로그인 상태는 서버 세션이나 JWT가 아니라 DB에 저장한 세션 행과 쿠키로만 판단한다.
 * 그래서 폼 로그인, HTTP Basic, 기본 로그아웃 처리를 모두 끄고 세션 정책도 STATELESS로 둔다.
 */
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
                        // 오류 포워드에도 인가가 적용되면 필터 밖에서 난 500이 401로 덮인다.
                        // 프런트엔드는 그것을 세션 만료로 읽고 사용자를 로그아웃시킨다.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/users").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/sessions").permitAll()
                        .requestMatchers("/v1/sessions/current", "/v1/me").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/v1/me/onboarding").authenticated()
                        .requestMatchers(HttpMethod.GET, "/v1/home").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/v1/daily-check-ins/*").authenticated()
                        .requestMatchers(HttpMethod.GET, "/v1/me/notification-settings").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/v1/me/notification-settings").authenticated()
                        .requestMatchers(HttpMethod.POST, "/v1/push-subscriptions").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/v1/push-subscriptions/*").authenticated()
                        .requestMatchers(HttpMethod.POST, "/v1/report-interpretations").authenticated()
                        .requestMatchers(HttpMethod.GET, "/v1/skin-reports").authenticated()
                        .requestMatchers(HttpMethod.POST, "/v1/skin-reports").authenticated()
                        .requestMatchers(HttpMethod.GET, "/v1/skin-reports/*").authenticated()
                        .requestMatchers(HttpMethod.POST, "/v1/skin-reports/*/care-guide-generations")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/v1/skin-reports/*/follow-up").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/v1/skin-reports/*/follow-up").authenticated()
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
