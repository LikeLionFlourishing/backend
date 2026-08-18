package likelion.flourishing.domain.followup.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.followup.dto.response.FollowUpResponse;
import likelion.flourishing.domain.followup.entity.ActionCompletion;
import likelion.flourishing.domain.followup.entity.ClinicianCheckStatus;
import likelion.flourishing.domain.followup.entity.FollowUpKind;
import likelion.flourishing.domain.followup.entity.SkinChange;
import likelion.flourishing.domain.followup.service.FollowUpService;
import likelion.flourishing.domain.followup.service.SavedFollowUp;
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
 * FollowUpController의 HTTP 계약 테스트. 서비스는 가짜(mock)로 두고 요청·응답 모양만 검증한다.
 *
 * <p>핵심은 kind에 따라 본문 모양이 갈리는 oneOf가 제대로 매핑되는지다. 명세 v2_1에서
 * actionCompletion이 두 종류 공통이 되어, 두 모양을 가르는 것은 clinicianCheckStatus 하나다.
 * SELF_CARE 응답에는 그 필드가 나가지 않아야 한다.
 */
@WebMvcTest(FollowUpController.class)
@AutoConfigureMockMvc(addFilters = false)
@EnableConfigurationProperties({CorsProperties.class, ProblemProperties.class})
@Import({GlobalExceptionHandler.class, ProblemFactory.class})
class FollowUpControllerTest {

    private static final UUID USER_ID = UUID.fromString("2c56fe08-ea1f-45fc-915d-c35b7c0bca39");
    private static final UUID SESSION_ID = UUID.fromString("5ecb88d8-6a21-4a54-8967-72599f078963");
    private static final UUID REPORT_ID = UUID.fromString("9a1d3f52-1f0b-4a44-9d2e-6e1d0c7a51bb");
    private static final String PATH = "/v1/skin-reports/9a1d3f52-1f0b-4a44-9d2e-6e1d0c7a51bb/follow-up";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FollowUpService followUpService;

    @Test
    void saveSelfCareReturnsCreatedWithoutClinicianField() throws Exception {
        when(followUpService.saveFollowUp(any(), any(), any()))
                .thenReturn(new SavedFollowUp(selfCareResponse(), true));

        mockMvc.perform(put(PATH)
                        .with(authentication(authenticationToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"SELF_CARE","skinChange":"IMPROVED","actionCompletion":"MOSTLY_DONE"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("SELF_CARE"))
                .andExpect(jsonPath("$.skinChange").value("IMPROVED"))
                .andExpect(jsonPath("$.actionCompletion").value("MOSTLY_DONE"))
                .andExpect(jsonPath("$.clinicianCheckStatus").doesNotExist())
                .andExpect(jsonPath("$.submittedAt").exists());
    }

    /** 명세 v2_1에서 의료진 확인 경과도 행동 실행 여부를 함께 받고, 응답에도 함께 담는다. */
    @Test
    void saveClinicianReturnsCreatedWithBothAnswers() throws Exception {
        when(followUpService.saveFollowUp(any(), any(), any()))
                .thenReturn(new SavedFollowUp(clinicianResponse(), true));

        mockMvc.perform(put(PATH)
                        .with(authentication(authenticationToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"CLINICIAN_CHECK","skinChange":"SIMILAR",\
                                "actionCompletion":"PARTLY_DONE","clinicianCheckStatus":"CHECKED"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clinicianCheckStatus").value("CHECKED"))
                .andExpect(jsonPath("$.actionCompletion").value("PARTLY_DONE"));
    }

    /** 의료진 확인 여부만 보내면 거절한다. 행동 실행 여부는 v2_1에서 필수가 됐다. */
    @Test
    void saveClinicianRejectsMissingActionCompletion() throws Exception {
        mockMvc.perform(put(PATH)
                        .with(authentication(authenticationToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"CLINICIAN_CHECK","skinChange":"SIMILAR","clinicianCheckStatus":"CHECKED"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("actionCompletion"));

        verify(followUpService, never()).saveFollowUp(any(), any(), any());
    }

    @Test
    void saveReturnsOkWhenSameContentAlreadyStored() throws Exception {
        when(followUpService.saveFollowUp(any(), any(), any()))
                .thenReturn(new SavedFollowUp(selfCareResponse(), false));

        mockMvc.perform(put(PATH)
                        .with(authentication(authenticationToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"SELF_CARE","skinChange":"IMPROVED","actionCompletion":"MOSTLY_DONE"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void saveRejectsMissingKindDiscriminator() throws Exception {
        mockMvc.perform(put(PATH)
                        .with(authentication(authenticationToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skinChange":"IMPROVED","actionCompletion":"MOSTLY_DONE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        verify(followUpService, never()).saveFollowUp(any(), any(), any());
    }

    @Test
    void saveRejectsFieldFromTheOtherKind() throws Exception {
        mockMvc.perform(put(PATH)
                        .with(authentication(authenticationToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"SELF_CARE","skinChange":"IMPROVED","clinicianCheckStatus":"CHECKED"}
                                """))
                .andExpect(status().isBadRequest());

        verify(followUpService, never()).saveFollowUp(any(), any(), any());
    }

    @Test
    void saveRejectsMissingRequiredField() throws Exception {
        mockMvc.perform(put(PATH)
                        .with(authentication(authenticationToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"SELF_CARE","skinChange":"IMPROVED"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("actionCompletion"));

        verify(followUpService, never()).saveFollowUp(any(), any(), any());
    }

    @Test
    void getReturnsStoredFollowUp() throws Exception {
        when(followUpService.getFollowUp(any(), any())).thenReturn(selfCareResponse());

        mockMvc.perform(get(PATH).with(authentication(authenticationToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId").value(REPORT_ID.toString()))
                .andExpect(jsonPath("$.kind").value("SELF_CARE"));
    }

    @Test
    void getRejectsMalformedReportId() throws Exception {
        mockMvc.perform(get("/v1/skin-reports/not-a-uuid/follow-up")
                        .with(authentication(authenticationToken())))
                .andExpect(status().isBadRequest());

        verify(followUpService, never()).getFollowUp(any(), any());
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

    private FollowUpResponse selfCareResponse() {
        return FollowUpResponse.of(
                REPORT_ID,
                FollowUpKind.SELF_CARE,
                SkinChange.IMPROVED,
                ActionCompletion.MOSTLY_DONE,
                null,
                OffsetDateTime.of(2026, 8, 11, 12, 0, 0, 0, ZoneOffset.UTC)
        );
    }

    private FollowUpResponse clinicianResponse() {
        return FollowUpResponse.of(
                REPORT_ID,
                FollowUpKind.CLINICIAN_CHECK,
                SkinChange.SIMILAR,
                ActionCompletion.PARTLY_DONE,
                ClinicianCheckStatus.CHECKED,
                OffsetDateTime.of(2026, 8, 11, 12, 0, 0, 0, ZoneOffset.UTC)
        );
    }
}
