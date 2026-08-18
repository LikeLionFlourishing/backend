package likelion.flourishing.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import likelion.flourishing.domain.report.entity.MatchReason;
import likelion.flourishing.domain.report.entity.RuleCategory;
import likelion.flourishing.domain.report.rule.CareRuleSnapshot;
import likelion.flourishing.domain.report.rule.IngredientSnapshot;
import org.junit.jupiter.api.Test;

/**
 * 성분 선정은 규칙표만 근거로 삼는다. 여기서 확인하는 것은 어떤 성분이 어떤 순서로 남고,
 * 근거 규칙이 어떻게 모이는지다.
 */
class RecommendedIngredientPlannerTest {

    private final RecommendedIngredientPlanner planner = new RecommendedIngredientPlanner();

    @Test
    void ruleWithoutIngredientsYieldsEmptyList() {
        // 명세가 "규칙에 해당 성분이 없으면 빈 배열"이라고 정한다. 오류가 아니다.
        assertThat(planner.plan(List.of(rule("GEN-001")))).isEmpty();
    }

    @Test
    void sameIngredientFromTwoRulesCollapsesAndKeepsBothSourceRules() {
        List<PlannedIngredient> planned = planner.plan(List.of(
                rule("GEN-001", ingredient("ING_PANTHENOL", "판테놀", 1)),
                rule("RED-002", ingredient("ING_PANTHENOL", "판테놀", 1))
        ));

        assertThat(planned).hasSize(1);
        assertThat(planned.getFirst().code()).isEqualTo("ING_PANTHENOL");
        assertThat(planned.getFirst().sourceRuleCodes()).containsExactly("GEN-001", "RED-002");
        assertThat(planned.getFirst().displayOrder()).isEqualTo(1);
    }

    @Test
    void ingredientsFollowRuleOrderThenRuleTableOrder() {
        List<PlannedIngredient> planned = planner.plan(List.of(
                rule("GEN-001", ingredient("ING_B", "비", 2), ingredient("ING_A", "에이", 1)),
                rule("RED-002", ingredient("ING_C", "씨", 1))
        ));

        // 첫 규칙 안에서는 규칙표 display_order 를 따르고, 규칙끼리는 걸린 순서를 따른다.
        assertThat(planned).extracting(PlannedIngredient::code)
                .containsExactly("ING_A", "ING_B", "ING_C");
        assertThat(planned).extracting(PlannedIngredient::displayOrder)
                .containsExactly(1, 2, 3);
    }

    @Test
    void keepsAtMostThreeIngredients() {
        List<PlannedIngredient> planned = planner.plan(List.of(rule(
                "GEN-001",
                ingredient("ING_A", "에이", 1),
                ingredient("ING_B", "비", 2),
                ingredient("ING_C", "씨", 3),
                ingredient("ING_D", "디", 4)
        )));

        // 명세 maxItems 3. 우선순위가 앞선 성분이 남는다.
        assertThat(planned).hasSize(RecommendedIngredientPlanner.MAX_INGREDIENTS);
        assertThat(planned).extracting(PlannedIngredient::code).containsExactly("ING_A", "ING_B", "ING_C");
    }

    private CareRuleSnapshot rule(String ruleCode, IngredientSnapshot... ingredients) {
        return new CareRuleSnapshot(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ruleCode,
                RuleCategory.COMMON,
                500,
                "적용 요약",
                "대체 문구",
                List.of(),
                List.of(),
                List.of(),
                List.of(ingredients)
        );
    }

    private IngredientSnapshot ingredient(String code, String name, int displayOrder) {
        return new IngredientSnapshot(UUID.randomUUID(), code, name, "설명", null, displayOrder);
    }

    @Test
    void matchReasonIsUnaffected() {
        assertThat(rule("GEN-001").matchReason()).isEqualTo(MatchReason.COMMON);
    }
}
