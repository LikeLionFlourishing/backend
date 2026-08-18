package likelion.flourishing.domain.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import likelion.flourishing.domain.auth.service.SessionCookieFactory;
import likelion.flourishing.domain.auth.service.SessionService;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ProblemResponseWriter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 세션 쿠키로 요청 주체를 확인하고 상태 변경 요청의 X-CSRF-Token을 검증한다.
 * 쿠키가 없거나 세션이 유효하지 않으면 인증하지 않고 통과시켜 Security의 접근 정책이 401을 결정하게 한다.
 */
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    public static final String CSRF_HEADER = "X-CSRF-Token";

    private final SessionService sessionService;
    private final SessionCookieFactory sessionCookieFactory;
    private final ProblemResponseWriter problemResponseWriter;

    public SessionAuthenticationFilter(
            SessionService sessionService,
            SessionCookieFactory sessionCookieFactory,
            ProblemResponseWriter problemResponseWriter
    ) {
        this.sessionService = sessionService;
        this.sessionCookieFactory = sessionCookieFactory;
        this.problemResponseWriter = problemResponseWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Optional<String> sessionToken = sessionCookieFactory.readToken(request);

        if (sessionToken.isPresent() && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                sessionService
                        .authenticate(sessionToken.get(), request.getMethod(), request.getHeader(CSRF_HEADER))
                        .ifPresent(this::setAuthentication);
            } catch (BusinessException csrfFailure) {
                SecurityContextHolder.clearContext();
                problemResponseWriter.write(request, response, csrfFailure.getErrorCode());
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void setAuthentication(AuthenticatedUser principal) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of())
        );
    }
}
