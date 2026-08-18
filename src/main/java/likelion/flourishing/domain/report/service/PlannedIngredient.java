package likelion.flourishing.domain.report.service;

import java.util.List;
import java.util.UUID;

/**
 * 저장 전에 정해 둔 추천 성분 하나.
 *
 * @param sourceRuleCodes 이 성분을 내놓은 규칙 코드. 선언 순서가 곧 노출 순서다.
 * @param displayOrder 1부터 시작하며 명세 maxItems 3 을 넘지 않는다.
 */
public record PlannedIngredient(
        UUID ingredientId,
        String code,
        String name,
        String description,
        String cautionNote,
        List<String> sourceRuleCodes,
        int displayOrder
) {
    public PlannedIngredient {
        sourceRuleCodes = List.copyOf(sourceRuleCodes);
    }
}
