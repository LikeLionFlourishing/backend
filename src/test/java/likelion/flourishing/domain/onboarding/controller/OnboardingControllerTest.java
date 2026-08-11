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
