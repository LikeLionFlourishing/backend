package likelion.flourishing.domain.notification.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.notification.dto.response.NotificationSettingsResponse;
import likelion.flourishing.domain.notification.service.NotificationSettingsPatchReader;
import likelion.flourishing.domain.notification.service.NotificationSettingsService;
import likelion.flourishing.domain.onboarding.dto.response.NotificationConsentResponse;
import likelion.flourishing.domain.onboarding.entity.NotificationPermission;
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

@WebMvcTest(NotificationSettingsController.class)
@AutoConfigureMockMvc(addFilters = false)
@EnableConfigurationProperties({CorsProperties.class, ProblemProperties.class})
@Import({GlobalExceptionHandler.class, ProblemFactory.class, NotificationSettingsPatchReader.class})
class NotificationSettingsControllerTest {

    private static final String PATH = "/v1/me/notification-settings";
    private static final UUID USER_ID = UUID.fromString("2c56fe08-ea1f-45fc-915d-c35b7c0bca39");
    private static final UUID SESSION_ID = UUID.fromString("5ecb88d8-6a21-4a54-8967-72599f078963");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationSettingsService notificationSettingsService;

    @Test
    void getReturnsUserTimeAndFixedZoneWithNoStoreHeader() throws Exception {
        when(notificationSettingsService.getSettings(any())).thenReturn(response(true, 1L));

        mockMvc.perform(get(PATH).with(authentication(authenticationToken())))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.time").value("21:00"))
                .andExpect(jsonPath("$.timezone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.permission").value("GRANTED"))
                .andExpect(jsonPath("$.activeSubscriptionCount").value(1))
                .andExpect(jsonPath("$.updatedAt").doesNotExist());
    }

    @Test
    void patchReturnsUpdatedSettings() throws Exception {
        when(notificationSettingsService.updateSettings(any(), any())).thenReturn(response(false, 0L));

        mockMvc.perform(patch(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}")
                        .with(authentication(authenticationToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.time").value("21:00"));
    }

    /** 명세 요청 본문에는 enabled·time·consent 만 있다. 권한은 온보딩에서 받는다. */
    @Test
    void patchRejectsPermissionFieldInBody() throws Exception {
        mockMvc.perform(patch(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"permission\":\"GRANTED\"}")
                        .with(authentication(authenticationToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        verify(notificationSettingsService, never()).updateSettings(any(), any());
    }

    /** 명세의 minProperties: 1. 아무것도 바꾸지 않는 요청은 성립하지 않는다. */
    @Test
    void patchWithAnEmptyObjectIsValidationError() throws Exception {
        mockMvc.perform(patch(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(authentication(authenticationToken())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(notificationSettingsService, never()).updateSettings(any(), any());
    }

    @Test
    void patchRejectsFixedTimeOverride() throws Exception {
        mockMvc.perform(patch(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"notificationTime\":\"09:00\"}")
                        .with(authentication(authenticationToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        verify(notificationSettingsService, never()).updateSettings(any(), any());
    }

    @Test
    void patchRejectsNonBooleanEnabled() throws Exception {
        mockMvc.perform(patch(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":\"maybe\"}")
                        .with(authentication(authenticationToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    private NotificationSettingsResponse response(boolean enabled, long subscriptions) {
        return NotificationSettingsResponse.of(
                enabled,
                "21:00",
                "Asia/Seoul",
                false,
                enabled ? NotificationPermission.GRANTED : NotificationPermission.DENIED,
                NotificationConsentResponse.of(enabled, "2026-08-16", enabled ? LocalDateTime.of(2026, 8, 16, 3, 0) : null),
                subscriptions
        );
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
}
