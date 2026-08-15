package likelion.flourishing.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import likelion.flourishing.domain.auth.dto.request.LoginRequest;
import likelion.flourishing.domain.auth.dto.request.RegisterRequest;
import likelion.flourishing.domain.auth.entity.User;
import likelion.flourishing.domain.auth.repository.UserRepository;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.global.config.AuthProperties;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import likelion.flourishing.global.exception.TooManyRequestsException;
import likelion.flourishing.support.RateLimitResult;
import likelion.flourishing.support.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * AuthService의 회원가입·로그인 규칙 테스트. DB와 Redis 없이 가짜 객체로만 돌린다.
 *
 * <p>확인하는 것: 가입 시 정규화한 이메일을 저장하고 세션까지 발급하는지, 이미 가입된 이메일을
 * 막는지, 요청 제한을 넘기면 가입을 중단하는지, 비밀번호가 맞을 때만 세션을 주는지.
 *
 * <p>loginHidesWhetherEmailExists는 보안 검증이다. 없는 이메일로 로그인해도 비밀번호 대조를
 * 한 번 수행해, 응답이 돌아오는 속도만 보고 가입 여부를 알아내지 못하게 한 것을 확인한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    private static final String EMAIL = "soldier@example.com";
    private static final String PASSWORD = "correct-horse-battery-staple";
    private static final String CLIENT_IP = "127.0.0.1";
    private static final UUID USER_ID = UUID.fromString("2c56fe08-ea1f-45fc-915d-c35b7c0bca39");
    private static final LocalDateTime ONBOARDING_TIME = LocalDateTime.of(2026, 8, 14, 9, 0);

    @Mock
    private UserRepository userRepository;

    @Mock
    private SessionService sessionService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RateLimiter rateLimiter;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                sessionService,
                passwordEncoder,
                rateLimiter,
                new AuthProperties(null, null)
        );
        allowRateLimit();
        when(sessionService.issue(any())).thenReturn(new IssuedSession(
                UUID.randomUUID(),
                "session-token",
                "csrf-token",
                LocalDateTime.of(2026, 8, 24, 0, 0)
        ));
    }

    @Test
    void registerStoresNormalizedEmailAndIssuesSession() {
        when(userRepository.existsByNormalizedEmail("soldier@example.com")).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hashed");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(call -> persisted(call.getArgument(0)));

        AuthSessionIssue issue = authService.register(new RegisterRequest("  Soldier@Example.com ", PASSWORD), CLIENT_IP);

        assertThat(issue.sessionToken()).isEqualTo("session-token");
        assertThat(issue.session().getCsrfToken()).isEqualTo("csrf-token");
        assertThat(issue.session().getUser().getEmail()).isEqualTo("Soldier@Example.com");
        assertThat(issue.session().getUser().isSignupCompleted()).isFalse();
    }

    @Test
    void registerRejectsAlreadyRegisteredEmail() {
        when(userRepository.existsByNormalizedEmail(anyString())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest(EMAIL, PASSWORD), CLIENT_IP))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_ALREADY_REGISTERED);

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void registerStopsWhenRateLimitExceeded() {
        when(rateLimiter.consume(anyString(), anyString(), anyInt(), any(Duration.class)))
                .thenReturn(new RateLimitResult(false, 10, 0, 42, 1_800_000_000L));

        assertThatThrownBy(() -> authService.register(new RegisterRequest(EMAIL, PASSWORD), CLIENT_IP))
                .isInstanceOf(TooManyRequestsException.class);

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void loginIssuesSessionWhenPasswordMatches() {
        User user = persisted(User.register(EMAIL, "hashed"));
        when(userRepository.findByNormalizedEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, "hashed")).thenReturn(true);

        AuthSessionIssue issue = authService.login(new LoginRequest(EMAIL, PASSWORD), CLIENT_IP);

        assertThat(issue.sessionToken()).isEqualTo("session-token");
        verify(sessionService).issue(user.getId());
    }

    @Test
    void loginHidesWhetherEmailExists() {
        when(userRepository.findByNormalizedEmail(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, PASSWORD), CLIENT_IP))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void loginRejectsWrongPassword() {
        User user = persisted(User.register(EMAIL, "hashed"));
        when(userRepository.findByNormalizedEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, PASSWORD), CLIENT_IP))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        verify(sessionService, never()).issue(any());
    }

    @Test
    void completeSignupStampsCompletionTime() {
        User user = persisted(User.register(EMAIL, "hashed"));
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));

        LocalDateTime completedAt = authService.completeSignup(principal(), ONBOARDING_TIME);

        assertThat(completedAt).isEqualTo(ONBOARDING_TIME);
        assertThat(user.isSignupCompleted()).isTrue();
    }

    @Test
    void completeSignupKeepsFirstCompletionTime() {
        User user = persisted(User.register(EMAIL, "hashed"));
        user.completeSignup(ONBOARDING_TIME);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));

        LocalDateTime completedAt = authService.completeSignup(principal(), ONBOARDING_TIME.plusDays(3));

        assertThat(completedAt).isEqualTo(ONBOARDING_TIME);
    }

    /**
     * 최초 시각을 유지한다는 약속은 잠금 없이는 성립하지 않는다. 잠그지 않고 읽으면 겹친 두 요청이
     * 모두 비어 있는 상태를 읽어, 나중에 커밋한 쪽이 먼저 쓴 시각을 덮는다.
     */
    @Test
    void completeSignupReadsUserWithRowLock() {
        User user = persisted(User.register(EMAIL, "hashed"));
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));

        authService.completeSignup(principal(), ONBOARDING_TIME);

        verify(userRepository).findByIdForUpdate(USER_ID);
        verify(userRepository, never()).findById(USER_ID);
    }

    @Test
    void completeSignupRejectsUnknownUser() {
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.completeSignup(principal(), ONBOARDING_TIME))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED);
    }

    /**
     * 제3자가 남의 이메일로 창을 채워도 정당한 로그인을 막지 못해야 한다. 자격 증명을 확인하기
     * 전에 이메일 창을 세면 이 요청이 429가 되어 계정이 잠긴다.
     */
    @Test
    void loginWithCorrectPasswordIsNotBlockedByEmailWindow() {
        User user = persisted(User.register(EMAIL, "hashed"));
        when(userRepository.findByNormalizedEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, "hashed")).thenReturn(true);

        AuthSessionIssue issue = authService.login(new LoginRequest(EMAIL, PASSWORD), CLIENT_IP);

        assertThat(issue.sessionToken()).isEqualTo("session-token");
        verify(rateLimiter, never()).consume(eq("login-email"), anyString(), anyInt(), any(Duration.class));
        verify(rateLimiter).reset("login-email", EMAIL);
    }

    /** 틀린 비밀번호만 창을 채운다. 대입 방어는 그대로 남는다. */
    @Test
    void failedLoginConsumesTheEmailWindow() {
        User user = persisted(User.register(EMAIL, "hashed"));
        when(userRepository.findByNormalizedEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, PASSWORD), CLIENT_IP))
                .isInstanceOf(BusinessException.class);

        verify(rateLimiter).consume(eq("login-email"), eq(EMAIL), anyInt(), any(Duration.class));
        verify(rateLimiter, never()).reset(anyString(), anyString());
    }

    /** 없는 이메일로 실패해도 같은 창을 쓴다. 응답은 여전히 INVALID_CREDENTIALS다. */
    @Test
    void failedLoginForUnknownEmailAlsoConsumesTheWindow() {
        when(userRepository.findByNormalizedEmail(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, PASSWORD), CLIENT_IP))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        verify(rateLimiter).consume(eq("login-email"), eq(EMAIL), anyInt(), any(Duration.class));
    }

    private AuthenticatedUser principal() {
        return new AuthenticatedUser(
                USER_ID,
                UUID.randomUUID(),
                LocalDateTime.of(2026, 8, 24, 0, 0),
                "csrf-token-value-that-is-long-enough"
        );
    }

    private void allowRateLimit() {
        when(rateLimiter.consume(anyString(), anyString(), anyInt(), any(Duration.class)))
                .thenReturn(new RateLimitResult(true, 10, 9, 600, 1_800_000_000L));
    }

    /** 저장 시 JPA Auditing이 채우는 생성 시각을 흉내낸다. */
    private User persisted(User user) {
        ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.of(2026, 8, 10, 0, 0));
        ReflectionTestUtils.setField(user, "updatedAt", LocalDateTime.of(2026, 8, 10, 0, 0));
        return user;
    }
}
