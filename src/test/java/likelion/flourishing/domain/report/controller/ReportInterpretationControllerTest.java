package likelion.flourishing.domain.report.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.report.dto.response.InterpretationFailureCode;
import likelion.flourishing.domain.report.dto.response.MissingField;
import likelion.flourishing.domain.report.dto.response.ReportInterpretationResponse;
import likelion.flourishing.domain.report.dto.response.StructuredSelectionsResponse;
import likelion.flourishing.domain.report.entity.Appearance;
import likelion.flourishing.domain.report.entity.BodyArea;
import likelion.flourishing.domain.report.entity.CareAvailability;
import likelion.flourishing.domain.report.entity.Sensation;
import likelion.flourishing.domain.report.entity.Situation;
import likelion.flourishing.domain.report.service.ReportInterpretationService;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 구조화 엔드포인트 테스트.
 *
 * <p>확인하는 것: 성공과 실패가 모두 200으로 나가는지, 실패에 failureCode가 붙는지, 동의가 없으면
 * 403인지, 본문 검증 실패가 422인지, 응답이 캐시되지 않는지.
 */
@WebMvcTest(ReportInterpretationController.class)
@AutoConfigureMockMvc(addFilters = false)
@EnableConfigurationProperties({CorsProperties.class, ProblemProperties.class})
@Import({GlobalExceptionHandler.class, ProblemFactory.class})
class ReportInterpretationControllerTest {

    private static final UUID USER_ID = UUID.fromString("2c56fe08-ea1f-45fc-915d-c35b7c0bca39");
    private static final UUID SESSION_ID = UUID.fromString("5ecb88d8-6a21-4a54-8967-72599f078963");

    private static final String VALID_BODY = """
            {
              "rawText": "오른쪽 턱이 빨갛고 따가워요.",
              "manualSelections": {
                "primaryArea": "RIGHT_CHIN",
                "appearances": ["APP_REDNESS"]
              }
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportInterpretationService reportInterpretationService;

    @Test
    void succeededInterpretationReturnsMergedSelections() throws Exception {
        when(reportInterpretationService.interpret(any(), any())).thenReturn(succeeded());

        mockMvc.perform(post("/v1/report-interpretations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY)
                        .with(authentication(authenticationToken())))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.processingStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.failureCode").value(nullValue()))
                .andExpect(jsonPath("$.proposed.primaryArea").value("RIGHT_CHIN"))
                .andExpect(jsonPath("$.proposed.appearances[0]").value("APP_REDNESS"))
                .andExpect(jsonPath("$.missingFields").isEmpty())
                .andExpect(jsonPath("$.ambiguities").isEmpty())
                // 명세에 없는 키는 나가지 않아야 한다.
                .andExpect(jsonPath("$.structured").doesNotExist())
                .andExpect(jsonPath("$.fieldSources").doesNotExist())
                .andExpect(jsonPath("$.interpretedAt").doesNotExist());
    }

    @Test
    void failedInterpretationStillReturnsOkWithFailureCode() throws Exception {
        when(reportInterpretationService.interpret(any(), any())).thenReturn(failed());

        mockMvc.perform(post("/v1/report-interpretations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY)
                        .with(authentication(authenticationToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processingStatus").value("FAILED"))
                .andExpect(jsonPath("$.failureCode").value("AI_TIMEOUT"))
                .andExpect(jsonPath("$.proposed.primaryArea").value("RIGHT_CHIN"))
                .andExpect(jsonPath("$.missingFields[0]").value("CARE_AVAILABILITY"));
    }

    @Test
    void missingConsentIsForbidden() throws Exception {
        when(reportInterpretationService.interpret(any(), any()))
                .thenThrow(new BusinessException(ErrorCode.CONSENT_REQUIRED));

        mockMvc.perform(post("/v1/report-interpretations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY)
                        .with(authentication(authenticationToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
    }

    @Test
    void blankRawTextIsUnprocessable() throws Exception {
        mockMvc.perform(post("/v1/report-interpretations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rawText\": \"  \"}")
                        .with(authentication(authenticationToken())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("rawText"));

        verify(reportInterpretationService, never()).interpret(any(), any());
    }

    @Test
    void unknownSelectionCodeIsBadRequest() throws Exception {
        mockMvc.perform(post("/v1/report-interpretations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rawText": "턱이 빨개요.", "manualSelections": {"primaryArea": "FOREARM"}}
                                """)
                        .with(authentication(authenticationToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        verify(reportInterpretationService, never()).interpret(any(), any());
    }

    @Test
    void undefinedFieldIsBadRequest() throws Exception {
        mockMvc.perform(post("/v1/report-interpretations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rawText\": \"턱이 빨개요.\", \"diagnosis\": \"여드름\"}")
                        .with(authentication(authenticationToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    private ReportInterpretationResponse succeeded() {
        return ReportInterpretationResponse.succeeded(proposed(), List.of(), List.of());
    }

    private ReportInterpretationResponse failed() {
        return ReportInterpretationResponse.failed(
                InterpretationFailureCode.AI_TIMEOUT,
                proposed(),
                List.of(MissingField.CARE_AVAILABILITY),
                List.of()
        );
    }

    private StructuredSelectionsResponse proposed() {
        return StructuredSelectionsResponse.of(
                BodyArea.RIGHT_CHIN,
                null,
                List.of(Appearance.APP_REDNESS),
                List.of(Sensation.REDNESS),
                List.of(Situation.SHAVING),
                CareAvailability.ALREADY_WASHED
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
