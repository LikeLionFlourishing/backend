package likelion.flourishing.domain.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import likelion.flourishing.support.UuidV7;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.hibernate.type.SqlTypes;

/**
 * HttpOnly 쿠키용 서버 세션.
 *
 * <p>쿠키로 오가는 세션 토큰과 CSRF 토큰은 원문을 저장하지 않고 SHA-256 해시만 저장한다.
 * 테이블에 updated_at이 없으므로 BaseTimeEntity를 상속하지 않는다.
 */
@Entity
@Getter
@Table(name = "user_sessions")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSession {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "session_token_hash", nullable = false, length = 32)
    private byte[] sessionTokenHash;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "csrf_secret_hash", nullable = false, length = 32)
    private byte[] csrfSecretHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private UserSession(
            UUID id,
            UUID userId,
            byte[] sessionTokenHash,
            byte[] csrfSecretHash,
            LocalDateTime expiresAt,
            LocalDateTime lastSeenAt
    ) {
        this.id = id;
        this.userId = userId;
        this.sessionTokenHash = sessionTokenHash;
        this.csrfSecretHash = csrfSecretHash;
        this.expiresAt = expiresAt;
        this.lastSeenAt = lastSeenAt;
    }

    public static UserSession issue(
            UUID userId,
            byte[] sessionTokenHash,
            byte[] csrfSecretHash,
            LocalDateTime now,
            LocalDateTime expiresAt
    ) {
        return new UserSession(UuidV7.generate(), userId, sessionTokenHash, csrfSecretHash, expiresAt, now);
    }

    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }

    public void touch(LocalDateTime now) {
        this.lastSeenAt = now;
    }
}
