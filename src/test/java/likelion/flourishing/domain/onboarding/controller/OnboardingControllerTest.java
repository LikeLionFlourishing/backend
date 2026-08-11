package likelion.flourishing.domain.onboarding.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.onboarding.dto.response.OnboardingResponse;
import likelion.flourishing.domain.onboarding.entity.NotificationPermission;
import likelion.flourishing.domain.onboarding.service.OnboardingService;
import likelion.flourishing.global.config.CorsProperties;
import likelion.flourishing.global.config.ProblemProperties;
import likelion.flourishing.global.exception.GlobalExceptionHandler;
import likelion.flourishing.global.exception.ProblemFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * OnboardingController의 HTTP 계약 테스트. 서비스는 가짜(mock)로 두고 요청·응답 모양만 검증한다.
 *
 * <p>확인하는 것: 정상 요청이 200과 다섯 필드를 돌려주는지, 그리고 잘못된 요청 네 가지가
 * 서비스까지 가지 않고 각각 맞는 상태 코드로 막히는지.
 *
 * <ul>
 *   <li>sensitiveDataConsent가 false — 422. 명세가 const true로 못 박은 필수 동의라 거절을 저장하지 않는다.
 *   <li>notificationPermission 누락, consentVersion 공백 — 422. 값 검증 실패라 어느 필드인지 함께 담는다.
 *   <li>정의되지 않은 필드, enum에 없는 값 — 400. 본문을 객체로 만들지 못한 단계의 실패다.
 * </ul>
 */
@WebMvcTest(OnboardingController.class)
@AutoConfigureMockMvc(addFilters = false)
@EnableConfigurationProperties({CorsProperties.class, ProblemProperties.class})
@Import({GlobalExceptionHandler.class, ProblemFactory.class})
class OnboardingControllerTest {

    private static final UUID USER_ID = UUID.fromString("2c56fe08-ea1f-45fc-915d-c35b7c0bca39");
    private static final UUID SESSION_ID = UUID.fromString("5ecb88d8-6a21-4a54-8967-72599f078963");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OnboardingService onboardingService;

    @Test
    void completeOnboardingReturnsSavedState() throws Exception {
        when(onboardingService.complete(any(), any())).thenReturn(onboardingResponse());

        mockMvc.perform(put("/v1/me/onboarding")
                        .with(authentication(authenticationToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"consentVersion":"2026-08-09","sensitiveDataConsent":true,\
                                "notificationEnabled":true,"notificationPermission":"GRANTED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consentVersion").value("2026-08-09"))
                .andExpect(jsonPath("$.consentedAt").exists())
                .andExpect(jsonPath("$.notificationEnabled").value(true))
                .andExpect(jsonPath("$.notificationPermission").value("GRANTED"))
                .andExpect(jsonPath("$.completedAt").exists());
    }

    @Test
    void completeOnboardingRejectsRefusedSensitiveDataConsent() throws Exception {
        mockMvc.perform(put("/v1/me/onboarding")
                        .with(authentication(authenticationToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"consentVersion":"2026-08-09","sensitiveDataConsent":false,\
                                "notificationEnabled":true,"notificationPermission":"GRANTED"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("sensitiveDataConsent"));

        verify(onboardingService, never()).complete(any(), any());
    }

    @Test
    void completeOnboardingRejectsMissingNotificationPermission() throws Exception {
        mockMvc.perform(put("/v1/me/onboarding")
                        .with(authentication(authenticationToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"consentVersion":"2026-08-09","sensitiveDataConsent":true,\
                                "notificationEnabled":true}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("notificationPermission"));

        verify(onboardingService, never()).complete(any(), any());
    }

    @Test
    void completeOnboardingRejectsBlankConsentVersion() throws Exception {
        mockMvc.perform(put("/v1/me/onboarding")
                        .with(authentication(authenticationToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"consentVersion":"  ","sensitiveDataConsent":true,\
                                "notificationEnabled":true,"notificationPermission":"GRANTED"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("consentVersion"));
    }

    @Test
    void completeOnboardingRejectsUndefinedField() throws Exception {
        mockMvc.perform(put("/v1/me/onboarding")
                        .with(authentication(authenticationToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"consentVersion":"2026-08-09","sensitiveDataConsent":true,\
                                "notificationEnabled":true,"notificationPermission":"GRANTED",\
                                "marketingConsent":true}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        verify(onboardingService, never()).complete(any(), any());
    }

    @Test
    void completeOnboardingRejectsUnknownNotificationPermission() throws Exception {
        mockMvc.perform(put("/v1/me/onboarding")
                        .with(authentication(authenticationToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"consentVersion":"2026-08-09","sensitiveDataConsent":true,\
                                "notificationEnabled":true,"notificationPermission":"MAYBE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        verify(onboardingService, never()).complete(any(), any());
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

    private OnboardingResponse onboardingResponse() {
        return OnboardingResponse.of(
                "2026-08-09",
                OffsetDateTime.of(2026, 8, 11, 7, 0, 0, 0, ZoneOffset.UTC),
                true,
                NotificationPermission.GRANTED,
                OffsetDateTime.of(2026, 8, 11, 7, 0, 0, 0, ZoneOffset.UTC)
        );
    }
}
