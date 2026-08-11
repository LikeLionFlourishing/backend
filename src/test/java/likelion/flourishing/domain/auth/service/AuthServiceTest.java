package likelion.flourishing.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    private static final String EMAIL = "soldier@example.com";
    private static final String PASSWORD = "correct-horse-battery-staple";
    private static final String CLIENT_IP = "127.0.0.1";

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
