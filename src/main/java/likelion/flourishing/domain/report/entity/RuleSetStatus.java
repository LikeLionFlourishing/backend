package likelion.flourishing.domain.report.entity;

/**
 * rule_sets.status. 새 관리 결과에는 {@link #ACTIVE} 세트만 쓴다.
 *
 * <p>ACTIVE 세트는 DDL의 생성 컬럼과 유니크 제약으로 전역에 하나만 존재한다.
 */
public enum RuleSetStatus {
    REVIEW_REQUIRED,
    APPROVED,
    ACTIVE,
    RETIRED
}
