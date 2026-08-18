package likelion.flourishing.domain.auth.security;

import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import likelion.flourishing.domain.auth.dto.response.UserResponse;
import likelion.flourishing.domain.auth.service.AuthService;
import likelion.flourishing.domain.auth.service.SessionService;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 실제 보안 필터 체인을 켜고 도는 통합 테스트. 컨트롤러 테스트가 addFilters = false로 필터를 꺼 두기
 * 때문에, 인증·CSRF 경계는 여기서만 검증된다.
 *
 * <p>확인하는 것: 세션 쿠키 없는 요청이 401인지, 유효한 세션 쿠키가 통과하는지, 로그아웃해서
 * 사라진 쿠키를 다시 써도 401인지, 상태 변경 요청에 CSRF 토큰이 없거나 틀리면 403인지,
 * 화이트리스트에 없는 경로가 막히는지, 오류 응답이 명세의 problem+json 형식인지.
 *
 * <p>SessionService만 가짜로 둔다. 필터가 세션을 어떻게 조회하는지가 아니라, 조회 결과에 따라
 * 요청을 어떻게 처리하는지가 검증 대상이기 때문이다. DB에 세션 행을 넣지 않아도 된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityFilterChainTest {

    private static final String COOKIE_NAME = "__Host-session";
    private static final String SESSION_TOKEN = "opaque-session-token";
    private static final String CSRF_TOKEN = "csrf-token-value-that-is-long-enough";
    private static final UUID USER_ID = UUID.fromString("2c56fe08-ea1f-45fc-915d-c35b7c0bca39");
    private static final UUID SESSION_ID = UUID.fromString("5ecb88d8-6a21-4a54-8967-72599f078963");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SessionService sessionService;

    @MockitoBean
    private AuthService authService;

    @Test
    void requestWithoutSessionCookieIsUnauthorized() throws Exception {
        mockMvc.perform(get("/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.status").value(401));

        verify(authService, never()).getMe(any());
    }

    @Test
    void requestWithValidSessionCookieReachesController() throws Exception {
        when(sessionService.authenticate(eq(SESSION_TOKEN), eq("GET"), isNull()))
                .thenReturn(Optional.of(principal()));
        when(authService.getMe(any())).thenReturn(userResponse());

        mockMvc.perform(get("/v1/me").cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()));
    }

    @Test
    void revokedSessionCookieIsUnauthorized() throws Exception {
        when(sessionService.authenticate(anyString(), anyString(), any()))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/v1/me").cookie(sessionCookie()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verify(authService, never()).getMe(any());
    }

    @Test
    void stateChangingRequestWithoutCsrfTokenIsForbidden() throws Exception {
        when(sessionService.authenticate(eq(SESSION_TOKEN), eq("DELETE"), isNull()))
                .thenThrow(new BusinessException(ErrorCode.CSRF_TOKEN_INVALID));

        mockMvc.perform(delete("/v1/sessions/current").cookie(sessionCookie()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));

        verify(authService, never()).logout(any());
    }

    @Test
    void stateChangingRequestWithWrongCsrfTokenIsForbidden() throws Exception {
        when(sessionService.authenticate(eq(SESSION_TOKEN), eq("DELETE"), eq("wrong-token")))
                .thenThrow(new BusinessException(ErrorCode.CSRF_TOKEN_INVALID));

        mockMvc.perform(delete("/v1/sessions/current")
                        .cookie(sessionCookie())
                        .header(SessionAuthenticationFilter.CSRF_HEADER, "wrong-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));

        verify(authService, never()).logout(any());
    }

    @Test
    void stateChangingRequestWithCsrfTokenPasses() throws Exception {
        when(sessionService.authenticate(eq(SESSION_TOKEN), eq("DELETE"), eq(CSRF_TOKEN)))
                .thenReturn(Optional.of(principal()));

        mockMvc.perform(delete("/v1/sessions/current")
                        .cookie(sessionCookie())
                        .header(SessionAuthenticationFilter.CSRF_HEADER, CSRF_TOKEN))
                .andExpect(status().isNoContent());

        verify(authService).logout(any());
    }

    @Test
    void pathOutsideTheWhitelistIsDenied() throws Exception {
        mockMvc.perform(get("/v1/not-registered-yet"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void skinRecordQueriesRequireAuthentication() throws Exception {
        mockMvc.perform(get("/v1/skin-reports"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        mockMvc.perform(get("/v1/skin-reports/0198a31f-f33f-7000-8000-000000000001"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    /**
     * Spring MVC가 4xx로 정의한 예외가 500으로 새지 않는지 확인한다. GlobalExceptionHandler가
     * Exception 캐치올을 들고 있어 따로 받지 않으면 전부 500이 된다.
     */
    @Test
    void unmappedMethodOnAnOpenPathIsMethodNotAllowed() throws Exception {
        when(sessionService.authenticate(eq(SESSION_TOKEN), eq("PUT"), isNull()))
                .thenReturn(Optional.of(principal()));

        mockMvc.perform(put("/v1/me").cookie(sessionCookie()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void requestWithoutContentTypeIsUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/v1/sessions").content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    /**
     * 인증이 필요 없는 경로는 세션 쿠키를 보지 않는다. 살아 있는 쿠키를 들고 로그인 화면을 다시
     * 제출하는 흐름이 CSRF 검사에 걸려 403이 되면 사용자는 원인을 알 수 없다.
     */
    @Test
    void loginWithALiveSessionCookieIsNotBlockedByCsrf() throws Exception {
        mockMvc.perform(post("/v1/sessions")
                        .cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"soldier@example.com\",\"password\":\"correct-horse-battery-staple\"}"))
                .andExpect(status().is(not(403)));

        verify(sessionService, never()).authenticate(anyString(), anyString(), any());
    }

    private Cookie sessionCookie() {
        return new Cookie(COOKIE_NAME, SESSION_TOKEN);
    }

    private AuthenticatedUser principal() {
        return new AuthenticatedUser(
                USER_ID,
                SESSION_ID,
                LocalDateTime.of(2026, 8, 24, 0, 0),
                CSRF_TOKEN
        );
    }

    private UserResponse userResponse() {
        return UserResponse.of(
                USER_ID,
                "soldier@example.com",
                false,
                OffsetDateTime.of(2026, 8, 10, 0, 0, 0, 0, ZoneOffset.UTC)
        );
    }
}
