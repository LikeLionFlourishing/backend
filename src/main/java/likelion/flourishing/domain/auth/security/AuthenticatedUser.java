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

    /**
     * csrfToken을 지운 표기.
     *
     * <p>이 객체는 SecurityContext의 principal로 실리기 때문에 Security 디버그 로그나 principal을
     * 찍는 코드가 한 번이라도 생기면 기본 record toString이 CSRF 토큰을 그대로 흘린다.
     */
    @Override
    public String toString() {
        return "AuthenticatedUser[userId=" + userId + ", sessionId=" + sessionId
                + ", expiresAt=" + expiresAt + ", csrfToken=(redacted)]";
    }
}
