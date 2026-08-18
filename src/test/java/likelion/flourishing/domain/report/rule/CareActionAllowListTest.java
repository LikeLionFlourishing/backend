package likelion.flourishing.domain.report.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import likelion.flourishing.domain.report.entity.RuleActionType;
import org.junit.jupiter.api.Test;

/**
 * 허용 문구 목록 테스트.
 *
 * <p>확인하는 것: 적용 순서가 문구 순서로 이어지는지, 같은 문구가 두 번 들어가지 않는지,
 * 대체 문구가 가장 앞선 규칙의 것인지, 목록에 없는 문구를 되찾지 못하는지.
 */
class CareActionAllowListTest {

    @Test
    void actionsFollowRuleApplicationOrder() {
        CareActionAllowList allowList = CareActionAllowList.from(List.of(
                CareRuleFixtures.safetyRule(),
                CareRuleFixtures.rednessRule(),
                CareRuleFixtures.commonRule()
        ));

        assertThat(allowList.contentsOf(RuleActionType.AVOID_TODAY))
                .containsExactly("짜거나 뜯지 않기", "각질 제거하지 않기", "손으로 만지지 않기");
        assertThat(allowList.contentsOf(RuleActionType.DO_TODAY))
                .containsExactly("찬 물수건으로 진정하기", "미지근한 물로 씻기");
    }

    @Test
    void duplicateContentAppearsOnce() {
        CareActionAllowList allowList = CareActionAllowList.from(List.of(
                CareRuleFixtures.commonRule(),
                CareRuleFixtures.commonRule()
        ));

        assertThat(allowList.contentsOf(RuleActionType.DO_TODAY)).containsExactly("미지근한 물로 씻기");
    }

    @Test
    void fallbackTextComesFromTheFirstRuleThatHasOne() {
        CareActionAllowList allowList = CareActionAllowList.from(List.of(
                CareRuleFixtures.safetyRule(),
                CareRuleFixtures.commonRule()
        ));

        assertThat(allowList.fallbackText())
                .isEqualTo("지금은 스스로 관리하기보다 의료진 확인이 필요한 상태로 보입니다.");
    }

    @Test
    void forbiddenExpressionsAreCollectedFromEveryRule() {
        CareActionAllowList allowList = CareActionAllowList.from(List.of(
                CareRuleFixtures.rednessRule(),
                CareRuleFixtures.commonRule()
        ));

        assertThat(allowList.forbiddenExpressions()).containsExactly("완치", "반드시 낫습니다");
    }

    @Test
    void topOfNeverReturnsMoreThanRequested() {
        CareActionAllowList allowList = CareActionAllowList.from(List.of(
                CareRuleFixtures.safetyRule(),
                CareRuleFixtures.rednessRule(),
                CareRuleFixtures.commonRule()
        ));

        assertThat(allowList.topOf(RuleActionType.AVOID_TODAY, 2))
                .extracting(RuleActionSnapshot::content)
                .containsExactly("짜거나 뜯지 않기", "각질 제거하지 않기");
    }

    @Test
    void contentOutsideTheAllowListCannotBeResolved() {
        CareActionAllowList allowList = CareActionAllowList.from(List.of(CareRuleFixtures.commonRule()));

        assertThat(allowList.findByContent(RuleActionType.DO_TODAY, "스테로이드 바르기")).isEmpty();
        assertThat(allowList.findByContent(RuleActionType.DO_TODAY, "미지근한 물로 씻기")).isPresent();
    }
}
