package likelion.flourishing.domain.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import likelion.flourishing.global.exception.ErrorCode;
import likelion.flourishing.global.exception.ProblemResponseWriter;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/** 세션이 없거나 만료된 요청에 명세의 401 Problem을 돌려준다. */
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ProblemResponseWriter problemResponseWriter;

    public ProblemAuthenticationEntryPoint(ProblemResponseWriter problemResponseWriter) {
        this.problemResponseWriter = problemResponseWriter;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authenticationException
    ) throws IOException {
        problemResponseWriter.write(request, response, ErrorCode.AUTHENTICATION_REQUIRED);
    }
}
