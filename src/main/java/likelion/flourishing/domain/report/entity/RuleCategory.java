package likelion.flourishing.domain.report.entity;

/**
 * care_rules.category. 규칙이 어떤 이유로 걸리는지를 나타내고 충돌 처리 순서를 정한다.
 *
 * <p>{@link #precedence()}가 작을수록 먼저 적용한다. 안전 규칙이 가장 앞이라 다른 규칙이
 * 같은 자리를 다투더라도 안전 안내를 밀어내지 못한다.
 */
public enum RuleCategory {

    SAFETY(1, MatchReason.SAFETY),
    CURRENT_STATE(2, MatchReason.CURRENT_STATE),
    SITUATION(3, MatchReason.SITUATION),
    HISTORY(4, MatchReason.HISTORY),
    COMMON(5, MatchReason.COMMON);

    private final int precedence;
    private final MatchReason matchReason;

    RuleCategory(int precedence, MatchReason matchReason) {
        this.precedence = precedence;
        this.matchReason = matchReason;
    }

    public int precedence() {
        return precedence;
    }

    /**
     * 결과에 남길 적용 이유.
     *
     * <p>{@link MatchReason#PROHIBITION}은 DDL이 허용하는 값이지만 어떤 카테고리도 여기에
     * 대응하지 않는다. 금지 규칙을 별도 분류로 뽑는 기준은 전문가 검토가 끝난 관리 규칙
     * 최종본에서 정해지므로, 그때까지는 안전 규칙이 금지 안내를 함께 담는다.
     */
    public MatchReason matchReason() {
        return matchReason;
    }
}
