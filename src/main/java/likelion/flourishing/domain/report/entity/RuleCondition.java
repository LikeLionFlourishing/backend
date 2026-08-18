package likelion.flourishing.domain.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
 * 규칙 엔진이 평가하는 한 줄짜리 조건.
 *
 * <p>같은 condition_group 안의 조건은 모두 만족해야 하고(AND), 그룹이 여러 개면 그중 하나만
 * 만족해도 규칙이 걸린다(OR). 사람이 규칙표에 쓰는 "A이고 B인 경우 또는 C인 경우"를 그대로
 * 옮기기 위한 구조다.
 *
 * <p>애플리케이션은 규칙을 읽기만 한다. 규칙 데이터 적재는 승인된 관리 규칙 최종본을
 * 초기 데이터로 넣는 별도 작업이다.
 */
@Entity
@Getter
@Table(name = "rule_conditions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RuleCondition {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "rule_version_id", nullable = false, updatable = false)
    private UUID ruleVersionId;

    @Column(name = "condition_group", nullable = false)
    private int conditionGroup;

    @Convert(converter = RuleConditionFieldConverter.class)
    @Column(name = "field_code", nullable = false, length = 40)
    private RuleConditionField fieldCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "operator_code", nullable = false, length = 30)
    private RuleOperator operatorCode;

    @Column(name = "value_code", nullable = false, length = 100)
    private String valueCode;

    @Column(name = "negated", nullable = false)
    private boolean negated;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
