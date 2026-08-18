package likelion.flourishing.domain.report.entity;

/**
 * rule_actions.action_type. 규칙이 허용하는 문구의 자리를 정한다.
 *
 * <p>{@link #FALLBACK}은 사용자에게 항목으로 보여 주는 값이 아니라 AI 설명 생성이 실패했을 때
 * 대신 쓰는 승인 문구다. 그래서 care_result_items의 item_type에는 대응 값이 없다.
 */
public enum RuleActionType {
    DO_TODAY,
    AVOID_TODAY,
    CHECK_NEXT,
    CLINICIAN_MESSAGE,
    FALLBACK;

    /** 사용자에게 표시하는 항목 유형. FALLBACK은 항목이 아니라 빈 값이다. */
    public CareResultItemType toItemType() {
        return switch (this) {
            case DO_TODAY -> CareResultItemType.DO_TODAY;
            case AVOID_TODAY -> CareResultItemType.AVOID_TODAY;
            case CHECK_NEXT -> CareResultItemType.CHECK_NEXT;
            case CLINICIAN_MESSAGE -> CareResultItemType.CLINICIAN_MESSAGE;
            case FALLBACK -> null;
        };
    }
}
