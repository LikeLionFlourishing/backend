package likelion.flourishing.domain.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import likelion.flourishing.domain.auth.dto.request.LoginRequest;
import likelion.flourishing.domain.auth.dto.response.AuthSessionResponse;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.auth.service.AuthService;
import likelion.flourishing.domain.auth.service.AuthSessionIssue;
import likelion.flourishing.domain.auth.service.SessionCookieFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 세션(로그인 상태) 엔드포인트. 명세 Authentication 태그 중 /sessions 경로를 담당한다.
 *
 * <p>POST /v1/sessions 로그인, GET /v1/sessions/current 현재 세션 조회,
 * DELETE /v1/sessions/current 로그아웃 세 개를 제공한다.
 *
 * <p>세션 토큰은 응답 본문이 아니라 Set-Cookie 헤더로만 내보낸다. 본문에는 상태 변경 요청에
 * 필요한 CSRF 토큰과 만료 시각만 담는다. 로그아웃은 만료된 쿠키를 함께 내려 브라우저에서 지운다.
 *
 * <p>경로에 /v1을 직접 붙인 이유는 명세 servers가 /v1이기 때문이다. 전역 prefix를 쓰면
 * /health와 Swagger 경로까지 바뀌어서 컨트롤러마다 직접 붙였다.
 */
@Tag(name = "Authentication", description = "회원가입과 쿠키 세션")
@RestController
@RequestMapping("/v1/sessions")
public class SessionController {

    private final AuthService authService;
    private final SessionCookieFactory sessionCookieFactory;

    public SessionController(AuthService authService, SessionCookieFactory sessionCookieFactory) {
        this.authService = authService;
        this.sessionCookieFactory = sessionCookieFactory;
    }

    @Operation(summary = "로그인")
    @PostMapping
    public ResponseEntity<AuthSessionResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthSessionIssue issue = authService.login(request, servletRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, sessionCookieFactory.create(issue.sessionToken()).toString())
                .body(issue.session());
    }

    @Operation(summary = "현재 세션 조회")
    @GetMapping("/current")
    public ResponseEntity<AuthSessionResponse> getCurrentSession(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ResponseEntity.ok(authService.currentSession(principal));
    }

    @Operation(summary = "로그아웃")
    @DeleteMapping("/current")
    public ResponseEntity<Void> deleteCurrentSession(@AuthenticationPrincipal AuthenticatedUser principal) {
        authService.logout(principal);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, sessionCookieFactory.clear().toString())
                .build();
    }
}
