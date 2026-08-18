package likelion.flourishing.domain.report.rule;

import java.util.UUID;
import likelion.flourishing.domain.report.entity.RuleActionType;

/**
 * 규칙이 허용한 문구 하나.
 *
 * @param actionId 결과 항목에 source_rule_action_id로 남긴다. 나중에 어떤 문구에서 왔는지 되짚는다.
 */
public record RuleActionSnapshot(
        UUID actionId,
        RuleActionType type,
        String content,
        int priority,
        int displayOrder
) {
}
