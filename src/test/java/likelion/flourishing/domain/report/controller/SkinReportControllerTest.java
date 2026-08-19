package likelion.flourishing.domain.report.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.report.idempotency.IdempotentResponse;
import likelion.flourishing.domain.report.service.CareGuideRegenerationService;
import likelion.flourishing.domain.report.service.SkinReportSubmissionService;
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
 * 보고 생성과 관리 설명 재생성 엔드포인트 테스트.
 *
 * <p>확인하는 것: 저장된 응답 본문이 그대로 나가는지, 201에 Location이 붙는지, Idempotency-Key가
 * 없으면 400인지, 같은 날 보고·키 재사용·재생성 초과가 각각 409인지, 남의 보고가 404인지,
 * 규칙이 없으면 503인지.
 */
@WebMvcTest(SkinReportController.class)
@AutoConfigureMockMvc(addFilters = false)
@EnableConfigurationProperties({CorsProperties.class, ProblemProperties.class})
@Import({GlobalExceptionHandler.class, ProblemFactory.class})
class SkinReportControllerTest {

    private static final UUID USER_ID = UUID.fromString("2c56fe08-ea1f-45fc-915d-c35b7c0bca39");
    private static final UUID SESSION_ID = UUID.fromString("5ecb88d8-6a21-4a54-8967-72599f078963");
    private static final UUID REPORT_ID = UUID.fromString("0198a31f-f33f-7000-8000-000000000001");
    private static final UUID IDEMPOTENCY_KEY = UUID.fromString("11111111-2222-4333-8444-555555555555");

    private static final String VALID_BODY = """
            {
              "reportDate": "2026-08-15",
              "rawText": "오른쪽 턱이 빨갛고 따가워요.",
              "confirmed": {
                "primaryArea": "RIGHT_CHIN",
                "appearances": ["APP_REDNESS"],
                "sensations": ["REDNESS"],
                "situations": ["SHAVING"],
                "careAvailability": "ALREADY_WASHED"
              },
              "preCareChecks": ["NONE"]
            }
            """;

