package likelion.flourishing.referencedata.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import likelion.flourishing.global.config.CorsProperties;
import likelion.flourishing.global.config.SecurityConfig;
import likelion.flourishing.referencedata.service.ReferenceDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReferenceDataController.class)
@Import({SecurityConfig.class, ReferenceDataService.class})
@EnableConfigurationProperties(CorsProperties.class)
class ReferenceDataControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser
    void authenticatedUserGetsSkinReportOptions() throws Exception {
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

    @Test
    void anonymousUserCannotGetSkinReportOptions() throws Exception {
        mockMvc.perform(get("/v1/reference-data/skin-report-options"))
                .andExpect(status().isForbidden());
    }
}
