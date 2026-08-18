package likelion.flourishing.domain.report.entity;

/**
 * care_result_rules.match_reason. 규칙이 왜 걸렸는지를 결과에 남긴다.
 *
 * <p>{@link RuleCategory}가 이 값을 하나씩 정해 준다. {@link #PROHIBITION}만 대응하는 분류가
 * 없다. 자세한 사정은 {@link RuleCategory#matchReason()}에 적어 두었다.
 */
public enum MatchReason {
    SAFETY,
    PROHIBITION,
    ENVIRONMENT,
    CURRENT_STATE,
    SITUATION,
    APPEARANCE,
    COMMON,
    HISTORY,
    INGREDIENT,
    FALLBACK
}
