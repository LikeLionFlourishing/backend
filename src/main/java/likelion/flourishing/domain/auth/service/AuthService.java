package likelion.flourishing.domain.auth.service;

import java.time.Duration;
import java.time.LocalDateTime;
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

    /**
     * 로그인.
     *
     * <p>이메일 단위 제한은 <b>실패한 시도만</b> 센다. 자격 증명을 확인하기 전에 세면 제3자가 남의
     * 이메일로 창을 채우는 것만으로 그 계정의 정당한 로그인까지 429로 막을 수 있다. IP를 바꿔가며
     * 채우면 잠금이 무기한 이어진다. 비밀번호가 맞는 요청은 창 상태와 무관하게 통과시키고,
     * 성공하면 그동안 쌓인 실패 기록을 지운다.
     *
     * <p>대입 방어는 그대로 남는다. 틀린 비밀번호는 계속 창을 채우므로 이메일 하나당 시도 횟수가
     * 여전히 묶이고, 요청 자체의 총량은 IP 단위 제한이 먼저 막는다.
     */
    @Transactional
    public AuthSessionIssue login(LoginRequest request, String clientIp) {
        String normalizedEmail = User.normalizeEmail(request.email());
        checkRateLimit("login-ip", clientIp, authProperties.rateLimit().loginPerIp());

        User user = userRepository.findByNormalizedEmail(normalizedEmail).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), DUMMY_PASSWORD_HASH);
            throw failedLogin(normalizedEmail);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw failedLogin(normalizedEmail);
        }

        rateLimiter.reset("login-email", normalizedEmail);
        IssuedSession session = sessionService.issue(user.getId());
        return toIssue(user, session);
    }

    /**
     * 실패한 시도를 세고 자격 증명 오류를 만든다.
     *
     * <p>창을 넘겼으면 여기서 {@link TooManyRequestsException}이 먼저 나간다. 이 자리에 온 요청은
     * 이미 비밀번호가 틀린 것이라, 429와 401 중 무엇이 나가든 알려 주는 정보가 늘지 않는다.
     */
    private BusinessException failedLogin(String normalizedEmail) {
        checkRateLimit("login-email", normalizedEmail, authProperties.rateLimit().loginPerEmail());
        return new BusinessException(ErrorCode.INVALID_CREDENTIALS);
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
     * 온보딩이 최초 설정을 마쳤을 때 호출한다. users는 auth가 소유하므로 다른 도메인이
     * 엔티티를 직접 다루지 않고 이 메서드를 거친다.
     *
     * <p>이미 완료한 사용자를 다시 호출해도 최초 시각을 유지한다. 이 약속을 지키려고 행에 잠금을
     * 걸고 읽는다. 잠그지 않으면 온보딩 완료 요청이 겹쳤을 때 두 트랜잭션이 모두
     * signup_completed_at이 비어 있는 상태를 읽고 각자 다른 시각을 써서, 나중에 커밋한 쪽이
     * 최초 시각을 덮어쓴다.
     *
     * @return 반영된 가입 완료 시각. 명세 Onboarding.completedAt 값이다.
     */
    @Transactional
    public LocalDateTime completeSignup(AuthenticatedUser principal, LocalDateTime now) {
        User user = userRepository.findByIdForUpdate(principal.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED));
        user.completeSignup(now);
        return user.getSignupCompletedAt();
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

    /**
     * 가입은 중복 이메일을 409 EMAIL_ALREADY_REGISTERED로 알려 준다. 명세가 정한 계약이라 따르지만,
     * 로그인이 없는 이메일과 틀린 비밀번호를 같은 오류로 감추는 것과는 방향이 다르다. 즉 가입
     * 엔드포인트는 계정 존재 여부를 그대로 알려 준다. IP 단위 제한(1시간 10회)만 있고 이메일 단위
     * 제한이 없어 IP를 나누면 열거가 가능하다는 것도 함께 알고 있는 상태다.
     */
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
