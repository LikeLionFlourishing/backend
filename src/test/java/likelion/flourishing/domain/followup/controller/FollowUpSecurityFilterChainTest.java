package likelion.flourishing.domain.followup.controller;

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
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.auth.security.SessionAuthenticationFilter;
import likelion.flourishing.domain.auth.service.SessionService;
import likelion.flourishing.domain.followup.dto.response.FollowUpResponse;
import likelion.flourishing.domain.followup.entity.ActionCompletion;
import likelion.flourishing.domain.followup.entity.FollowUpKind;
import likelion.flourishing.domain.followup.entity.SkinChange;
import likelion.flourishing.domain.followup.service.FollowUpService;
import likelion.flourishing.domain.followup.service.SavedFollowUp;
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
 * 경과 엔드포인트를 실제 보안 필터 체인 위에서 확인한다.
 *
 * <p>{@code FollowUpControllerTest}는 addFilters = false로 필터를 꺼 두기 때문에 이번에 추가한
 * SecurityConfig 매처와 PUT의 CSRF 검사가 그쪽에서는 한 번도 실행되지 않는다.
 *
 * <p>{@code SessionService}만 가짜로 둔다. 세션을 어떻게 조회하는지가 아니라 조회 결과에 따라
 * 필터가 요청을 어떻게 처리하는지가 검증 대상이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FollowUpSecurityFilterChainTest {

    private static final String COOKIE_NAME = "__Host-session";
    private static final String SESSION_TOKEN = "opaque-session-token";
    private static final String CSRF_TOKEN = "csrf-token-value-that-is-long-enough";
    private static final UUID USER_ID = UUID.fromString("2c56fe08-ea1f-45fc-915d-c35b7c0bca39");
    private static final UUID SESSION_ID = UUID.fromString("5ecb88d8-6a21-4a54-8967-72599f078963");
    private static final UUID REPORT_ID = UUID.fromString("9a1d3f52-1f0b-4a44-9d2e-6e1d0c7a51bb");
    private static final String PATH = "/v1/skin-reports/{reportId}/follow-up";
    private static final String BODY = """
            {"kind":"SELF_CARE","skinChange":"IMPROVED","actionCompletion":"MOSTLY_DONE"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SessionService sessionService;

    @MockitoBean
    private FollowUpService followUpService;

    @Test
    void getFollowUpWithoutSessionCookieIsUnauthorized() throws Exception {
        mockMvc.perform(get(PATH, REPORT_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verify(followUpService, never()).getFollowUp(any(), any());
    }

    @Test
    void saveFollowUpWithoutSessionCookieIsUnauthorized() throws Exception {
        mockMvc.perform(put(PATH, REPORT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verify(followUpService, never()).saveFollowUp(any(), any(), any());
    }

    @Test
    void saveFollowUpWithoutCsrfTokenIsForbidden() throws Exception {
        when(sessionService.authenticate(eq(SESSION_TOKEN), eq("PUT"), isNull()))
                .thenThrow(new BusinessException(ErrorCode.CSRF_TOKEN_INVALID));

        mockMvc.perform(put(PATH, REPORT_ID)
                        .cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));

        verify(followUpService, never()).saveFollowUp(any(), any(), any());
    }

    @Test
    void saveFollowUpWithWrongCsrfTokenIsForbidden() throws Exception {
        when(sessionService.authenticate(eq(SESSION_TOKEN), eq("PUT"), eq("wrong-token")))
                .thenThrow(new BusinessException(ErrorCode.CSRF_TOKEN_INVALID));

        mockMvc.perform(put(PATH, REPORT_ID)
                        .cookie(sessionCookie())
                        .header(SessionAuthenticationFilter.CSRF_HEADER, "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));

        verify(followUpService, never()).saveFollowUp(any(), any(), any());
    }

    @Test
    void getFollowUpWithValidSessionCookieReachesController() throws Exception {
        when(sessionService.authenticate(eq(SESSION_TOKEN), eq("GET"), isNull()))
                .thenReturn(Optional.of(principal()));
        when(followUpService.getFollowUp(any(), any())).thenReturn(response());

        mockMvc.perform(get(PATH, REPORT_ID).cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("SELF_CARE"));
    }

    @Test
    void saveFollowUpWithSessionAndCsrfTokenReachesController() throws Exception {
        when(sessionService.authenticate(eq(SESSION_TOKEN), eq("PUT"), eq(CSRF_TOKEN)))
                .thenReturn(Optional.of(principal()));
        when(followUpService.saveFollowUp(any(), any(), any()))
                .thenReturn(new SavedFollowUp(response(), true));

        mockMvc.perform(put(PATH, REPORT_ID)
                        .cookie(sessionCookie())
                        .header(SessionAuthenticationFilter.CSRF_HEADER, CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.skinChange").value("IMPROVED"));

        verify(followUpService).saveFollowUp(any(), any(), any());
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

    private FollowUpResponse response() {
        return FollowUpResponse.of(
                REPORT_ID,
                FollowUpKind.SELF_CARE,
                SkinChange.IMPROVED,
                ActionCompletion.MOSTLY_DONE,
                null,
                OffsetDateTime.of(2026, 8, 12, 9, 0, 0, 0, ZoneOffset.UTC)
        );
    }
}
