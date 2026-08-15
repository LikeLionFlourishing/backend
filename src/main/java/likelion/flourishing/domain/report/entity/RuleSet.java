package likelion.flourishing.domain.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 함께 배포되는 관리 규칙 버전 묶음.
 *
 * <p>새 결과에는 status가 ACTIVE인 세트만 쓴다. DDL의 생성 컬럼과 유니크 제약 때문에 ACTIVE
 * 세트는 전역에 하나뿐이므로, 결과에 남긴 rule_set_id로 당시 기준을 정확히 되짚을 수 있다.
 *
 * <p>active_guard는 DB가 계산하는 컬럼이라 매핑하지 않는다.
 */
@Entity
@Getter
@Table(name = "rule_sets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RuleSet {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "version_code", nullable = false, length = 30)
    private String versionCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private RuleSetStatus status;
}
