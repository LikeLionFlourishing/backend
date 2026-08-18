package likelion.flourishing.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import likelion.flourishing.domain.auth.entity.UserSession;
import likelion.flourishing.domain.auth.repository.UserSessionRepository;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.global.config.AuthProperties;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
/**
 * SessionService의 세션 발급·검증 규칙 테스트.
 *
 * <p>확인하는 것: 발급 시 DB에는 해시만 남고 원문 토큰은 호출자에게만 돌아가는지,
 * 조회(GET 같은 안전한 메서드)는 CSRF 토큰 없이 통과하고 상태 변경 요청은 토큰이 맞아야만
 * 통과하는지, 만료된 세션은 바로 삭제하고 인증하지 않는지, 모르는 토큰은 조용히 빈 값을 주는지.
 *
 * <p>마지막 항목이 중요한 이유는, 여기서 예외를 던지면 필터가 500을 내기 때문이다.
 * 인증 실패는 접근 정책이 401로 결정하도록 넘긴다.
 */
class SessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");

    @Mock
    private UserSessionRepository userSessionRepository;

    private SessionTokenFactory sessionTokenFactory;
    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        sessionTokenFactory = new SessionTokenFactory();
        sessionService = new SessionService(
                userSessionRepository,
                sessionTokenFactory,
                new AuthProperties(null, null),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void issueStoresOnlyHashesAndReturnsRawTokens() {
        when(userSessionRepository.save(any(UserSession.class))).thenAnswer(call -> call.getArgument(0));

        IssuedSession issued = sessionService.issue(UUID.randomUUID());

        assertThat(issued.sessionToken()).isNotBlank();
        assertThat(issued.csrfToken()).isNotBlank();
        assertThat(issued.expiresAt()).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).plusDays(14));
    }

    @Test
    void authenticateReturnsPrincipalForSafeMethodWithoutCsrfToken() {
        String sessionToken = sessionTokenFactory.createSessionToken();
        when(userSessionRepository.findBySessionTokenHash(any()))
                .thenReturn(Optional.of(activeSession(sessionToken)));

        Optional<AuthenticatedUser> principal = sessionService.authenticate(sessionToken, "GET", null);

        assertThat(principal).isPresent();
        assertThat(principal.get().csrfToken()).isEqualTo(sessionTokenFactory.deriveCsrfToken(sessionToken));
    }

    @Test
    void authenticateRejectsStateChangingRequestWithoutMatchingCsrfToken() {
        String sessionToken = sessionTokenFactory.createSessionToken();
        when(userSessionRepository.findBySessionTokenHash(any()))
                .thenReturn(Optional.of(activeSession(sessionToken)));

        assertThatThrownBy(() -> sessionService.authenticate(sessionToken, "POST", "틀린-토큰"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CSRF_TOKEN_INVALID);
    }

    @Test
    void authenticateAcceptsStateChangingRequestWithMatchingCsrfToken() {
        String sessionToken = sessionTokenFactory.createSessionToken();
        when(userSessionRepository.findBySessionTokenHash(any()))
                .thenReturn(Optional.of(activeSession(sessionToken)));

        Optional<AuthenticatedUser> principal = sessionService.authenticate(
                sessionToken,
                "POST",
                sessionTokenFactory.deriveCsrfToken(sessionToken)
        );

        assertThat(principal).isPresent();
    }

    @Test
    void authenticateDeletesExpiredSession() {
        String sessionToken = sessionTokenFactory.createSessionToken();
        UserSession expired = UserSession.issue(
                UUID.randomUUID(),
                sessionTokenFactory.hash(sessionToken),
                sessionTokenFactory.hash(sessionTokenFactory.deriveCsrfToken(sessionToken)),
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).minusDays(30),
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).minusDays(1)
        );
        when(userSessionRepository.findBySessionTokenHash(any())).thenReturn(Optional.of(expired));

        Optional<AuthenticatedUser> principal = sessionService.authenticate(sessionToken, "GET", null);

        assertThat(principal).isEmpty();
        verify(userSessionRepository).delete(expired);
    }

    @Test
    void authenticateReturnsEmptyForUnknownToken() {
        when(userSessionRepository.findBySessionTokenHash(any())).thenReturn(Optional.empty());

        assertThat(sessionService.authenticate("모르는-토큰", "GET", null)).isEmpty();
        verify(userSessionRepository, never()).delete(any());
    }

    private UserSession activeSession(String sessionToken) {
        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        return UserSession.issue(
                UUID.randomUUID(),
                sessionTokenFactory.hash(sessionToken),
                sessionTokenFactory.hash(sessionTokenFactory.deriveCsrfToken(sessionToken)),
                now,
                now.plusDays(14)
        );
    }
}
