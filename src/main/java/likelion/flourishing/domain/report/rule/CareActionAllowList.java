package likelion.flourishing.domain.report.rule;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import likelion.flourishing.domain.report.entity.RuleActionType;

/**
 * 걸린 규칙이 허용한 문구를 유형별로 모아 둔 목록.
 *
 * <p>순서가 곧 우선순위다. 규칙 적용 순서대로 넣고 같은 문구는 앞의 것만 남긴다. 여러 규칙이
 * 같은 안내를 담고 있어도 사용자에게는 한 번만 보여야 한다.
 *
 * <p>이 목록이 결과에 들어갈 수 있는 문구의 전부다. AI가 고르든 실패해서 앞에서부터 채우든,
 * 여기 없는 문장은 어떤 경로로도 결과에 들어가지 않는다.
 */
public final class CareActionAllowList {

    private final Map<RuleActionType, List<RuleActionSnapshot>> actionsByType;
    private final List<String> ruleSummaries;
    private final List<String> forbiddenExpressions;
    private final String fallbackText;

    private CareActionAllowList(
            Map<RuleActionType, List<RuleActionSnapshot>> actionsByType,
            List<String> ruleSummaries,
            List<String> forbiddenExpressions,
            String fallbackText
    ) {
        this.actionsByType = actionsByType;
        this.ruleSummaries = ruleSummaries;
        this.forbiddenExpressions = forbiddenExpressions;
        this.fallbackText = fallbackText;
    }

    /**
     * 적용 순서대로 정렬된 규칙에서 목록을 만든다.
     *
     * <p>fallbackText는 가장 앞선 규칙의 것을 쓴다. 안전 규칙이 걸렸다면 그 문구가 먼저 오므로
     * 설명 생성이 실패해도 안전 안내가 요약 자리를 차지한다.
     */
    public static CareActionAllowList from(List<CareRuleSnapshot> matchedRules) {
        Map<RuleActionType, List<RuleActionSnapshot>> actionsByType = new EnumMap<>(RuleActionType.class);
        Map<RuleActionType, Set<String>> seenContents = new EnumMap<>(RuleActionType.class);
        List<String> ruleSummaries = new ArrayList<>();
        Set<String> forbiddenExpressions = new LinkedHashSet<>();
        String fallbackText = null;

        for (CareRuleSnapshot rule : matchedRules) {
            ruleSummaries.add(rule.applicationSummary());
            forbiddenExpressions.addAll(rule.forbiddenExpressions());
            if (fallbackText == null && rule.fallbackText() != null && !rule.fallbackText().isBlank()) {
                fallbackText = rule.fallbackText().trim();
            }
            for (RuleActionSnapshot action : rule.actions()) {
                Set<String> seen = seenContents.computeIfAbsent(action.type(), type -> new LinkedHashSet<>());
                if (seen.add(action.content())) {
                    actionsByType.computeIfAbsent(action.type(), type -> new ArrayList<>()).add(action);
                }
            }
        }

        return new CareActionAllowList(
                actionsByType,
                List.copyOf(ruleSummaries),
                List.copyOf(forbiddenExpressions),
                fallbackText
        );
    }

    public List<RuleActionSnapshot> actionsOf(RuleActionType type) {
        return List.copyOf(actionsByType.getOrDefault(type, List.of()));
    }

    public List<String> contentsOf(RuleActionType type) {
        return actionsOf(type).stream().map(RuleActionSnapshot::content).toList();
    }

    /** 설명 생성이 실패했을 때 앞에서부터 정해진 개수만 채운다. */
    public List<RuleActionSnapshot> topOf(RuleActionType type, int limit) {
        List<RuleActionSnapshot> actions = actionsOf(type);
        return actions.subList(0, Math.min(limit, actions.size()));
    }

    /** 문구로 원래 규칙 행동을 되찾는다. 결과 항목에 출처를 남기기 위해서다. */
    public Optional<RuleActionSnapshot> findByContent(RuleActionType type, String content) {
        return actionsOf(type).stream().filter(action -> action.content().equals(content)).findFirst();
    }

    public List<String> ruleSummaries() {
        return ruleSummaries;
    }

    public List<String> forbiddenExpressions() {
        return forbiddenExpressions;
    }

    /** 승인된 대체 문구. 없으면 null이고, 호출한 쪽이 결과를 만들지 않고 503으로 답한다. */
    public String fallbackText() {
        return fallbackText;
    }
}
