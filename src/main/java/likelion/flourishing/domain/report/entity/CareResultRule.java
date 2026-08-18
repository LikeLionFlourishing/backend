package likelion.flourishing.domain.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
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

    private CareResultRule(CareResultRuleId id, int applicationOrder, MatchReason matchReason) {
        this.id = id;
        this.applicationOrder = applicationOrder;
        this.matchReason = matchReason;
    }

    /**
     * 결과에 규칙 버전을 적용 순서와 함께 붙인다.
     *
     * @param applicationOrder 1부터 시작하는 적용 순서. 결과 안에서 겹칠 수 없다.
     */
    public static CareResultRule of(
            UUID careResultId,
            UUID ruleVersionId,
            int applicationOrder,
            MatchReason matchReason
    ) {
        if (applicationOrder < 1) {
            throw new IllegalArgumentException("적용 순서는 1부터 시작합니다.");
        }
        return new CareResultRule(
                new CareResultRuleId(careResultId, ruleVersionId),
                applicationOrder,
                matchReason
        );
    }
}
