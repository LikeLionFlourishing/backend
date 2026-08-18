package likelion.flourishing.domain.report.service;

import java.util.List;
import java.util.UUID;
import likelion.flourishing.domain.report.entity.AiGenerationStatus;
import likelion.flourishing.domain.report.entity.ResultType;
import likelion.flourishing.domain.report.rule.CareRuleSnapshot;

/**
 * 저장 전에 다 정해 둔 관리 결과.
 *
 * <p>규칙 조회와 AI 호출은 여기까지에서 끝난다. 외부 호출이 쓰기 트랜잭션 안에 들어가면 보고
 * 유니크 인덱스 락과 DB 커넥션을 응답이 올 때까지 붙잡게 되므로, 결정을 먼저 끝내고 저장만
 * 짧은 트랜잭션에서 한다.
 *
 * @param clinicianMessage 의료진 확인 결과에서만 값이 있다.
 * @param ingredients 추천 성분. 명세가 CLINICIAN_CHECK 에는 maxItems 0 을 걸어 두어 그때는 비어 있다.
 */
public record CareResultPlan(
        UUID ruleSetId,
        String ruleVersion,
        ResultType resultType,
        AiGenerationStatus aiGenerationStatus,
        String summary,
        String clinicianMessage,
        List<CareRuleSnapshot> matchedRules,
        List<PlannedCareItem> items,
        List<PlannedIngredient> ingredients
) {

    public CareResultPlan {
        matchedRules = List.copyOf(matchedRules);
        items = List.copyOf(items);
        ingredients = List.copyOf(ingredients);
    }
}
