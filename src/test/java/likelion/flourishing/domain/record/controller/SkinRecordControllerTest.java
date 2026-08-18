package likelion.flourishing.domain.record.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.record.dto.response.CareResultResponse;
import likelion.flourishing.domain.record.dto.response.ConfirmedStructuredReportResponse;
import likelion.flourishing.domain.record.dto.response.CursorPageResponse;
import likelion.flourishing.domain.record.dto.response.SkinReportDetailResponse;
import likelion.flourishing.domain.record.dto.response.SkinReportListResponse;
import likelion.flourishing.domain.record.dto.response.SkinReportSummaryResponse;
import likelion.flourishing.domain.record.service.SkinRecordService;
import likelion.flourishing.domain.report.entity.AiGenerationStatus;
import likelion.flourishing.domain.report.entity.Appearance;
import likelion.flourishing.domain.report.entity.BodyArea;
import likelion.flourishing.domain.report.entity.CareAvailability;
import likelion.flourishing.domain.report.entity.PreCareCheck;
import likelion.flourishing.domain.report.entity.ReportStatus;
import likelion.flourishing.domain.report.entity.ResultType;
import likelion.flourishing.domain.report.entity.Sensation;
import likelion.flourishing.domain.report.entity.Situation;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SkinRecordController.class)
@AutoConfigureMockMvc(addFilters = false)
@EnableConfigurationProperties({CorsProperties.class, ProblemProperties.class})
@Import({GlobalExceptionHandler.class, ProblemFactory.class})
class SkinRecordControllerTest {

    private static final UUID USER_ID = UUID.fromString("2c56fe08-ea1f-45fc-915d-c35b7c0bca39");
    private static final UUID SESSION_ID = UUID.fromString("5ecb88d8-6a21-4a54-8967-72599f078963");
    private static final UUID REPORT_ID = UUID.fromString("0198a31f-f33f-7000-8000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SkinRecordService skinRecordService;

    @Test
    void listReturnsOpenApiShapeAndNoStoreHeader() throws Exception {
        when(skinRecordService.getRecords(any(), any(), any(), any(), any())).thenReturn(listResponse());

        mockMvc.perform(get("/v1/skin-reports")
                        .queryParam("limit", "20")
                        .queryParam("status", "FOLLOW_UP_PENDING")
                        .with(authentication(authenticationToken())))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data[0].id").value(REPORT_ID.toString()))
                .andExpect(jsonPath("$.data[0].primaryArea").value("RIGHT_CHIN"))
                .andExpect(jsonPath("$.data[0].skinChange").doesNotExist())
                .andExpect(jsonPath("$.pagination.nextCursor").value("signed-cursor"))
                .andExpect(jsonPath("$.pagination.hasMore").value(true))
                .andExpect(jsonPath("$.pagination.limit").value(20));
    }

    @Test
    void detailReturnsConfirmedResultAndNoStoreHeader() throws Exception {
        when(skinRecordService.getRecord(any(), any())).thenReturn(detailResponse());

        mockMvc.perform(get("/v1/skin-reports/{reportId}", REPORT_ID)
                        .with(authentication(authenticationToken())))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.rawText").value("오른쪽 턱이 빨갛고 따가워요."))
                .andExpect(jsonPath("$.confirmed.careAvailability").value("ALREADY_WASHED"))
                .andExpect(jsonPath("$.preCareChecks[0]").value("NONE"))
                .andExpect(jsonPath("$.careResult.ruleVersion").value("2026-08-09-v1"))
                .andExpect(jsonPath("$.followUp").doesNotExist());
    }

    @Test
    void listRejectsUnknownStatusAsBadRequest() throws Exception {
        mockMvc.perform(get("/v1/skin-reports")
                        .queryParam("status", "DELETED")
                        .with(authentication(authenticationToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        verify(skinRecordService, never()).getRecords(any(), any(), any(), any(), any());
    }

    @Test
    void detailReturnsResourceNotFoundWithoutOwnershipDisclosure() throws Exception {
        when(skinRecordService.getRecord(any(), any()))
                .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        mockMvc.perform(get("/v1/skin-reports/{reportId}", REPORT_ID)
                        .with(authentication(authenticationToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private SkinReportListResponse listResponse() {
        SkinReportSummaryResponse summary = SkinReportSummaryResponse.of(
                REPORT_ID,
                LocalDate.of(2026, 8, 15),
                BodyArea.RIGHT_CHIN,
                List.of(Appearance.APP_REDNESS),
                List.of(Sensation.REDNESS),
                List.of(Situation.SHAVING),
                ResultType.SELF_CARE_GUIDE,
                ReportStatus.FOLLOW_UP_PENDING,
                null
        );
        return SkinReportListResponse.of(
                List.of(summary),
                CursorPageResponse.of("signed-cursor", true, 20)
        );
    }

    private SkinReportDetailResponse detailResponse() {
        List<Appearance> appearances = List.of(Appearance.APP_REDNESS);
        List<Sensation> sensations = List.of(Sensation.REDNESS);
        List<Situation> situations = List.of(Situation.SHAVING);
        ConfirmedStructuredReportResponse confirmed = ConfirmedStructuredReportResponse.of(
                BodyArea.RIGHT_CHIN,
                null,
                appearances,
                sensations,
                situations,
                CareAvailability.ALREADY_WASHED
        );
        CareResultResponse careResult = CareResultResponse.of(
                ResultType.SELF_CARE_GUIDE,
                List.of("GEN-001"),
                "2026-08-09-v1",
                "자극 없이 관리해 보세요.",
                List.of("미지근한 물로 씻기"),
                List.of("손으로 만지지 않기"),
                List.of("붉은 범위 확인"),
                List.of(),
                null,
                null,
                AiGenerationStatus.GENERATED,
                OffsetDateTime.of(2026, 8, 15, 3, 1, 0, 0, ZoneOffset.UTC),
                false
        );
        return SkinReportDetailResponse.of(
                REPORT_ID,
                LocalDate.of(2026, 8, 15),
                BodyArea.RIGHT_CHIN,
                appearances,
                sensations,
                situations,
                ResultType.SELF_CARE_GUIDE,
                ReportStatus.FOLLOW_UP_PENDING,
                null,
                "오른쪽 턱이 빨갛고 따가워요.",
                confirmed,
                List.of(PreCareCheck.NONE),
                careResult,
                null,
                OffsetDateTime.of(2026, 8, 15, 3, 0, 0, 0, ZoneOffset.UTC)
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
