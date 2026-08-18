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
 * 버전이 바뀌어도 변하지 않는 규칙 식별자.
 *
 * <p>사용자에게 보이는 matchedRuleIds는 여기의 rule_code이고, 실제 문구와 조건은
 * {@link CareRuleVersion}에 버전별로 붙는다.
 */
@Entity
@Getter
@Table(name = "care_rules")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CareRule {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "rule_code", nullable = false, length = 20)
    private String ruleCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private RuleCategory category;

    @Column(name = "name", nullable = false, length = 120)
    private String name;
}
