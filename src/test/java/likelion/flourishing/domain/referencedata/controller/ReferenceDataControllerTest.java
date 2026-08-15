package likelion.flourishing.domain.referencedata.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import likelion.flourishing.domain.referencedata.service.ReferenceDataService;
import likelion.flourishing.global.config.CorsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 응답 JSON 계약만 검증한다. 보안 경계 검증은 {@link ReferenceDataSecurityTest}가 맡는다.
 *
 * <p>addFilters = false로 보안 필터를 꺼서 SecurityConfig에 의존하지 않는다. 인증 기능이
 * SecurityConfig를 바꾸면서 securityFilterChain에 새 협력 객체를 요구하더라도, @WebMvcTest
 * 슬라이스에 그 빈이 없어 컨텍스트 로딩이 깨지는 일을 막기 위해서다.
 *
 * <p>CorsProperties는 SecurityConfig가 아니라 WebMvcConfigurer인 CorsConfig가 요구한다.
 * CorsConfig는 @WebMvcTest 슬라이스에 포함되므로 이 선언은 남겨 둔다.
 */
@WebMvcTest(ReferenceDataController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ReferenceDataService.class)
@EnableConfigurationProperties(CorsProperties.class)
class ReferenceDataControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getsSkinReportOptions() throws Exception {
        mockMvc.perform(get("/v1/reference-data/skin-report-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("2026-08-09"))
                .andExpect(jsonPath("$.areas.length()").value(13))
                .andExpect(jsonPath("$.areas[0].value").value("LEFT_FOREHEAD"))
                .andExpect(jsonPath("$.areas[0].label").value("왼쪽 이마"))
                .andExpect(jsonPath("$.areas[12].value").value("OTHER"))
                .andExpect(jsonPath("$.appearances.length()").value(8))
                .andExpect(jsonPath("$.sensations.length()").value(7))
                .andExpect(jsonPath("$.situations.length()").value(9))
                .andExpect(jsonPath("$.situations[7].value").value("OTHER"))
                .andExpect(jsonPath("$.situations[8].value").value("NONE_RECALLED"))
                .andExpect(jsonPath("$.careAvailability.length()").value(4))
                .andExpect(jsonPath("$.preCareChecks.length()").value(4))
                .andExpect(jsonPath("$.preCareChecks[3].value").value("NONE"));
    }
}
