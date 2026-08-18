package likelion.flourishing.domain.report.entity;

/**
 * care_rule_versions.review_status. 새 관리 결과에는 {@link #APPROVED} 버전만 적용한다.
 *
 * <p>검토 전 규칙으로 사용자에게 관리 행동을 만들어 주지 않기 위한 구분이다.
 */
public enum RuleReviewStatus {
    REVIEW_REQUIRED,
    APPROVED,
    RETIRED
}
