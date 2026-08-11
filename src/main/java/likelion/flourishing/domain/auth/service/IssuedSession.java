package likelion.flourishing.domain.auth.service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 새로 발급한 세션. sessionToken은 쿠키로만 나가고 저장하지 않는다.
 *
 * @param sessionId    세션 식별자
 * @param sessionToken 쿠키에 담을 원본 세션 토큰
 * @param csrfToken    응답 본문에 담을 CSRF 토큰
 * @param expiresAt    만료 시각(UTC)
 */
public record IssuedSession(UUID sessionId, String sessionToken, String csrfToken, LocalDateTime expiresAt) {
}
