package likelion.flourishing.domain.referencedata.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 선택값 조회가 실제 보안 필터 체인 뒤에 있는지 검증한다.
 *
 * <p>SecurityConfig의 마지막 규칙이 anyRequest().denyAll()이라, 화이트리스트에서
 * /v1/reference-data/**가 빠지면 이 API가 통째로 막힌다. 이 테스트는 그 경계가 살아 있는지를 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReferenceDataSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * 상태코드를 4xx로 넓게 두는 이유: 지금은 기본 엔트리 포인트가 403을 주지만, 인증 기능이
     * 병합되면 ProblemAuthenticationEntryPoint가 401 AUTHENTICATION_REQUIRED를
     * application/problem+json으로 돌려준다. 명세상 401이 최종 동작이므로, 그 전환에 이 테스트가
     * 깨지지 않게 한다. 병합 후 isUnauthorized()와 응답 본문까지 단언하도록 좁힌다.
     */
    @Test
    void 미인증_요청은_선택값을_받지_못한다() throws Exception {
        mockMvc.perform(get("/v1/reference-data/skin-report-options"))
                .andExpect(status().is4xxClientError())
                .andExpect(content().string(not(containsString("2026-08-09"))));
    }
}
