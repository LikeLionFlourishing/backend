package likelion.flourishing.domain.report.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import likelion.flourishing.domain.report.rule.CareRuleSnapshot;
import likelion.flourishing.domain.report.rule.IngredientSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 걸린 규칙이 가리키는 성분 중 결과에 담을 것을 고른다.
 *
 * <p>AI를 부르지 않는다. 성분은 관리 규칙표에서 조회한 값만 쓰고 새로 만들지 않는다는 것이
 * 명세가 못 박은 제약이라, 문구를 고르는 단계와 분리해 둔다.
 *
 * <p>같은 성분을 여러 규칙이 권하면 하나로 접고 그 규칙들을 모두 sourceRuleIds 에 담는다.
 * 순서는 규칙이 걸린 순서(= 우선순위)를 따르고, 같은 규칙 안에서는 규칙표가 정한 display_order 다.
 */
@Component
public class RecommendedIngredientPlanner {

    private static final Logger log = LoggerFactory.getLogger(RecommendedIngredientPlanner.class);

    /**
     * 한 결과에 담을 성분 개수 상한.
     *
     * <p>관리규칙 v0.3의 ING 공통 호출 조건이 "최대 2개까지 출력"으로 정했다. 명세
     * CareResult.recommendedIngredients.maxItems는 3이라 둘을 함께 만족한다. 규칙 쪽 상한이 더
     * 좁은 이유는 참고 성분을 늘리는 것이 제품 추천처럼 읽히기 때문이다.
     */
    public static final int MAX_INGREDIENTS = 2;

    /**
     * 걸린 규칙 순서대로 성분을 모아 상한까지 고른다.
     *
     * <p>규칙에 성분이 없으면 빈 목록이다. 명세가 "규칙에 해당 성분이 없으면 빈 배열"이라고
     * 정하고 있어 오류가 아니다. 성분을 내보내지 않아야 하는 조건(새 제품 사용 후, 겉모습이
     * 기타 단독, 의료진 확인, 폴백 단독)은 규칙 조건과 호출부에서 이미 걸러진다.
     */
    public List<PlannedIngredient> plan(List<CareRuleSnapshot> matchedRules) {
        Map<String, Candidate> byCode = new LinkedHashMap<>();

        for (CareRuleSnapshot rule : matchedRules) {
            for (IngredientSnapshot ingredient : sortedByRuleOrder(rule.ingredients())) {
                byCode.computeIfAbsent(ingredient.code(), code -> new Candidate(ingredient))
                        .addSourceRule(rule.ruleCode());
            }
        }

        List<PlannedIngredient> planned = new ArrayList<>();
        for (Candidate candidate : byCode.values()) {
            if (planned.size() == MAX_INGREDIENTS) {
                // 상한을 넘으면 뒤엣것을 버린다. 규칙 우선순위가 앞선 성분이 남는다.
                log.debug(
                        "추천 성분이 상한을 넘어 일부를 제외했습니다. 상한={} 후보={}",
                        MAX_INGREDIENTS,
                        byCode.size()
                );
                break;
            }
            planned.add(candidate.toPlanned(planned.size() + 1));
        }
        return List.copyOf(planned);
    }

    private List<IngredientSnapshot> sortedByRuleOrder(List<IngredientSnapshot> ingredients) {
        return ingredients.stream()
                .sorted((left, right) -> Integer.compare(left.displayOrder(), right.displayOrder()))
                .toList();
    }

    /** 같은 성분을 권한 규칙을 모으는 중간 값. 규칙 코드는 중복 없이 처음 순서를 지킨다. */
    private static final class Candidate {

        private final IngredientSnapshot ingredient;
        private final LinkedHashSet<String> sourceRuleCodes = new LinkedHashSet<>();

        private Candidate(IngredientSnapshot ingredient) {
            this.ingredient = ingredient;
        }

        private void addSourceRule(String ruleCode) {
            sourceRuleCodes.add(ruleCode);
        }

        private PlannedIngredient toPlanned(int displayOrder) {
            return new PlannedIngredient(
                    ingredient.ingredientId(),
                    ingredient.code(),
                    ingredient.name(),
                    ingredient.description(),
                    ingredient.cautionNote(),
                    List.copyOf(sourceRuleCodes),
                    displayOrder
            );
        }
    }
}
