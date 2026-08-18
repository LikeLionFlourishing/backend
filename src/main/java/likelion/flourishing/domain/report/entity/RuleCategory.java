package likelion.flourishing.domain.report.entity;

/**
 * care_rules.category. 규칙이 어떤 이유로 걸리는지를 나타내고 충돌 처리 순서를 정한다.
 *
 * <p>관리규칙 v0.3의 9개 prefix에 하나씩 대응한다. CR=COMMON, ENV=ENVIRONMENT, SIT=SITUATION,
 * APP=APPEARANCE, ST=CURRENT_STATE, SAF=SAFETY, HR=HISTORY, ING=INGREDIENT,
 * FALLBACK=FALLBACK.
 *
 * <p>{@link #precedence()}가 작을수록 먼저 적용한다. 안전 규칙이 가장 앞이라 다른 규칙이
 * 같은 자리를 다투더라도 안전 안내를 밀어내지 못한다. 값을 10 단위로 띄운 것은 나중에 분류가
 * 늘어도 기존 규칙의 상대 순서를 건드리지 않고 사이에 끼울 수 있게 하려는 것이다.
 *
 * <p>{@link #FALLBACK}은 순서가 사실상 의미 없다. 규칙 문서가 폴백을 다른 규칙과 조합하지 않고
 * 단독 실행하도록 정했기 때문이다. 그래도 정렬이 끊기지 않게 맨 뒤 값을 준다.
 */
public enum RuleCategory {

    /** SAF-*. 위험 신호가 있으면 일반 관리보다 먼저 온다. */
    SAFETY(10, MatchReason.SAFETY),

    /** ENV-*. 온보딩에서 설정한 예상 환경으로 관리 행동을 보정한다. */
    ENVIRONMENT(20, MatchReason.ENVIRONMENT),

    /** ST-*. 세안 여부와 추가 관리 가능 여부. */
    CURRENT_STATE(30, MatchReason.CURRENT_STATE),

    /** SIT-*. 피부 불편이 발생한 상황. */
    SITUATION(40, MatchReason.SITUATION),

    /**
     * APP-*. 사용자가 관찰한 겉모습.
     *
     * <p>규칙 문서가 겉모습을 "행동을 만들지 않는 입력값"으로 정했다. 그래서 이 분류의 규칙에는
     * 행동 문구를 달지 않고, 어떤 겉모습이 결과에 영향을 줬는지 남기는 용도로만 쓴다.
     */
    APPEARANCE(50, MatchReason.APPEARANCE),

    /** HR-*. 과거 유사 기록. 현재 규칙보다 우선하지 않는다. */
    HISTORY(60, MatchReason.HISTORY),

    /** CR-*. 모든 보고에 붙는 공통 안내. */
    COMMON(70, MatchReason.COMMON),

    /** ING-*. 현재 피부 상태에 따른 성분 참고 정보. 기본 관리 행동과 별도 흐름이다. */
    INGREDIENT(80, MatchReason.INGREDIENT),

    /** FALLBACK-*. 어떤 상황 규칙에도 걸리지 않을 때 단독으로 실행한다. */
    FALLBACK(90, MatchReason.FALLBACK);

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
     * 대응하지 않는다. 금지 안내를 별도 분류로 뽑는 기준이 v0.3에도 없어서, 안전 규칙이 금지
     * 안내를 함께 담는다.
     */
    public MatchReason matchReason() {
        return matchReason;
    }
}
