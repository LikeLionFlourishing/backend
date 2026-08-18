package likelion.flourishing.domain.report.service;

import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import likelion.flourishing.domain.report.dto.response.CareGuideResponse;
import likelion.flourishing.domain.report.dto.response.SimilarExperienceSummaryResponse;
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

    public CareGuideResponse assemble(
            CareResult careResult,
            String ruleVersion,
            List<CareRuleSnapshot> appliedRules,
            List<PlannedCareItem> items,
            SimilarExperienceSummaryResponse similarExperience
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

        return CareGuideResponse.of(
                careResult.getResultType(),
                List.copyOf(ruleCodes),
                ruleVersion,
                careResult.getSummary(),
                contentsByType.getOrDefault(CareResultItemType.DO_TODAY, List.of()),
                contentsByType.getOrDefault(CareResultItemType.AVOID_TODAY, List.of()),
                contentsByType.getOrDefault(CareResultItemType.CHECK_NEXT, List.of()),
                List.copyOf(reasonTags),
                careResult.getClinicianMessage(),
                similarExperience,
                careResult.getAiGenerationStatus(),
                careResult.getGeneratedAt().atOffset(ZoneOffset.UTC),
                careResult.isRetryUsed()
        );
    }
}
