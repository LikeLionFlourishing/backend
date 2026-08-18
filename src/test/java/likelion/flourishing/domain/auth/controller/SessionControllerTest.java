package likelion.flourishing.domain.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import likelion.flourishing.domain.auth.dto.response.AuthSessionResponse;
import likelion.flourishing.domain.auth.dto.response.UserResponse;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.auth.service.AuthService;
import likelion.flourishing.domain.auth.service.AuthSessionIssue;
import likelion.flourishing.domain.auth.service.SessionCookieFactory;
import likelion.flourishing.global.config.CorsProperties;
import likelion.flourishing.global.config.ProblemProperties;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import likelion.flourishing.global.exception.GlobalExceptionHandler;
import likelion.flourishing.global.exception.ProblemFactory;
import likelion.flourishing.global.exception.TooManyRequestsException;
import likelion.flourishing.support.RateLimitResult;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * SessionController의 HTTP 계약 테스트. 서비스는 가짜(mock)로 두고 요청·응답 모양만 검증한다.
 *
 * <p>확인하는 것: 로그인 201과 세션 쿠키, 비밀번호가 틀렸을 때 problem+json 형식과
 * INVALID_CREDENTIALS 코드, 요청 제한에 걸렸을 때 Retry-After와 X-RateLimit-* 헤더,
 * 현재 세션 조회가 CSRF 토큰을 돌려주는지, 로그아웃이 만료된 쿠키를 내리는지.
 */
@WebMvcTest(SessionController.class)
@AutoConfigureMockMvc(addFilters = false)
@EnableConfigurationProperties({CorsProperties.class, ProblemProperties.class})
@Import({GlobalExceptionHandler.class, ProblemFactory.class})
class SessionControllerTest {

    private static final UUID USER_ID = UUID.fromString("2c56fe08-ea1f-45fc-915d-c35b7c0bca39");
    private static final UUID SESSION_ID = UUID.fromString("5ecb88d8-6a21-4a54-8967-72599f078963");
    private static final String LOGIN_BODY = """
            {"email":"soldier@example.com","password":"correct-horse-battery-staple"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private SessionCookieFactory sessionCookieFactory;

    @Test
    void loginReturnsCreatedWithSessionCookie() throws Exception {
        when(authService.login(any(), anyString())).thenReturn(authSessionIssue());
        when(sessionCookieFactory.create(anyString())).thenReturn(sessionCookie());

        mockMvc.perform(post("/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, Matchers.containsString("__Host-session=opaque")))
                .andExpect(jsonPath("$.csrfToken").value("csrf-token-value-that-is-long-enough"))
                .andExpect(jsonPath("$.user.email").value("soldier@example.com"));
    }

    @Test
    void loginWithWrongCredentialsReturnsProblemJson() throws Exception {
        when(authService.login(any(), anyString()))
                .thenThrow(new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        mockMvc.perform(post("/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type",
                        Matchers.startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                .andExpect(jsonPath("$.type")
                        .value("https://api.example.invalid/problems/invalid-credentials"))
                .andExpect(jsonPath("$.title").value("인증 실패"))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.requestId").value(Matchers.startsWith("req_")));
    }

    @Test
    void loginBeyondRateLimitReturnsRetryHeaders() throws Exception {
        when(authService.login(any(), anyString()))
                .thenThrow(new TooManyRequestsException(new RateLimitResult(false, 20, 0, 42, 1_800_000_000L)));

        mockMvc.perform(post("/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "42"))
                .andExpect(header().string("X-RateLimit-Limit", "20"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(header().string("X-RateLimit-Reset", "1800000000"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    /**
     * BCrypt가 72바이트까지만 읽으므로 가입에서 그 길이를 넘는 비밀번호를 막는다. 로그인도 같이
     * 막지 않으면 잘려 읽힌 앞부분만 맞아도 통과하는 길이 남는다.
     */
    @Test
    void loginRejectsPasswordOverBcryptByteLimit() throws Exception {
        mockMvc.perform(post("/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"soldier@example.com\",\"password\":\"" + "a".repeat(73) + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("password"));

        verify(authService, never()).login(any(), anyString());
    }

    @Test
    void getCurrentSessionReturnsCsrfToken() throws Exception {
        when(authService.currentSession(any())).thenReturn(authSessionIssue().session());

        mockMvc.perform(get("/v1/sessions/current").with(authentication(authenticationToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.csrfToken").value("csrf-token-value-that-is-long-enough"))
                .andExpect(jsonPath("$.expiresAt").exists());
    }

    @Test
    void logoutClearsSessionCookie() throws Exception {
        when(sessionCookieFactory.clear()).thenReturn(clearedCookie());

        mockMvc.perform(delete("/v1/sessions/current").with(authentication(authenticationToken())))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, Matchers.containsString("Max-Age=0")));

        verify(authService).logout(any());
    }

    private UsernamePasswordAuthenticationToken authenticationToken() {
        AuthenticatedUser principal = new AuthenticatedUser(
                USER_ID,
                SESSION_ID,
                LocalDateTime.of(2026, 8, 24, 0, 0),
                "csrf-token-value-that-is-long-enough"
        );
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
    }

    private AuthSessionIssue authSessionIssue() {
        UserResponse user = UserResponse.of(
                USER_ID,
                "soldier@example.com",
                false,
                OffsetDateTime.of(2026, 8, 10, 0, 0, 0, 0, ZoneOffset.UTC)
        );
        return new AuthSessionIssue(
                AuthSessionResponse.of(
                        user,
                        "csrf-token-value-that-is-long-enough",
                        OffsetDateTime.of(2026, 8, 24, 0, 0, 0, 0, ZoneOffset.UTC)
                ),
                "opaque"
        );
    }

    private ResponseCookie sessionCookie() {
        return ResponseCookie.from("__Host-session", "opaque")
                .path("/")
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .build();
    }

    private ResponseCookie clearedCookie() {
        return ResponseCookie.from("__Host-session", "")
                .path("/")
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .maxAge(0)
                .build();
    }
}
