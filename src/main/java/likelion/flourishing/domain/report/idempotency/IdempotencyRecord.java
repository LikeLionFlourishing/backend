package likelion.flourishing.domain.report.idempotency;

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
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 같은 요청이 다시 들어왔을 때 되돌려 줄 응답.
 *
 * <p>(user_id, operation_id, idempotency_key) 조합이 유니크다. 키는 사용자와 작업 단위로만
 * 의미가 있어서, 다른 사용자가 우연히 같은 키를 써도 서로 영향을 주지 않는다.
 *
 * <p>requestHash는 본문을 정규화해 SHA-256으로 줄인 값이다. 같은 키에 다른 본문이 오면 덮어쓰기
 * 시도로 보아 409를 낸다. 본문 자체를 저장하지 않는 이유는 원문이 들어 있기 때문이다.
 *
 * <p>응답 본문은 암호화해 저장한다. 결과에 피부 상태와 관리 안내가 들어 있어 평문으로 두면
 * DB만 읽어도 사용자의 상태를 알 수 있다.
 *
 * <p>updated_at이 없는 테이블이라 BaseTimeEntity를 상속하지 않는다. 한 번 쓰고 고치지 않는다.
 */
@Entity
@Getter
@Table(name = "idempotency_records")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyRecord {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "operation_id", nullable = false, updatable = false, length = 80)
    private String operationId;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private UUID idempotencyKey;

    // 고정 길이 BINARY(32) 컬럼이라 JDBC 타입을 BINARY로 못 박는다.
    // 그렇지 않으면 ddl-auto = validate가 VARBINARY로 기대해 기동을 막는다.
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "request_hash", nullable = false, updatable = false, length = 32)
    private byte[] requestHash;

    @Column(name = "response_status", nullable = false, updatable = false)
    private short responseStatus;

    @Column(name = "response_body_encrypted", nullable = false, updatable = false)
    private byte[] responseBodyEncrypted;

    @Column(name = "resource_id", updatable = false)
    private UUID resourceId;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private IdempotencyRecord(
            UUID userId,
            String operationId,
            UUID idempotencyKey,
            byte[] requestHash,
            short responseStatus,
            byte[] responseBodyEncrypted,
            UUID resourceId,
            LocalDateTime expiresAt
    ) {
        this.id = UuidV7.generate();
        this.userId = userId;
        this.operationId = operationId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.responseStatus = responseStatus;
        this.responseBodyEncrypted = responseBodyEncrypted;
        this.resourceId = resourceId;
        this.expiresAt = expiresAt;
    }

    public static IdempotencyRecord of(
            UUID userId,
            String operationId,
            UUID idempotencyKey,
            byte[] requestHash,
            int responseStatus,
            byte[] responseBodyEncrypted,
            UUID resourceId,
            LocalDateTime expiresAt
    ) {
        return new IdempotencyRecord(
                userId,
                operationId,
                idempotencyKey,
                requestHash,
                (short) responseStatus,
                responseBodyEncrypted,
                resourceId,
                expiresAt
        );
    }

    public boolean isExpiredAt(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }
}
