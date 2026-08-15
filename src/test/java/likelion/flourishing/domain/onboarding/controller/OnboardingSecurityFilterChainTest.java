package likelion.flourishing.domain.onboarding.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.auth.security.SessionAuthenticationFilter;
import likelion.flourishing.domain.auth.service.SessionService;
import likelion.flourishing.domain.onboarding.dto.response.OnboardingResponse;
import likelion.flourishing.domain.onboarding.entity.NotificationPermission;
import likelion.flourishing.domain.onboarding.service.OnboardingService;
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
 * 온보딩 엔드포인트를 실제 보안 필터 체인 위에서 확인한다.
 *
 * <p>{@code OnboardingControllerTest}는 addFilters = false로 필터를 꺼 두기 때문에 이 엔드포인트의
 * 인증·CSRF 경계가 검증되지 않는다. 이슈 #7이 약속한 "세션 없으면 401, CSRF 없거나 틀리면 403"은
 * 여기서만 실제로 확인된다.
 *
 * <p>{@code SessionService}만 가짜로 둔다. 세션을 어떻게 조회하는지가 아니라 조회 결과에 따라
 * 필터가 요청을 어떻게 처리하는지가 검증 대상이라 DB에 세션 행을 넣지 않아도 된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OnboardingSecurityFilterChainTest {

    private static final String COOKIE_NAME = "__Host-session";
    private static final String SESSION_TOKEN = "opaque-session-token";
    private static final String CSRF_TOKEN = "csrf-token-value-that-is-long-enough";
    private static final UUID USER_ID = UUID.fromString("2c56fe08-ea1f-45fc-915d-c35b7c0bca39");
    private static final UUID SESSION_ID = UUID.fromString("5ecb88d8-6a21-4a54-8967-72599f078963");
    private static final String BODY = """
            {"consentVersion":"2026-08-09","sensitiveDataConsent":true,
             "notificationEnabled":true,"notificationPermission":"GRANTED"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SessionService sessionService;

    @MockitoBean
    private OnboardingService onboardingService;

    @Test
    void onboardingWithoutSessionCookieIsUnauthorized() throws Exception {
        mockMvc.perform(put("/v1/me/onboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verify(onboardingService, never()).complete(any(), any());
    }

    @Test
    void onboardingWithoutCsrfTokenIsForbidden() throws Exception {
        when(sessionService.authenticate(eq(SESSION_TOKEN), eq("PUT"), isNull()))
                .thenThrow(new BusinessException(ErrorCode.CSRF_TOKEN_INVALID));

        mockMvc.perform(put("/v1/me/onboarding")
                        .cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));

        verify(onboardingService, never()).complete(any(), any());
    }

    @Test
    void onboardingWithWrongCsrfTokenIsForbidden() throws Exception {
        when(sessionService.authenticate(eq(SESSION_TOKEN), eq("PUT"), eq("wrong-token")))
                .thenThrow(new BusinessException(ErrorCode.CSRF_TOKEN_INVALID));

        mockMvc.perform(put("/v1/me/onboarding")
                        .cookie(sessionCookie())
                        .header(SessionAuthenticationFilter.CSRF_HEADER, "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));

        verify(onboardingService, never()).complete(any(), any());
    }

    @Test
    void onboardingWithSessionAndCsrfTokenReachesController() throws Exception {
        when(sessionService.authenticate(eq(SESSION_TOKEN), eq("PUT"), eq(CSRF_TOKEN)))
                .thenReturn(Optional.of(principal()));
        when(onboardingService.complete(any(), any())).thenReturn(response());

        mockMvc.perform(put("/v1/me/onboarding")
                        .cookie(sessionCookie())
                        .header(SessionAuthenticationFilter.CSRF_HEADER, CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consentVersion").value("2026-08-09"));

        verify(onboardingService).complete(any(), any());
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

    private OnboardingResponse response() {
        OffsetDateTime at = OffsetDateTime.of(2026, 8, 11, 7, 0, 0, 0, ZoneOffset.UTC);
        return OnboardingResponse.of("2026-08-09", at, true, NotificationPermission.GRANTED, at);
    }
}
