package likelion.flourishing.domain.home.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.home.dto.response.DailyCheckInResponse;
import likelion.flourishing.domain.home.dto.response.HomeResponse;
import likelion.flourishing.domain.home.dto.response.SkinReportSummaryResponse;
import likelion.flourishing.domain.home.entity.CheckInState;
import likelion.flourishing.domain.home.entity.HomePriority;
import likelion.flourishing.domain.home.service.HomeService;
import likelion.flourishing.domain.home.service.SavedDailyCheckIn;
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
 * HomeController의 HTTP 계약 테스트. 서비스는 가짜(mock)로 두고 요청·응답 모양만 검증한다.
 *
 * <p>확인하는 것: 홈 조회가 명세의 다섯 필드를 주고 비어 있는 항목도 null로 남기는지,
 * 저장이 새로 만들 때 201·이미 있을 때 200으로 갈리는지, 날짜 형식과 상태 값이 잘못된
 * 요청이 서비스까지 가지 않고 막히는지.
 */
@WebMvcTest(HomeController.class)
@AutoConfigureMockMvc(addFilters = false)
@EnableConfigurationProperties({CorsProperties.class, ProblemProperties.class})
@Import({GlobalExceptionHandler.class, ProblemFactory.class})
class HomeControllerTest {

    private static final UUID USER_ID = UUID.fromString("2c56fe08-ea1f-45fc-915d-c35b7c0bca39");
    private static final UUID SESSION_ID = UUID.fromString("5ecb88d8-6a21-4a54-8967-72599f078963");
    private static final UUID REPORT_ID = UUID.fromString("9a1d3f52-1f0b-4a44-9d2e-6e1d0c7a51bb");
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 12);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HomeService homeService;

    @Test
    void getHomeReturnsAggregatedFields() throws Exception {
        when(homeService.getHome(any())).thenReturn(homeResponse());

        mockMvc.perform(get("/v1/home").with(authentication(authenticationToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serverDate").value("2026-08-12"))
                .andExpect(jsonPath("$.priority").value("RECENT_RECORD"))
                .andExpect(jsonPath("$.pendingFollowUp").doesNotExist())
                .andExpect(jsonPath("$.today").doesNotExist())
                .andExpect(jsonPath("$.recentReport.id").value(REPORT_ID.toString()))
                .andExpect(jsonPath("$.recentReport.primaryArea").value("RIGHT_CHIN"))
                .andExpect(jsonPath("$.recentReport.appearances[0]").value("APP_REDNESS"));
    }

    @Test
    void saveReturnsCreatedWhenNewlyStored() throws Exception {
        when(homeService.saveNoDiscomfort(any(), any(), any()))
                .thenReturn(new SavedDailyCheckIn(dailyCheckInResponse(), true));

        mockMvc.perform(put("/v1/daily-check-ins/2026-08-12")
                        .with(authentication(authenticationToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"state":"NO_DISCOMFORT"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.date").value("2026-08-12"))
                .andExpect(jsonPath("$.state").value("NO_DISCOMFORT"));
    }

    @Test
    void saveReturnsOkWhenSameValueAlreadyStored() throws Exception {
        when(homeService.saveNoDiscomfort(any(), any(), any()))
                .thenReturn(new SavedDailyCheckIn(dailyCheckInResponse(), false));

        mockMvc.perform(put("/v1/daily-check-ins/2026-08-12")
                        .with(authentication(authenticationToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"state":"NO_DISCOMFORT"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void saveRejectsMissingState() throws Exception {
        mockMvc.perform(put("/v1/daily-check-ins/2026-08-12")
                        .with(authentication(authenticationToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("state"));

        verify(homeService, never()).saveNoDiscomfort(any(), any(), any());
    }

    @Test
    void saveRejectsUnknownState() throws Exception {
        mockMvc.perform(put("/v1/daily-check-ins/2026-08-12")
                        .with(authentication(authenticationToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"state":"FEELING_FINE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        verify(homeService, never()).saveNoDiscomfort(any(), any(), any());
    }

    @Test
    void saveRejectsMalformedDate() throws Exception {
        mockMvc.perform(put("/v1/daily-check-ins/2026-13-99")
                        .with(authentication(authenticationToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"state":"NO_DISCOMFORT"}
                                """))
                .andExpect(status().isBadRequest());

        verify(homeService, never()).saveNoDiscomfort(any(), any(), any());
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

    private HomeResponse homeResponse() {
        return HomeResponse.of(
                TODAY,
                HomePriority.RECENT_RECORD,
                null,
                null,
                SkinReportSummaryResponse.of(
                        REPORT_ID,
                        TODAY.minusDays(1),
                        "RIGHT_CHIN",
                        List.of("APP_REDNESS"),
                        List.of("BREAKOUT"),
                        List.of("SHAVING"),
                        "SELF_CARE_GUIDE",
                        "FOLLOW_UP_PENDING",
                        null
                )
        );
    }

    private DailyCheckInResponse dailyCheckInResponse() {
        return DailyCheckInResponse.of(
                TODAY,
                CheckInState.NO_DISCOMFORT,
                null,
                OffsetDateTime.of(2026, 8, 12, 7, 0, 0, 0, ZoneOffset.UTC)
        );
    }
}
