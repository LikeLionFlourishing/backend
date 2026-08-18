package likelion.flourishing.domain.auth.security;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 세션 쿠키로 확인된 요청 주체.
 *
 * @param userId    사용자 식별자
 * @param sessionId 현재 세션 식별자
 * @param expiresAt 세션 만료 시각(UTC)
 * @param csrfToken 이 세션의 X-CSRF-Token 값
 */
public record AuthenticatedUser(UUID userId, UUID sessionId, LocalDateTime expiresAt, String csrfToken) {
}