    private static final String CREATED_BODY = """
            {"id":"0198a31f-f33f-7000-8000-000000000001","resultType":"SELF_CARE_GUIDE"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SkinReportSubmissionService skinReportSubmissionService;

    @MockitoBean
    private CareGuideRegenerationService careGuideRegenerationService;

    @Test
    void createReturnsStoredBodyWithLocationAndNoStore() throws Exception {
        when(skinReportSubmissionService.submit(any(), eq(IDEMPOTENCY_KEY), any()))
                .thenReturn(IdempotentResponse.created(CREATED_BODY, REPORT_ID));

        mockMvc.perform(post("/v1/skin-reports")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY)
                        .with(authentication(authenticationToken())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/v1/skin-reports/" + REPORT_ID))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(REPORT_ID.toString()))
                .andExpect(jsonPath("$.resultType").value("SELF_CARE_GUIDE"));
    }

    /** 재전송에는 처음 만든 상태 코드와 본문이 그대로 나가야 한다. */
    @Test
    void replayReturnsTheStoredStatusAndBody() throws Exception {
        when(skinReportSubmissionService.submit(any(), any(), any()))
                .thenReturn(IdempotentResponse.replay(201, CREATED_BODY, REPORT_ID));

        mockMvc.perform(post("/v1/skin-reports")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY)
                        .with(authentication(authenticationToken())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/v1/skin-reports/" + REPORT_ID))
                .andExpect(content().json(CREATED_BODY));
    }

    @Test
    void missingIdempotencyKeyIsBadRequest() throws Exception {
        mockMvc.perform(post("/v1/skin-reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY)
                        .with(authentication(authenticationToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        verify(skinReportSubmissionService, never()).submit(any(), any(), any());
    }

    @Test
    void malformedIdempotencyKeyIsBadRequest() throws Exception {
        mockMvc.perform(post("/v1/skin-reports")
                        .header("Idempotency-Key", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY)
                        .with(authentication(authenticationToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void sameDayReportIsConflict() throws Exception {
        when(skinReportSubmissionService.submit(any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.REPORT_ALREADY_EXISTS));

        mockMvc.perform(post("/v1/skin-reports")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY)
                        .with(authentication(authenticationToken())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_ALREADY_EXISTS"));
    }

    @Test
    void reusedIdempotencyKeyIsConflict() throws Exception {
        when(skinReportSubmissionService.submit(any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED));

        mockMvc.perform(post("/v1/skin-reports")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY)
                        .with(authentication(authenticationToken())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void unavailableRulesAreServiceUnavailable() throws Exception {
        when(skinReportSubmissionService.submit(any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.RULE_ENGINE_UNAVAILABLE));

        mockMvc.perform(post("/v1/skin-reports")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY)
                        .with(authentication(authenticationToken())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("RULE_ENGINE_UNAVAILABLE"));
    }

    /** 명세가 reportDate 를 required 로 두었으므로 누락은 형식 오류다. */
    @Test
    void missingReportDateIsUnprocessable() throws Exception {
        mockMvc.perform(post("/v1/skin-reports")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rawText": "턱이 빨개요.",
                                  "confirmed": {
                                    "primaryArea": "RIGHT_CHIN",
                                    "appearances": ["APP_REDNESS"],
                                    "sensations": ["BREAKOUT"],
                                    "situations": ["SHAVING"],
                                    "careAvailability": "ALREADY_WASHED"
                                  },
                                  "preCareChecks": ["NONE"]
                                }
                                """)
                        .with(authentication(authenticationToken())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("reportDate"));
    }

    @Test
    void emptyMultiSelectionIsUnprocessable() throws Exception {
        mockMvc.perform(post("/v1/skin-reports")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reportDate": "2026-08-15",
                                  "rawText": "턱이 빨개요.",
                                  "confirmed": {
                                    "primaryArea": "RIGHT_CHIN",
                                    "appearances": [],
                                    "sensations": ["BREAKOUT"],
                                    "situations": ["NONE_RECALLED"],
                                    "careAvailability": "ALREADY_WASHED"
                                  },
                                  "preCareChecks": ["NONE"]
                                }
                                """)
                        .with(authentication(authenticationToken())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("confirmed.appearances"));
    }

    @Test
    void regenerationWorksWithoutIdempotencyKey() throws Exception {
        when(careGuideRegenerationService.regenerate(any(), eq(REPORT_ID), isNull()))
                .thenReturn(IdempotentResponse.ok("{\"retryUsed\":true}", REPORT_ID));

        mockMvc.perform(post("/v1/skin-reports/{reportId}/care-guide-generations", REPORT_ID)
                        .with(authentication(authenticationToken())))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.retryUsed").value(true));
    }

    @Test
    void regenerationOfAnotherUsersReportIsNotFound() throws Exception {
        when(careGuideRegenerationService.regenerate(any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        mockMvc.perform(post("/v1/skin-reports/{reportId}/care-guide-generations", REPORT_ID)
                        .with(authentication(authenticationToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void secondRegenerationIsConflict() throws Exception {
        when(careGuideRegenerationService.regenerate(any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.AI_RETRY_ALREADY_USED));

        mockMvc.perform(post("/v1/skin-reports/{reportId}/care-guide-generations", REPORT_ID)
                        .with(authentication(authenticationToken())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_RETRY_ALREADY_USED"));
    }

    @Test
    void regenerationOfGeneratedResultIsUnprocessable() throws Exception {
        when(careGuideRegenerationService.regenerate(any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.AI_RETRY_NOT_AVAILABLE));

        mockMvc.perform(post("/v1/skin-reports/{reportId}/care-guide-generations", REPORT_ID)
                        .with(authentication(authenticationToken())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("AI_RETRY_NOT_AVAILABLE"));
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
