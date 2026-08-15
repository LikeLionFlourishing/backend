package likelion.flourishing.domain.report.rule;

import java.util.List;
import java.util.UUID;
import likelion.flourishing.domain.report.entity.MatchReason;
import likelion.flourishing.domain.report.entity.RuleCategory;

/**
 * 규칙 엔진이 다루는 승인된 규칙 버전 하나.
 *
 * <p>엔티티를 그대로 넘기지 않고 값 타입으로 옮겨 담는다. 엔진과 서비스가 영속 상태에 손대지
 * 못하게 해서 규칙 데이터가 요청 처리 중에 바뀌는 일을 막는다.
 *
 * @param fallbackText AI 설명 생성이 실패했을 때 대신 저장할 승인 문구.
 */
public record CareRuleSnapshot(
        UUID ruleVersionId,
        UUID ruleId,
        String ruleCode,
        RuleCategory category,
        int priority,
        String applicationSummary,
        String fallbackText,
        List<String> forbiddenExpressions,
        List<RuleConditionSpec> conditions,
        List<RuleActionSnapshot> actions
) {

    public CareRuleSnapshot {
        forbiddenExpressions = List.copyOf(forbiddenExpressions);
        conditions = List.copyOf(conditions);
        actions = List.copyOf(actions);
    }

    public MatchReason matchReason() {
        return category.matchReason();
    }
}
