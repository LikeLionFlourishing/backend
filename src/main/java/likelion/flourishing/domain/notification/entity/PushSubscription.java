package likelion.flourishing.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import likelion.flourishing.global.entity.BaseTimeEntity;
import likelion.flourishing.support.UuidV7;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 표준 Web Push 구독. (user_id, endpoint_fingerprint)에 유니크 제약이 걸려 있어
 * 같은 사용자가 같은 endpoint를 다시 등록하면 행이 늘지 않고 갱신된다.
 *
 * <p>endpoint와 두 키는 평문으로 저장하지 않는다. 세 값이 모두 암호문이라 SQL로는 비교할 수 없고,
 * 그래서 결정적인 지문 컬럼을 따로 둔다.
 *
 * <p>Push 서비스가 404나 410을 돌려주면 그 구독은 영구히 못 쓰는 것이므로 active를 내린다.
 * 행을 지우지 않는 이유는 사용자가 같은 endpoint로 다시 구독할 때 되살릴 수 있어야 하기 때문이다.
 */
@Entity
@Getter
@Table(name = "push_subscriptions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushSubscription extends BaseTimeEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    // DDL이 고정 길이 BINARY(32)라서 기본 매핑(VARBINARY)이 아니라 BINARY로 못 박는다.
    // 그렇지 않으면 ddl-auto = validate가 타입 불일치로 기동을 막는다.
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "endpoint_fingerprint", nullable = false, updatable = false, length = 32)
    private byte[] endpointFingerprint;

    @Column(name = "endpoint_ciphertext", nullable = false, length = 4096)
    private byte[] endpointCiphertext;

    @Column(name = "p256dh_ciphertext", nullable = false, length = 1024)
    private byte[] p256dhCiphertext;

    @Column(name = "auth_ciphertext", nullable = false, length = 512)
    private byte[] authCiphertext;

    @Column(name = "user_agent", nullable = false, length = 512)
    private String userAgent;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "last_success_at")
    private LocalDateTime lastSuccessAt;

    private PushSubscription(
            UUID userId,
            byte[] endpointFingerprint,
            byte[] endpointCiphertext,
            byte[] p256dhCiphertext,
            byte[] authCiphertext,
            String userAgent,
            LocalDateTime expiresAt
    ) {
        this.id = UuidV7.generate();
        this.userId = userId;
        this.endpointFingerprint = endpointFingerprint;
        this.endpointCiphertext = endpointCiphertext;
        this.p256dhCiphertext = p256dhCiphertext;
        this.authCiphertext = authCiphertext;
        this.userAgent = userAgent;
        this.expiresAt = expiresAt;
        this.active = true;
    }

    public static PushSubscription register(
            UUID userId,
            byte[] endpointFingerprint,
            byte[] endpointCiphertext,
            byte[] p256dhCiphertext,
            byte[] authCiphertext,
            String userAgent,
            LocalDateTime expiresAt
    ) {
        return new PushSubscription(
                userId,
                endpointFingerprint,
                endpointCiphertext,
                p256dhCiphertext,
                authCiphertext,
                userAgent,
                expiresAt
        );
    }

    /**
     * 같은 endpoint를 다시 등록했을 때 키와 만료 시각을 최신 값으로 덮어쓴다.
     *
     * <p>브라우저가 구독을 갱신하면 endpoint는 같아도 p256dh와 auth가 바뀔 수 있다.
     * 비활성으로 내려 뒀던 구독도 사용자가 다시 등록했으므로 되살린다.
     */
    public void renew(
            byte[] endpointCiphertext,
            byte[] p256dhCiphertext,
            byte[] authCiphertext,
            String userAgent,
            LocalDateTime expiresAt
    ) {
        this.endpointCiphertext = endpointCiphertext;
        this.p256dhCiphertext = p256dhCiphertext;
        this.authCiphertext = authCiphertext;
        this.userAgent = userAgent;
        this.expiresAt = expiresAt;
        this.active = true;
    }

    /** Push 서비스가 만료를 알렸을 때만 쓴다. */
    public void deactivate() {
        this.active = false;
    }

    public void markSuccess(LocalDateTime sentAt) {
        this.lastSuccessAt = sentAt;
    }
}
