package likelion.flourishing.domain.auth.service;

import java.time.Duration;
import likelion.flourishing.domain.auth.dto.request.LoginRequest;
import likelion.flourishing.domain.auth.dto.request.RegisterRequest;
import likelion.flourishing.domain.auth.dto.response.AuthSessionResponse;
import likelion.flourishing.domain.auth.dto.response.UserResponse;
import likelion.flourishing.domain.auth.entity.User;
import likelion.flourishing.domain.auth.repository.UserRepository;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.global.config.AuthProperties;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import likelion.flourishing.global.exception.TooManyRequestsException;
import likelion.flourishing.support.RateLimitResult;
import likelion.flourishing.support.RateLimiter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원가입, 로그인, 로그아웃, 내 계정 조회와 삭제. */
@Service
public class AuthService {

    /**
     * 존재하지 않는 이메일로 로그인해도 비밀번호 검증과 비슷한 시간을 쓰게 만들어
     * 응답 시간만으로 가입 여부를 알아내지 못하게 한다.
     */
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$C6UzMDM.H6dfI/f/IKcEe.7Uy0MU9r0hCoTiv0v0vT5xVJUbHi4tW";

    private final UserRepository userRepository;
    private final SessionService sessionService;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;
    private final AuthProperties authProperties;

    public AuthService(
            UserRepository userRepository,
            SessionService sessionService,
            PasswordEncoder passwordEncoder,
            RateLimiter rateLimiter,
            AuthProperties authProperties
    ) {
        this.userRepository = userRepository;
        this.sessionService = sessionService;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.authProperties = authProperties;
    }

    @Transactional
    public AuthSessionIssue register(RegisterRequest request, String clientIp) {
        checkRateLimit("register", clientIp, authProperties.rateLimit().register());

        String normalizedEmail = User.normalizeEmail(request.email());
        if (userRepository.existsByNormalizedEmail(normalizedEmail)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        User user = saveNewUser(request);
        IssuedSession session = sessionService.issue(user.getId());
        return toIssue(user, session);
    }

    @Transactional
    public AuthSessionIssue login(LoginRequest request, String clientIp) {
        String normalizedEmail = User.normalizeEmail(request.email());
        checkRateLimit("login-ip", clientIp, authProperties.rateLimit().loginPerIp());
        checkRateLimit("login-email", normalizedEmail, authProperties.rateLimit().loginPerEmail());

        User user = userRepository.findByNormalizedEmail(normalizedEmail).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), DUMMY_PASSWORD_HASH);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        IssuedSession session = sessionService.issue(user.getId());
        return toIssue(user, session);
    }

    @Transactional(readOnly = true)
    public AuthSessionResponse currentSession(AuthenticatedUser principal) {
        User user = findUser(principal);
        return AuthSessionResponse.from(user, principal.csrfToken(), principal.expiresAt());
    }

    @Transactional(readOnly = true)
    public UserResponse getMe(AuthenticatedUser principal) {
        return UserResponse.from(findUser(principal));
    }

    @Transactional
    public void logout(AuthenticatedUser principal) {
        sessionService.revoke(principal.sessionId());
    }

    /**
     * 계정을 지우면 users를 참조하는 보고, 결과, 경과, Push 구독, 세션이 FK의 ON DELETE CASCADE로 함께 사라진다.
     * 측정 이벤트는 user_id가 NULL이 되어 피부 상세정보 없는 집계로만 남는다.
     */
    @Transactional
    public void deleteMe(AuthenticatedUser principal) {
        User user = findUser(principal);
        userRepository.delete(user);
    }

    private User saveNewUser(RegisterRequest request) {
        try {
            return userRepository.saveAndFlush(
                    User.register(request.email(), passwordEncoder.encode(request.password()))
            );
        } catch (DataIntegrityViolationException duplicateEmail) {
            // 같은 이메일이 동시에 들어오면 유니크 제약이 먼저 걸린다.
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }
    }

    private User findUser(AuthenticatedUser principal) {
        return userRepository.findById(principal.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED));
    }

    private AuthSessionIssue toIssue(User user, IssuedSession session) {
        return new AuthSessionIssue(
                AuthSessionResponse.from(user, session.csrfToken(), session.expiresAt()),
                session.sessionToken()
        );
    }

    private void checkRateLimit(String scope, String identifier, AuthProperties.RateLimit.Rule rule) {
        if (identifier == null || identifier.isBlank()) {
            return;
        }
        Duration window = rule.window();
        RateLimitResult result = rateLimiter.consume(scope, identifier, rule.limit(), window);
        if (!result.allowed()) {
            throw new TooManyRequestsException(result);
        }
    }
}
