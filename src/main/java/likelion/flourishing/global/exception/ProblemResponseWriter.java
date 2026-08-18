package likelion.flourishing.global.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import likelion.flourishing.global.response.ErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * 필터와 Security 진입점처럼 {@code @RestControllerAdvice}를 거치지 않는 지점에서 Problem 응답을 직접 쓴다.
 */
@Component
public class ProblemResponseWriter {

    private final ProblemFactory problemFactory;
    private final ObjectMapper objectMapper;

    public ProblemResponseWriter(ProblemFactory problemFactory, ObjectMapper objectMapper) {
        this.problemFactory = problemFactory;
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }

        ErrorResponse body = problemFactory.create(errorCode, request);
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
