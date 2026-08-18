package likelion.flourishing.domain.report.entity;

/**
 * rule_conditions.operator_code. 규칙 조건이 구조화 값을 어떻게 비교하는지 정한다.
 *
 * <p>단일 값 필드(primaryArea, careAvailability)에는 EQUALS와 NOT_EQUALS를,
 * 다중 선택 필드에는 CONTAINS 계열을 쓴다. EXISTS는 값이 하나라도 있는지만 본다.
 */
public enum RuleOperator {
    EQUALS,
    NOT_EQUALS,
    CONTAINS,
    CONTAINS_ANY,
    NOT_CONTAINS_ANY,
    EXISTS
}
