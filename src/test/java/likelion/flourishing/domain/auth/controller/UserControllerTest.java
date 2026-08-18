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
import likelion.flourishing.global.exception.GlobalExceptionHandler;
import likelion.flourishing.global.exception.ProblemFactory;
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
 * UserController의 HTTP 계약 테스트. 서비스는 가짜(mock)로 두고 요청·응답 모양만 검증한다.
 *
 * <p>확인하는 것: 회원가입 201과 Location·Set-Cookie 헤더, 짧은 비밀번호가 422이고 어느 필드가
 * 틀렸는지 담기는지, 정의되지 않은 필드가 400인지, 계정 삭제가 확인 헤더 없이는 막히고
 * 성공 시 쿠키를 지우는지.
 *
 * <p>addFilters = false로 보안 필터를 끄고 authentication(...)으로 로그인 상태를 흉내 낸다.
 * 필터까지 켜면 쿠키와 세션 조회가 필요해져 컨트롤러 검증에 집중할 수 없기 때문이다.
 */
@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@EnableConfigurationProperties({CorsProperties.class, ProblemProperties.class})
@Import({GlobalExceptionHandler.class, ProblemFactory.class})
class UserControllerTest {

    private static final UUID USER_ID = UUID.fromString("2c56fe08-ea1f-45fc-915d-c35b7c0bca39");
    private static final UUID SESSION_ID = UUID.fromString("5ecb88d8-6a21-4a54-8967-72599f078963");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private SessionCookieFactory sessionCookieFactory;

    @Test
    void registerReturnsCreatedWithLocationAndSessionCookie() throws Exception {
        when(authService.register(any(), anyString())).thenReturn(authSessionIssue());
        when(sessionCookieFactory.create(anyString())).thenReturn(sessionCookie());

        mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"soldier@example.com","password":"correct-horse-battery-staple"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/v1/me"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, Matchers.containsString("__Host-session=opaque")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, Matchers.containsString("HttpOnly")))
                .andExpect(jsonPath("$.user.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.user.signupCompleted").value(false))
                .andExpect(jsonPath("$.csrfToken").value("csrf-token-value-that-is-long-enough"))
                .andExpect(jsonPath("$.expiresAt").exists());
    }

    @Test
    void registerRejectsShortPassword() throws Exception {
        mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"soldier@example.com","password":"short"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("password"));

        verify(authService, never()).register(any(), anyString());
    }

    /**
     * BCrypt는 UTF-8 기준 72바이트까지만 읽는다. 문자 수 제한만 있으면 앞 72바이트가 같은 두
     * 비밀번호가 같은 해시가 되어, 가입할 때 정한 뒷부분이 로그인에서 검사되지 않는다.
     */
    @Test
    void registerRejectsPasswordOverBcryptByteLimit() throws Exception {
        String tooLong = "a".repeat(73);

        mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"soldier@example.com\",\"password\":\"" + tooLong + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("password"));

        verify(authService, never()).register(any(), anyString());
    }

    @Test
    void registerAcceptsPasswordAtBcryptByteLimit() throws Exception {
        when(authService.register(any(), anyString())).thenReturn(authSessionIssue());
        when(sessionCookieFactory.create(anyString())).thenReturn(sessionCookie());

        mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"soldier@example.com\",\"password\":\"" + "a".repeat(72) + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void registerRejectsUndefinedField() throws Exception {
        mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"soldier@example.com","password":"correct-horse-battery-staple","role":"ADMIN"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void getMeReturnsCurrentUser() throws Exception {
        when(authService.getMe(any())).thenReturn(userResponse());

        mockMvc.perform(get("/v1/me").with(authentication(authenticationToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.email").value("soldier@example.com"))
                .andExpect(jsonPath("$.signupCompleted").value(false));
    }

    @Test
    void deleteMeRequiresConfirmationHeader() throws Exception {
        mockMvc.perform(delete("/v1/me").with(authentication(authenticationToken())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DELETE_CONFIRMATION_REQUIRED"));

        verify(authService, never()).deleteMe(any());
    }

    @Test
    void deleteMeRejectsWrongConfirmationValue() throws Exception {
        mockMvc.perform(delete("/v1/me")
                        .header("X-Confirm-Deletion", "yes")
                        .with(authentication(authenticationToken())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DELETE_CONFIRMATION_REQUIRED"));
    }

    @Test
    void deleteMeClearsSessionCookie() throws Exception {
        when(sessionCookieFactory.clear()).thenReturn(clearedCookie());

        mockMvc.perform(delete("/v1/me")
                        .header("X-Confirm-Deletion", "delete-account")
                        .with(authentication(authenticationToken())))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, Matchers.containsString("Max-Age=0")));

        verify(authService).deleteMe(any());
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
        return new AuthSessionIssue(
                AuthSessionResponse.of(
                        userResponse(),
                        "csrf-token-value-that-is-long-enough",
                        OffsetDateTime.of(2026, 8, 24, 0, 0, 0, 0, ZoneOffset.UTC)
                ),
                "opaque"
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
