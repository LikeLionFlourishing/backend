package likelion.flourishing.domain.report.rule;

import java.util.UUID;

/**
 * 규칙이 권하는 성분 하나. 규칙 카탈로그를 읽을 때 함께 담긴다.
 *
 * <p>엔티티를 그대로 넘기지 않는 이유는 {@link CareRuleSnapshot}과 같다. 엔진과 서비스가 영속
 * 상태에 손대지 못하게 해서 규칙 데이터가 요청 처리 중에 바뀌는 일을 막는다.
 *
 * @param code 명세 RecommendedIngredient.id. 사람이 읽을 수 있는 규칙표 식별자다.
 */
public record IngredientSnapshot(
        UUID ingredientId,
        String code,
        String name,
        String description,
        String cautionNote,
        int displayOrder
) {
}
