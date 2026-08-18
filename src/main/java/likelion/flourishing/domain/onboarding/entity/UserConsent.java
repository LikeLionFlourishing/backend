package likelion.flourishing.domain.onboarding.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import likelion.flourishing.support.UuidV7;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 버전별 필수 동의 이력.
 *
 * <p>동의를 철회하거나 수정하지 않고 버전마다 한 행을 남긴다. DDL의 CHECK가 accepted = TRUE만
 * 허용하므로 거절은 저장 대상이 아니라 요청 검증에서 막는다.
 *
 * <p>테이블에 updated_at이 없으므로 BaseTimeEntity를 상속하지 않는다.
 */
@Entity
@Getter
@Table(name = "user_consents")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserConsent {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "consent_version", nullable = false, length = 50)
    private String consentVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", nullable = false, length = 40)
    private ConsentType consentType;

    @Column(name = "accepted", nullable = false)
    private boolean accepted;

    @Column(name = "consented_at", nullable = false)
    private LocalDateTime consentedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private UserConsent(UUID id, UUID userId, ConsentType consentType, String consentVersion, LocalDateTime consentedAt) {
        this.id = id;
        this.userId = userId;
        this.consentType = consentType;
        this.consentVersion = consentVersion;
        this.accepted = true;
        this.consentedAt = consentedAt;
    }

    public static UserConsent accept(
            UUID userId,
            ConsentType consentType,
            String consentVersion,
            LocalDateTime consentedAt
    ) {
        return new UserConsent(UuidV7.generate(), userId, consentType, consentVersion, consentedAt);
    }
}
