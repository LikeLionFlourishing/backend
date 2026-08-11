package likelion.flourishing.global.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.constraints.NotBlank;
import likelion.flourishing.global.config.CorsProperties;
import likelion.flourishing.global.config.ProblemProperties;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@AutoConfigureMockMvc(addFilters = false)
@EnableConfigurationProperties({CorsProperties.class, ProblemProperties.class})
@Import({
        GlobalExceptionHandler.class,
        ProblemFactory.class,
        GlobalExceptionHandlerTest.TestController.class
})
/**
 * 예외를 명세 Problem 형식으로 바꾸는 규칙 테스트.
 *
 * <p>실제 도메인 컨트롤러 대신 이 테스트 안에 임시 컨트롤러를 두고 일부러 예외를 던진다.
 * 특정 기능에 얽매이지 않고 변환 규칙만 확인하기 위해서다.
 *
 * <p>확인하는 것: BusinessException이 코드에 맞는 상태와 problem+json이 되는지,
 * 검증 실패가 어느 필드가 왜 틀렸는지 errors 배열에 담는지, 깨진 JSON이 400인지,
 * 그리고 예상 못 한 예외가 500이 되면서 내부 메시지나 스택 트레이스를 응답에 노출하지 않는지.
 */
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void businessExceptionReturnsProblemJson() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", Matchers.startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                .andExpect(jsonPath("$.type").value("https://api.example.invalid/problems/bad-request"))
                .andExpect(jsonPath("$.title").value(ErrorCode.BAD_REQUEST.getTitle()))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value(ErrorCode.BAD_REQUEST.getDetail()))
                .andExpect(jsonPath("$.instance").value("/test/business"))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.requestId").value(Matchers.startsWith("req_")))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void validationFailureReturnsFieldErrors() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].code").exists())
                .andExpect(jsonPath("$.errors[0].message").exists());
    }

    @Test
    void malformedJsonReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void unexpectedExceptionHidesInternalDetails() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.detail").value(ErrorCode.INTERNAL_SERVER_ERROR.getDetail()))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @Validated
    @RestController
    static class TestController {

        @GetMapping("/test/business")
        void business() {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        @PostMapping("/test/validation")
        void validation(@RequestBody @jakarta.validation.Valid TestRequest request) {
        }

        @GetMapping("/test/unexpected")
        void unexpected() {
            throw new IllegalStateException("데이터베이스 비밀번호가 잘못되었습니다");
        }
    }

    record TestRequest(@NotBlank String name) {
    }
}
