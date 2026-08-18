package likelion.flourishing.domain.report.entity;

import java.util.Arrays;

/**
 * rule_conditions.field_code. 규칙이 들여다보는 구조화 값의 이름이다.
 *
 * <p>DDL의 CHECK가 API 필드 이름과 같은 lowerCamelCase 문자열만 허용하므로 enum 이름을 그대로
 * 저장할 수 없다. {@link RuleConditionFieldConverter}가 {@link #code()}로 변환한다.
 *
 * <p>completedHistory는 이 보고가 아니라 사용자의 완료된 과거 기록에서 뽑은 값이다.
 * environments는 보고가 아니라 온보딩에서 1회 설정하는 예상 환경이다.
 */
public enum RuleConditionField {

    PRIMARY_AREA("primaryArea"),
    APPEARANCES("appearances"),
    SENSATIONS("sensations"),
    SITUATIONS("situations"),
    CARE_AVAILABILITY("careAvailability"),
    PRE_CARE_CHECKS("preCareChecks"),
    COMPLETED_HISTORY("completedHistory"),
    ENVIRONMENTS("environments");

    private final String code;

    RuleConditionField(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static RuleConditionField fromCode(String code) {
        return Arrays.stream(values())
                .filter(field -> field.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("관리 규칙 조건 필드 이름을 알 수 없습니다."));
    }
}
