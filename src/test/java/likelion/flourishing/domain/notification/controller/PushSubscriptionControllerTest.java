package likelion.flourishing.domain.notification.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.notification.dto.response.PushSubscriptionResponse;
import likelion.flourishing.domain.notification.service.PushSubscriptionService;
import likelion.flourishing.domain.notification.service.SavedPushSubscription;
import likelion.flourishing.global.config.CorsProperties;
import likelion.flourishing.global.config.ProblemProperties;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import likelion.flourishing.global.exception.GlobalExceptionHandler;
import likelion.flourishing.global.exception.ProblemFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PushSubscriptionController.class)
@AutoConfigureMockMvc(addFilters = false)
@EnableConfigurationProperties({CorsProperties.class, ProblemProperties.class})
@Import({GlobalExceptionHandler.class, ProblemFactory.class})
class PushSubscriptionControllerTest {

    private static final String PATH = "/v1/push-subscriptions";
    private static final UUID USER_ID = UUID.fromString("2c56fe08-ea1f-45fc-915d-c35b7c0bca39");
    private static final UUID SESSION_ID = UUID.fromString("5ecb88d8-6a21-4a54-8967-72599f078963");
    private static final UUID SUBSCRIPTION_ID = UUID.fromString("0198a31f-f33f-7000-8000-0000000000a1");
    private static final String FINGERPRINT =
            "2b7f0a6cb9c0d2d3f4e5a6b7c8d9e0f11223344556677889900aabbccddeeff0";
    private static final String VALID_BODY = """
            {
              "endpoint": "https://push.example.net/push/JzLQ3raZJfFBR0aqvOMsLrt54w4rJUsV",
              "keys": {
                "p256dh": "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4",
                "auth": "BTBZMqHH6r4Tts7J_aSIgg"
              }
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PushSubscriptionService pushSubscriptionService;

    @Test
    void newSubscriptionReturnsCreatedWithFingerprintOnly() throws Exception {
        when(pushSubscriptionService.register(any(), any(), any()))
                .thenReturn(new SavedPushSubscription(response(), true));

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.USER_AGENT, "Chrome/130")
                        .content(VALID_BODY)
                        .with(authentication(authenticationToken())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subscriptionId").value(SUBSCRIPTION_ID.toString()))
                .andExpect(jsonPath("$.endpointFingerprint").value(FINGERPRINT))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.endpoint").doesNotExist())
                .andExpect(jsonPath("$.keys").doesNotExist());
    }

    @Test
    void repeatedEndpointReturnsOk() throws Exception {
        when(pushSubscriptionService.register(any(), any(), any()))
                .thenReturn(new SavedPushSubscription(response(), false));

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY)
                        .with(authentication(authenticationToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptionId").value(SUBSCRIPTION_ID.toString()));
    }

    @Test
    void userAgentHeaderIsForwardedToService() throws Exception {
        when(pushSubscriptionService.register(any(), any(), eq("Chrome/130")))
                .thenReturn(new SavedPushSubscription(response(), true));

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.USER_AGENT, "Chrome/130")
                        .content(VALID_BODY)
                        .with(authentication(authenticationToken())))
                .andExpect(status().isCreated());

        verify(pushSubscriptionService).register(any(), any(), eq("Chrome/130"));
    }

    @Test
    void blankEndpointIsValidationError() throws Exception {
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"\",\"keys\":{\"p256dh\":\"a\",\"auth\":\"b\"}}")
                        .with(authentication(authenticationToken())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(pushSubscriptionService, never()).register(any(), any(), any());
    }

    @Test
    void nonHttpsEndpointIsValidationError() throws Exception {
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"http://push.example.net/p\",\"keys\":{\"p256dh\":\"a\",\"auth\":\"b\"}}")
                        .with(authentication(authenticationToken())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void missingKeysIsValidationError() throws Exception {
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"https://push.example.net/p\"}")
                        .with(authentication(authenticationToken())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void unknownFieldIsRejected() throws Exception {
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"https://push.example.net/p\",\"keys\":{\"p256dh\":\"a\",\"auth\":\"b\"},\"vapid\":\"x\"}")
                        .with(authentication(authenticationToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void keyRejectedByServiceIsValidationError() throws Exception {
        when(pushSubscriptionService.register(any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.VALIDATION_ERROR));

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY)
                        .with(authentication(authenticationToken())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void unregisterReturnsNoContent() throws Exception {
        mockMvc.perform(delete(PATH + "/{subscriptionId}", SUBSCRIPTION_ID)
                        .with(authentication(authenticationToken())))
                .andExpect(status().isNoContent());

        verify(pushSubscriptionService).unregister(any(), eq(SUBSCRIPTION_ID));
    }

    @Test
    void unregisterHidesOtherUsersSubscriptionAsNotFound() throws Exception {
        doThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND))
                .when(pushSubscriptionService).unregister(any(), eq(SUBSCRIPTION_ID));

        mockMvc.perform(delete(PATH + "/{subscriptionId}", SUBSCRIPTION_ID)
                        .with(authentication(authenticationToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void unregisterWithMalformedIdIsBadRequest() throws Exception {
        mockMvc.perform(delete(PATH + "/not-a-uuid")
                        .with(authentication(authenticationToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    private PushSubscriptionResponse response() {
        OffsetDateTime savedAt = OffsetDateTime.of(2026, 8, 15, 8, 30, 0, 0, ZoneOffset.UTC);
        return PushSubscriptionResponse.of(SUBSCRIPTION_ID, FINGERPRINT, true, null, savedAt, savedAt);
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
