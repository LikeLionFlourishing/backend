package likelion.flourishing.domain.report.rule;

import java.util.List;

/**
 * 이미 만들어진 결과에 적용된 규칙 묶음.
 *
 * <p>활성 카탈로그와 달리 은퇴한 규칙 버전도 들어 있다. 결과가 참조하는 근거는 당시 상태 그대로
 * 남아 있어야 하기 때문이다.
 *
 * @param rules 적용 순서대로 정렬된 규칙.
 */
public record AppliedRuleSet(String versionCode, List<CareRuleSnapshot> rules) {

    public AppliedRuleSet {
        rules = List.copyOf(rules);
    }
}
