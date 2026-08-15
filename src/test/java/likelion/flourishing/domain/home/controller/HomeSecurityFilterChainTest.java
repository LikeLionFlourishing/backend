package likelion.flourishing.domain.home.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.auth.security.SessionAuthenticationFilter;
import likelion.flourishing.domain.auth.service.SessionService;
import likelion.flourishing.domain.home.dto.response.DailyCheckInResponse;
import likelion.flourishing.domain.home.dto.response.HomeResponse;
import likelion.flourishing.domain.home.entity.CheckInState;
import likelion.flourishing.domain.home.entity.HomePriority;
import likelion.flourishing.domain.home.service.HomeService;
import likelion.flourishing.domain.home.service.SavedDailyCheckIn;
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
 * 홈 엔드포인트를 실제 보안 필터 체인 위에서 확인한다.
 *
 * <p>{@code HomeControllerTest}는 addFilters = false로 필터를 꺼 두기 때문에 이번에 추가한
 * SecurityConfig 매처와 PUT의 CSRF 검사가 그쪽에서는 한 번도 실행되지 않는다.
 *
 * <p>{@code SessionService}만 가짜로 둔다. 세션을 어떻게 조회하는지가 아니라 조회 결과에 따라
 * 필터가 요청을 어떻게 처리하는지가 검증 대상이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HomeSecurityFilterChainTest {

    private static final String COOKIE_NAME = "__Host-session";
    private static final String SESSION_TOKEN = "opaque-session-token";
    private static final String CSRF_TOKEN = "csrf-token-value-that-is-long-enough";
    private static final UUID USER_ID = UUID.fromString("2c56fe08-ea1f-45fc-915d-c35b7c0bca39");
    private static final UUID SESSION_ID = UUID.fromString("5ecb88d8-6a21-4a54-8967-72599f078963");
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 12);
    private static final String BODY = "{\"state\":\"NO_DISCOMFORT\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SessionService sessionService;

    @MockitoBean
    private HomeService homeService;

    @Test
    void homeWithoutSessionCookieIsUnauthorized() throws Exception {
        mockMvc.perform(get("/v1/home"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verify(homeService, never()).getHome(any());
    }

    @Test
    void checkInWithoutSessionCookieIsUnauthorized() throws Exception {
        mockMvc.perform(put("/v1/daily-check-ins/{date}", TODAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verify(homeService, never()).saveNoDiscomfort(any(), any(), any());
    }

    @Test
    void checkInWithoutCsrfTokenIsForbidden() throws Exception {
        when(sessionService.authenticate(eq(SESSION_TOKEN), eq("PUT"), isNull()))
                .thenThrow(new BusinessException(ErrorCode.CSRF_TOKEN_INVALID));

        mockMvc.perform(put("/v1/daily-check-ins/{date}", TODAY)
                        .cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));

        verify(homeService, never()).saveNoDiscomfort(any(), any(), any());
    }

    @Test
    void checkInWithWrongCsrfTokenIsForbidden() throws Exception {
        when(sessionService.authenticate(eq(SESSION_TOKEN), eq("PUT"), eq("wrong-token")))
                .thenThrow(new BusinessException(ErrorCode.CSRF_TOKEN_INVALID));

        mockMvc.perform(put("/v1/daily-check-ins/{date}", TODAY)
                        .cookie(sessionCookie())
                        .header(SessionAuthenticationFilter.CSRF_HEADER, "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));

        verify(homeService, never()).saveNoDiscomfort(any(), any(), any());
    }

    @Test
    void homeWithValidSessionCookieReachesController() throws Exception {
        when(sessionService.authenticate(eq(SESSION_TOKEN), eq("GET"), isNull()))
                .thenReturn(Optional.of(principal()));
        when(homeService.getHome(any())).thenReturn(HomeResponse.of(
                TODAY, HomePriority.EMPTY, null, null, null
        ));

        mockMvc.perform(get("/v1/home").cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serverDate").value("2026-08-12"))
                .andExpect(jsonPath("$.priority").value("EMPTY"));
    }

    @Test
    void checkInWithSessionAndCsrfTokenReachesController() throws Exception {
        when(sessionService.authenticate(eq(SESSION_TOKEN), eq("PUT"), eq(CSRF_TOKEN)))
                .thenReturn(Optional.of(principal()));
        when(homeService.saveNoDiscomfort(any(), any(), any())).thenReturn(new SavedDailyCheckIn(
                DailyCheckInResponse.of(
                        TODAY,
                        CheckInState.NO_DISCOMFORT,
                        null,
                        OffsetDateTime.of(2026, 8, 12, 7, 0, 0, 0, ZoneOffset.UTC)
                ),
                true
        ));

        mockMvc.perform(put("/v1/daily-check-ins/{date}", TODAY)
                        .cookie(sessionCookie())
                        .header(SessionAuthenticationFilter.CSRF_HEADER, CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("NO_DISCOMFORT"));

        verify(homeService).saveNoDiscomfort(any(), any(), any());
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
}
