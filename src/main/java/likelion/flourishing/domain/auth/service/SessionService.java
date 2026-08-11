package likelion.flourishing.domain.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import likelion.flourishing.domain.auth.entity.UserSession;
import likelion.flourishing.domain.auth.repository.UserSessionRepository;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.global.config.AuthProperties;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 세션 발급, 쿠키 검증, 폐기를 담당한다. */
@Service
public class SessionService {

    /** 요청마다 쓰기를 만들지 않도록 마지막 접속 시각은 이 간격을 넘겼을 때만 갱신한다. */
    private static final Duration LAST_SEEN_UPDATE_THRESHOLD = Duration.ofMinutes(5);

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final UserSessionRepository userSessionRepository;
    private final SessionTokenFactory sessionTokenFactory;
    private final AuthProperties authProperties;
    private final Clock clock;

    public SessionService(
            UserSessionRepository userSessionRepository,
            SessionTokenFactory sessionTokenFactory,
            AuthProperties authProperties,
            Clock clock
    ) {
        this.userSessionRepository = userSessionRepository;
        this.sessionTokenFactory = sessionTokenFactory;
        this.authProperties = authProperties;
        this.clock = clock;
    }

    @Transactional
    public IssuedSession issue(UUID userId) {
        String sessionToken = sessionTokenFactory.createSessionToken();
        String csrfToken = sessionTokenFactory.deriveCsrfToken(sessionToken);

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime expiresAt = now.plus(authProperties.session().ttl());

        UserSession session = userSessionRepository.save(UserSession.issue(
                userId,
                sessionTokenFactory.hash(sessionToken),
                sessionTokenFactory.hash(csrfToken),
                now,
                expiresAt
        ));

        return new IssuedSession(session.getId(), sessionToken, csrfToken, expiresAt);
    }

    /**
     * 쿠키의 세션 토큰을 확인한다. 상태 변경 요청이면 X-CSRF-Token까지 검증한다.
     * 만료된 세션은 바로 지우고 인증되지 않은 요청으로 처리한다.
     *
     * @throws BusinessException CSRF 토큰이 없거나 일치하지 않을 때
     */
    @Transactional
    public Optional<AuthenticatedUser> authenticate(String sessionToken, String method, String csrfHeader) {
        Optional<UserSession> found = userSessionRepository.findBySessionTokenHash(
                sessionTokenFactory.hash(sessionToken)
        );
        if (found.isEmpty()) {
            return Optional.empty();
        }

        UserSession session = found.get();
        LocalDateTime now = LocalDateTime.now(clock);
        if (session.isExpired(now)) {
            userSessionRepository.delete(session);
            return Optional.empty();
        }

        if (requiresCsrfToken(method) && !sessionTokenFactory.matches(session.getCsrfSecretHash(), csrfHeader)) {
            throw new BusinessException(ErrorCode.CSRF_TOKEN_INVALID);
        }

        if (session.getLastSeenAt().plus(LAST_SEEN_UPDATE_THRESHOLD).isBefore(now)) {
            session.touch(now);
        }

        return Optional.of(new AuthenticatedUser(
                session.getUserId(),
                session.getId(),
                session.getExpiresAt(),
                sessionTokenFactory.deriveCsrfToken(sessionToken)
        ));
    }

    @Transactional
    public void revoke(UUID sessionId) {
        userSessionRepository.deleteById(sessionId);
    }

    private boolean requiresCsrfToken(String method) {
        return method != null && !SAFE_METHODS.contains(method.toUpperCase(Locale.ROOT));
    }
}
