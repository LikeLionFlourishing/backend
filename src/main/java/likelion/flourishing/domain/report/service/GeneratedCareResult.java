package likelion.flourishing.domain.report.service;

import java.util.List;
import likelion.flourishing.domain.report.entity.CareResult;
import likelion.flourishing.domain.report.rule.CareRuleSnapshot;

/**
 * 저장까지 끝난 관리 결과와 응답을 만들 재료.
 *
 * <p>응답을 만들려면 저장된 결과 말고도 어떤 규칙이 걸렸는지와 어떤 항목을 넣었는지가 필요하다.
 * 저장 직후 DB를 다시 읽지 않기 위해 만들 때 쓴 값을 그대로 들고 나온다.
 */
public record GeneratedCareResult(
        CareResult careResult,
        String ruleVersion,
        List<CareRuleSnapshot> appliedRules,
        List<PlannedCareItem> items,
        List<PlannedIngredient> ingredients
) {

    public GeneratedCareResult {
        appliedRules = List.copyOf(appliedRules);
        items = List.copyOf(items);
        ingredients = List.copyOf(ingredients);
    }
}
