package likelion.flourishing.domain.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 관리 결과에 실제 적용된 규칙 버전과 적용 순서의 불변 스냅샷 연결. */
@Entity
@Getter
@Table(name = "care_result_rules")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CareResultRule {

    @EmbeddedId
    private CareResultRuleId id;

    @Column(name = "application_order", nullable = false)
    private int applicationOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_reason", nullable = false, length = 30)
    private MatchReason matchReason;
}
