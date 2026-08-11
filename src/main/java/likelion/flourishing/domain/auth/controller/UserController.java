package likelion.flourishing.domain.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import likelion.flourishing.domain.auth.dto.request.RegisterRequest;
import likelion.flourishing.domain.auth.dto.response.AuthSessionResponse;
import likelion.flourishing.domain.auth.dto.response.UserResponse;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.auth.service.AuthService;
import likelion.flourishing.domain.auth.service.AuthSessionIssue;
import likelion.flourishing.domain.auth.service.SessionCookieFactory;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 계정 엔드포인트. 명세 Authentication 태그 중 /users와 /me 경로를 담당한다.
 *
 * <p>POST /v1/users 회원가입, GET /v1/me 내 계정 조회, DELETE /v1/me 계정 삭제를 제공한다.
 * 회원가입은 계정을 만들고 곧바로 로그인 세션까지 발급해 별도 로그인 요청 없이 이어지게 한다.
 *
 * <p>계정 삭제는 되돌릴 수 없어 X-Confirm-Deletion 헤더 값이 정확할 때만 진행한다.
 * 실수로 부른 DELETE를 막는 장치라 서비스가 아니라 요청 경계인 여기서 검사한다.
 */
@Tag(name = "Authentication", description = "회원가입과 쿠키 세션")
@RestController
@RequestMapping("/v1")
public class UserController {

    private static final String DELETE_CONFIRMATION_HEADER = "X-Confirm-Deletion";
    private static final String DELETE_CONFIRMATION_VALUE = "delete-account";

    private final AuthService authService;
    private final SessionCookieFactory sessionCookieFactory;

    public UserController(AuthService authService, SessionCookieFactory sessionCookieFactory) {
        this.authService = authService;
        this.sessionCookieFactory = sessionCookieFactory;
    }

    @Operation(summary = "이메일·비밀번호 회원가입")
    @PostMapping("/users")
    public ResponseEntity<AuthSessionResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthSessionIssue issue = authService.register(request, servletRequest.getRemoteAddr());
        return ResponseEntity.created(URI.create("/v1/me"))
                .header(HttpHeaders.SET_COOKIE, sessionCookieFactory.create(issue.sessionToken()).toString())
                .body(issue.session());
    }

    @Operation(summary = "내 계정 조회")
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(authService.getMe(principal));
    }

    @Operation(summary = "계정과 전체 기록 즉시 삭제")
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMe(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestHeader(name = DELETE_CONFIRMATION_HEADER, required = false) String confirmation
    ) {
        if (!DELETE_CONFIRMATION_VALUE.equals(confirmation)) {
            throw new BusinessException(ErrorCode.DELETE_CONFIRMATION_REQUIRED);
        }

        authService.deleteMe(principal);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, sessionCookieFactory.clear().toString())
                .build();
    }
}
