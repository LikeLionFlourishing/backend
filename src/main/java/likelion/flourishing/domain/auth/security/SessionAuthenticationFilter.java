package likelion.flourishing.domain.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    /**
     * 세션 쿠키를 아예 보지 않는 경로.
     *
     * <p>가입과 로그인은 인증이 필요 없는데도 상태를 바꾸는 POST라, 브라우저에 살아 있는 세션
     * 쿠키가 남아 있으면 CSRF 검사에 걸려 403이 됐다. 로그인 화면을 다시 제출하는 흐름이
     * 원인을 알 수 없는 실패로 끝난다. 인증을 요구하지 않는 경로는 쿠키를 무시한다.
     */
    private static final Set<String> ANONYMOUS_ENDPOINTS = Set.of("POST /v1/users", "POST /v1/sessions");

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
        if (isAnonymousEndpoint(request)) {
            filterChain.doFilter(request, response);
            return;
        }

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

    private boolean isAnonymousEndpoint(HttpServletRequest request) {
        return ANONYMOUS_ENDPOINTS.contains(request.getMethod() + " " + request.getRequestURI());
    }

    private void setAuthentication(AuthenticatedUser principal) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of())
        );
    }
}
