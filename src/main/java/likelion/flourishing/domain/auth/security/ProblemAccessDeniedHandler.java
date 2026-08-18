package likelion.flourishing.domain.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import likelion.flourishing.global.exception.ErrorCode;
import likelion.flourishing.global.exception.ProblemResponseWriter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/** 인증은 되었지만 접근이 막힌 요청에 403 Problem을 돌려준다. */
public class ProblemAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemResponseWriter problemResponseWriter;

    public ProblemAccessDeniedHandler(ProblemResponseWriter problemResponseWriter) {
        this.problemResponseWriter = problemResponseWriter;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        problemResponseWriter.write(request, response, ErrorCode.ACCESS_DENIED);
    }
}
