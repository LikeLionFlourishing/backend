package likelion.flourishing.domain.analytics.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import likelion.flourishing.domain.analytics.service.AnalyticsEventService;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
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

@WebMvcTest(AnalyticsEventController.class)
@AutoConfigureMockMvc(addFilters = false)
@EnableConfigurationProperties({CorsProperties.class, ProblemProperties.class})
@Import({GlobalExceptionHandler.class, ProblemFactory.class})
class AnalyticsEventControllerTest {

    private static final UUID USER_ID = UUID.fromString("2c56fe08-ea1f-45fc-915d-c35b7c0bca39");
    private static final UUID SESSION_ID = UUID.fromString("5ecb88d8-6a21-4a54-8967-72599f078963");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalyticsEventService analyticsEventService;

    @Test
    void collectReturnsAcceptedCount() throws Exception {
        when(analyticsEventService.collect(any(), any())).thenReturn(2);

        mockMvc.perform(post("/v1/analytics-events")
                        .with(authentication(authenticationToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "events": [
                                    {
                                      "eventId": "0198a31f-f33f-7000-8000-000000000001",
                                      "eventName": "REPORT_STARTED",
                                      "occurredAt": "2026-08-15T12:00:00+09:00"
                                    },
                                    {
                                      "eventId": "0198a31f-f33f-7000-8000-000000000002",
                                      "eventName": "REPORT_SUBMITTED",
                                      "properties": {
                                        "durationMs": 19000,
                                        "inputAssistUsed": true,
                                        "resultType": "SELF_CARE_GUIDE",
                                        "aiSucceeded": true
                                      },
                                      "occurredAt": "2026-08-15T12:00:19+09:00"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.acceptedCount").value(2));
    }

    @Test
    void collectRejectsUnknownEventNameWithUnprocessableEntity() throws Exception {
        mockMvc.perform(post("/v1/analytics-events")
                        .with(authentication(authenticationToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(singleEvent("DIAGNOSIS_CREATED", "")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(analyticsEventService, never()).collect(any(), any());
    }

    @Test
    void collectRejectsSensitivePropertyWithUnprocessableEntity() throws Exception {
        mockMvc.perform(post("/v1/analytics-events")
                        .with(authentication(authenticationToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(singleEvent(
                                "REPORT_STARTED",
                                "\"properties\": {\"rawText\": \"얼굴이 붉고 따가워요\"},"
                        )))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(analyticsEventService, never()).collect(any(), any());
    }

    @Test
    void collectRejectsUnknownEventFieldWithUnprocessableEntity() throws Exception {
        mockMvc.perform(post("/v1/analytics-events")
                        .with(authentication(authenticationToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(singleEvent("REPORT_STARTED", "\"skinArea\": \"FACE\",")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(analyticsEventService, never()).collect(any(), any());
    }

    @Test
    void collectRejectsMoreThanTwentyEvents() throws Exception {
        String events = IntStream.rangeClosed(1, 21)
                .mapToObj(index -> """
                        {
                          "eventId": "%s",
                          "eventName": "REPORT_STARTED",
                          "occurredAt": "2026-08-15T03:00:00Z"
                        }
                        """.formatted(new UUID(0, index)))
                .collect(Collectors.joining(","));

        mockMvc.perform(post("/v1/analytics-events")
                        .with(authentication(authenticationToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"events\":[" + events + "]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(analyticsEventService, never()).collect(any(), any());
    }

    private String singleEvent(String eventName, String additionalField) {
        return """
                {
                  "events": [
                    {
                      "eventId": "0198a31f-f33f-7000-8000-000000000001",
                      "eventName": "%s",
                      %s
                      "occurredAt": "2026-08-15T03:00:00Z"
                    }
                  ]
                }
                """.formatted(eventName, additionalField);
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
