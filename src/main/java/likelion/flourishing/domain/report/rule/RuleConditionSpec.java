package likelion.flourishing.domain.report.rule;

import likelion.flourishing.domain.report.entity.RuleConditionField;
import likelion.flourishing.domain.report.entity.RuleOperator;

/**
 * 규칙 조건 한 줄.
 *
 * <p>같은 conditionGroup끼리는 모두 만족해야 하고(AND), 그룹 사이에는 하나만 만족하면 된다(OR).
 *
 * @param valueCode CONTAINS_ANY와 NOT_CONTAINS_ANY에서는 쉼표로 구분한 여러 값이 들어온다.
 * @param negated   조건 결과를 뒤집는다. 연산자에 부정형이 없는 경우를 위한 장치다.
 */
public record RuleConditionSpec(
        int conditionGroup,
        RuleConditionField field,
        RuleOperator operator,
        String valueCode,
        boolean negated
) {
}
