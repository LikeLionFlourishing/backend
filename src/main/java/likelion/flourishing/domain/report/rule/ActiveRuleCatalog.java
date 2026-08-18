package likelion.flourishing.domain.report.rule;

import java.util.List;
import java.util.UUID;

/**
 * 지금 새 결과에 쓸 수 있는 규칙 전체.
 *
 * <p>ACTIVE 세트에 승인된 규칙 버전이 하나도 없으면 카탈로그를 만들지 않는다. 빈 카탈로그를
 * 넘기면 서비스가 "걸린 규칙이 없다"와 "쓸 규칙이 없다"를 구분할 수 없기 때문이다.
 *
 * @param versionCode 사용자에게 보여 주는 ruleVersion 값.
 */
public record ActiveRuleCatalog(UUID ruleSetId, String versionCode, List<CareRuleSnapshot> rules) {

    public ActiveRuleCatalog {
        rules = List.copyOf(rules);
    }
}
