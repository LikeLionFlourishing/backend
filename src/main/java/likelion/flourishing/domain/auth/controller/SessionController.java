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
