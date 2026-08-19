package likelion.flourishing.domain.report.service;

import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import likelion.flourishing.domain.report.dto.response.GuideSectionResponse;
import likelion.flourishing.domain.report.dto.response.RecommendedIngredientResponse;
import likelion.flourishing.domain.report.entity.ResultType;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import likelion.flourishing.domain.record.dto.response.CareResultResponse;
import likelion.flourishing.domain.record.dto.response.SimilarExperienceResponse;
import likelion.flourishing.domain.report.entity.CareResult;
import likelion.flourishing.domain.report.entity.CareResultItemType;
import likelion.flourishing.domain.report.entity.MatchReason;
import likelion.flourishing.domain.report.rule.CareRuleSnapshot;
import org.springframework.stereotype.Component;

/**
 * 저장한 결과를 응답 모양으로 옮긴다.
 *
 * <p>보고 생성과 설명 재생성이 같은 모양을 돌려줘야 해서 한곳에 모았다. 기록 조회의 careResult와도
 * 필드가 같아 클라이언트는 세 경로에서 같은 타입을 쓴다.
 */
@Component
public class CareGuideResponseAssembler {

    private static final Logger log = LoggerFactory.getLogger(CareGuideResponseAssembler.class);

    private final GuideSectionAssembler guideSectionAssembler;

    public CareGuideResponseAssembler(GuideSectionAssembler guideSectionAssembler) {
        this.guideSectionAssembler = guideSectionAssembler;
    }

    public CareResultResponse assemble(
            CareResult careResult,
            String ruleVersion,
            List<CareRuleSnapshot> appliedRules,
            List<PlannedCareItem> items,
            List<PlannedIngredient> ingredients,
            SimilarExperienceResponse similarExperience
    ) {
        Map<CareResultItemType, List<String>> contentsByType = items.stream()
                .sorted((left, right) -> Integer.compare(left.displayOrder(), right.displayOrder()))
                .collect(Collectors.groupingBy(
                        PlannedCareItem::itemType,
                        Collectors.mapping(PlannedCareItem::content, Collectors.toList())
                ));

        LinkedHashSet<String> ruleCodes = appliedRules.stream()
                .map(CareRuleSnapshot::ruleCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        LinkedHashSet<MatchReason> reasonTags = appliedRules.stream()
                .map(CareRuleSnapshot::matchReason)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<String> doToday = contentsByType.getOrDefault(CareResultItemType.DO_TODAY, List.of());
        List<String> avoidToday = contentsByType.getOrDefault(CareResultItemType.AVOID_TODAY, List.of());
        List<String> checkNext = contentsByType.getOrDefault(CareResultItemType.CHECK_NEXT, List.of());

        // 명세가 CLINICIAN_CHECK 에 guideSections 와 recommendedIngredients 모두 maxItems 0 을
        // 걸어 두었다. 병원에 가 보라는 안내에 성분을 함께 주면 자가 처치를 권하는 것으로 읽히고,
        // 섹션으로 나누는 화면도 아니다.
        //
        // 결과를 정하는 쪽(CareResultGenerator)이 이미 빈 목록을 넘기지만 여기서도 막는다.
        // 기록 조회처럼 저장된 값을 다시 읽어 오는 경로가 늘어나도 계약이 깨지지 않게 하려는 것이다.
        boolean clinicianCheck = careResult.getResultType() == ResultType.CLINICIAN_CHECK;

        List<RecommendedIngredientResponse> recommendedIngredients =
                clinicianCheck ? List.of() : toIngredientResponses(ingredients, ruleCodes);

        List<GuideSectionResponse> guideSections =
                clinicianCheck
                        ? List.of()
                        : guideSectionAssembler.assemble(new GuideSectionAssembler.GuideSectionContent(
                                careResult.getSummary(),
                                doToday,
                                avoidToday,
                                checkNext,
                                recommendedIngredients,
                                similarExperience != null
                        ));

        return CareResultResponse.of(
                careResult.getResultType(),
                List.copyOf(ruleCodes),
                guideSections,
                ruleVersion,
                careResult.getSummary(),
                doToday,
                avoidToday,
                checkNext,
                recommendedIngredients,
                List.copyOf(reasonTags),
                careResult.getClinicianMessage(),
                similarExperience,
                careResult.getAiGenerationStatus(),
                careResult.getGeneratedAt().atOffset(ZoneOffset.UTC),
                careResult.isRetryUsed()
        );
    }

    /**
     * 명세는 sourceRuleIds 가 matchedRuleIds 의 부분집합이어야 한다고 정한다.
     *
     * <p>지금 경로에서는 성분이 걸린 규칙에서만 나오므로 어긋날 일이 없다. 그래도 확인하는 이유는
     * 규칙 데이터가 바뀌어 스냅샷과 어긋나는 경우를 조용히 넘기지 않기 위해서다. 벗어난 코드는
     * 빼고, 근거가 하나도 남지 않은 성분은 응답에서 제외한다. 근거 없는 성분 추천은 내보내지 않는다.
     */
    private List<RecommendedIngredientResponse> toIngredientResponses(
            List<PlannedIngredient> ingredients,
            Set<String> matchedRuleCodes
    ) {
        List<RecommendedIngredientResponse> responses = new ArrayList<>();
        for (PlannedIngredient ingredient : ingredients) {
            List<String> sourceRuleIds = ingredient.sourceRuleCodes().stream()
                    .filter(matchedRuleCodes::contains)
                    .toList();
            if (sourceRuleIds.size() != ingredient.sourceRuleCodes().size()) {
                log.warn(
                        "추천 성분의 근거 규칙이 적용 규칙 밖을 가리킵니다. ingredient={} 근거={} 적용={}",
                        ingredient.code(),
                        ingredient.sourceRuleCodes(),
                        matchedRuleCodes
                );
            }
            if (sourceRuleIds.isEmpty()) {
                continue;
            }
            responses.add(RecommendedIngredientResponse.of(
                    ingredient.code(),
                    ingredient.name(),
                    ingredient.description(),
                    ingredient.cautionNote(),
                    sourceRuleIds
            ));
        }
        return List.copyOf(responses);
    }
}
