package likelion.flourishing.domain.report.rule;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 확정된 선택값에 걸리는 규칙을 고르고 적용 순서를 정한다.
 *
 * <p>순서는 안전 → 현재 상태 → 직전 상황 → 과거 기록 → 공통이다. 같은 카테고리 안에서는 규칙에
 * 적힌 priority가 작은 것이 앞이고, 그마저 같으면 규칙 코드 순이다. 같은 입력에 항상 같은 순서가
 * 나와야 결과의 application_order가 재현 가능해진다.
 *
 * <p>조건이 하나도 없는 규칙은 항상 걸린다. 모든 보고에 붙는 공통 안내가 그런 규칙이다.
 */
@Component
public class CareRuleEngine {

    private static final String VALUE_DELIMITER = ",";

    private static final Comparator<CareRuleSnapshot> APPLICATION_ORDER = Comparator
            .comparingInt((CareRuleSnapshot rule) -> rule.category().precedence())
            .thenComparingInt(CareRuleSnapshot::priority)
            .thenComparing(CareRuleSnapshot::ruleCode);

    /** 걸린 규칙을 적용 순서대로 돌려준다. 하나도 걸리지 않으면 빈 목록이다. */
    public List<CareRuleSnapshot> match(ActiveRuleCatalog catalog, RuleEvaluationFacts facts) {
        return catalog.rules().stream()
                .filter(rule -> matches(rule, facts))
                .sorted(APPLICATION_ORDER)
                .toList();
    }

    private boolean matches(CareRuleSnapshot rule, RuleEvaluationFacts facts) {
        if (rule.conditions().isEmpty()) {
            return true;
        }
        Map<Integer, List<RuleConditionSpec>> groups = rule.conditions().stream()
                .collect(Collectors.groupingBy(RuleConditionSpec::conditionGroup));
        return groups.values().stream()
                .anyMatch(group -> group.stream().allMatch(condition -> evaluate(condition, facts)));
    }

    private boolean evaluate(RuleConditionSpec condition, RuleEvaluationFacts facts) {
        Set<String> actual = facts.valuesOf(condition.field());
        boolean result = switch (condition.operator()) {
            case EQUALS -> isExactly(actual, condition.valueCode());
            case NOT_EQUALS -> !isExactly(actual, condition.valueCode());
            case CONTAINS -> actual.contains(condition.valueCode().trim());
            case CONTAINS_ANY -> containsAny(actual, condition.valueCode());
            case NOT_CONTAINS_ANY -> !containsAny(actual, condition.valueCode());
            case EXISTS -> !actual.isEmpty();
        };
        return condition.negated() != result;
    }

    /**
     * 값이 정확히 그 하나인지.
     *
     * <p>단일 값 필드에서는 같은지 보는 것과 같고, 다중 선택 필드에서는 "그것만 골랐는지"가 된다.
     * 불편이 NONE 하나뿐인 경우처럼 배타 선택을 조건으로 쓸 수 있다.
     */
    private boolean isExactly(Set<String> actual, String valueCode) {
        return actual.size() == 1 && actual.contains(valueCode.trim());
    }

    private boolean containsAny(Set<String> actual, String valueCode) {
        Set<String> candidates = Arrays.stream(valueCode.split(VALUE_DELIMITER))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return candidates.stream().anyMatch(actual::contains);
    }
}
