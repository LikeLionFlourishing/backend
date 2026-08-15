package likelion.flourishing.domain.report.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import likelion.flourishing.domain.report.ai.CareGuideNarrationPort;
import likelion.flourishing.domain.report.ai.NarrationCommand;
import likelion.flourishing.domain.report.ai.NarrationOutcome;
import likelion.flourishing.domain.report.entity.AiGenerationStatus;
import likelion.flourishing.domain.report.entity.CareResult;
import likelion.flourishing.domain.report.entity.CareResultItem;
import likelion.flourishing.domain.report.entity.CareResultRule;
import likelion.flourishing.domain.report.entity.ResultType;
import likelion.flourishing.domain.report.entity.RuleActionType;
import likelion.flourishing.domain.report.repository.CareResultItemRepository;
import likelion.flourishing.domain.report.repository.CareResultRepository;
import likelion.flourishing.domain.report.repository.CareResultRuleRepository;
import likelion.flourishing.domain.report.rule.ActiveRuleCatalog;
import likelion.flourishing.domain.report.rule.CareActionAllowList;
import likelion.flourishing.domain.report.rule.CareRuleCatalogPort;
import likelion.flourishing.domain.report.rule.CareRuleEngine;
import likelion.flourishing.domain.report.rule.CareRuleSnapshot;
import likelion.flourishing.domain.report.rule.RuleEvaluationFacts;
import likelion.flourishing.domain.report.similarity.ScoredSimilarExperience;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 확정된 보고에 붙는 관리 결과를 만들어 저장한다.
 *
 * <p>결과를 만들 근거는 승인된 활성 규칙뿐이다. 규칙 세트가 없거나 걸리는 규칙이 없으면 결과를
 * 만들지 않고 {@link ErrorCode#RULE_ENGINE_UNAVAILABLE}을 던진다. 이 예외는 보고 저장까지 함께
 * 되돌려 결과 없는 보고가 남지 않게 한다.
 *
 * <p>AI는 문구를 고르고 요약을 쓰는 데만 쓴다. 실패하면 규칙이 정한 순서로 앞에서부터 채우고
 * 승인된 fallbackText를 요약으로 저장한다. 사용자에게는 결과가 나가고 상태만 FALLBACK이 된다.
 */
@Component
public class CareResultGenerator {

    private final CareRuleCatalogPort careRuleCatalogPort;
    private final CareRuleEngine careRuleEngine;
    private final CareGuideNarrationPort narrationPort;
    private final CareGuideItemPlanner itemPlanner;
    private final CareResultRepository careResultRepository;
    private final CareResultRuleRepository careResultRuleRepository;
    private final CareResultItemRepository careResultItemRepository;
    private final Clock clock;

    public CareResultGenerator(
            CareRuleCatalogPort careRuleCatalogPort,
            CareRuleEngine careRuleEngine,
            CareGuideNarrationPort narrationPort,
            CareGuideItemPlanner itemPlanner,
            CareResultRepository careResultRepository,
            CareResultRuleRepository careResultRuleRepository,
            CareResultItemRepository careResultItemRepository,
            Clock clock
    ) {
        this.careRuleCatalogPort = careRuleCatalogPort;
        this.careRuleEngine = careRuleEngine;
        this.narrationPort = narrationPort;
        this.itemPlanner = itemPlanner;
        this.careResultRepository = careResultRepository;
        this.careResultRuleRepository = careResultRuleRepository;
        this.careResultItemRepository = careResultItemRepository;
        this.clock = clock;
    }

    @Transactional
    public GeneratedCareResult generate(
            UUID reportId,
            UUID userId,
            ResultType resultType,
            RuleEvaluationFacts facts,
            ScoredSimilarExperience similarExperience
    ) {
        ActiveRuleCatalog catalog = careRuleCatalogPort.loadActiveCatalog()
                .orElseThrow(() -> new BusinessException(ErrorCode.RULE_ENGINE_UNAVAILABLE));

        List<CareRuleSnapshot> matchedRules = careRuleEngine.match(catalog, facts);
        if (matchedRules.isEmpty()) {
            // 걸리는 규칙이 없으면 안내할 근거가 없다. 빈 결과를 만들거나 문구를 지어내지 않는다.
            throw new BusinessException(ErrorCode.RULE_ENGINE_UNAVAILABLE);
        }

        CareActionAllowList allowList = CareActionAllowList.from(matchedRules);
        LocalDateTime generatedAt = LocalDateTime.now(clock);
        UUID similarReportId = similarExperience == null ? null : similarExperience.reportId();
        Integer similarityScore = similarExperience == null ? null : similarExperience.score();

        CareResultDraft draft = resultType == ResultType.CLINICIAN_CHECK
                ? draftClinicianCheck(allowList)
                : draftSelfCareGuide(allowList, facts);

        CareResult careResult = careResultRepository.saveAndFlush(resultType == ResultType.CLINICIAN_CHECK
                ? CareResult.clinicianCheck(
                        reportId,
                        userId,
                        catalog.ruleSetId(),
                        similarReportId,
                        similarityScore,
                        draft.summary(),
                        draft.clinicianMessage(),
                        generatedAt
                )
                : CareResult.selfCareGuide(
                        reportId,
                        userId,
                        catalog.ruleSetId(),
                        similarReportId,
                        similarityScore,
                        draft.aiGenerationStatus(),
                        draft.summary(),
                        generatedAt
                ));

        saveAppliedRules(careResult.getId(), matchedRules);
        saveItems(careResult.getId(), draft.items());
        return new GeneratedCareResult(careResult, catalog.versionCode(), matchedRules, draft.items());
    }

    /**
     * 일반 관리 안내를 만든다.
     *
     * <p>AI가 성공하면 고른 문구와 요약을 쓴다. 실패하면 규칙 순서대로 채우고 승인된 대체 문구를
     * 요약으로 쓴다. 대체 문구가 없으면 저장할 요약이 없어 결과를 만들 수 없으므로 503으로 돌린다.
     */
    private CareResultDraft draftSelfCareGuide(CareActionAllowList allowList, RuleEvaluationFacts facts) {
        NarrationOutcome narration = narrationPort.narrate(new NarrationCommand(
                facts.primaryArea(),
                facts.appearances(),
                facts.sensations(),
                facts.situations(),
                facts.careAvailability(),
                allowList.ruleSummaries(),
                allowList.contentsOf(RuleActionType.DO_TODAY),
                allowList.contentsOf(RuleActionType.AVOID_TODAY),
                allowList.contentsOf(RuleActionType.CHECK_NEXT),
                allowList.forbiddenExpressions(),
                CareGuideItemPlanner.MAX_ITEMS_PER_TYPE
        ));

        if (narration.isSucceeded()) {
            List<PlannedCareItem> items = itemPlanner.planFromNarration(allowList, narration);
            if (!items.isEmpty()) {
                return CareResultDraft.generated(narration.summary(), items);
            }
        }

        // 대체 경로에도 보여 줄 항목이 있어야 한다. 항목 없는 안내는 사용자가 할 수 있는 일이
        // 없다는 뜻이라 결과로 내보내지 않고 규칙이 준비되지 않은 것으로 다룬다.
        List<PlannedCareItem> fallbackItems = itemPlanner.planFromRules(allowList);
        if (fallbackItems.isEmpty()) {
            throw new BusinessException(ErrorCode.RULE_ENGINE_UNAVAILABLE);
        }
        return CareResultDraft.fallback(requireFallbackText(allowList), fallbackItems);
    }

    /**
     * 의료진 확인 안내를 만든다.
     *
     * <p>AI를 부르지 않는다. 병원에 가 보라는 안내는 문장을 다듬을 대상이 아니라 승인된 문구를
     * 그대로 전달해야 하는 내용이다. 그래서 상태가 NOT_APPLICABLE이고 재생성도 열리지 않는다.
     */
    private CareResultDraft draftClinicianCheck(CareActionAllowList allowList) {
        List<PlannedCareItem> clinicianMessage = itemPlanner.planClinicianMessage(allowList);
        if (clinicianMessage.isEmpty()) {
            throw new BusinessException(ErrorCode.RULE_ENGINE_UNAVAILABLE);
        }

        List<PlannedCareItem> items = new ArrayList<>(itemPlanner.planFromRules(allowList));
        items.addAll(clinicianMessage);
        return CareResultDraft.clinicianCheck(
                requireFallbackText(allowList),
                clinicianMessage.getFirst().content(),
                items
        );
    }

    private String requireFallbackText(CareActionAllowList allowList) {
        return Optional.ofNullable(allowList.fallbackText())
                .orElseThrow(() -> new BusinessException(ErrorCode.RULE_ENGINE_UNAVAILABLE));
    }

    private void saveAppliedRules(UUID careResultId, List<CareRuleSnapshot> matchedRules) {
        List<CareResultRule> appliedRules = new ArrayList<>();
        for (int index = 0; index < matchedRules.size(); index++) {
            CareRuleSnapshot rule = matchedRules.get(index);
            appliedRules.add(CareResultRule.of(
                    careResultId, rule.ruleVersionId(), index + 1, rule.matchReason()
            ));
        }
        careResultRuleRepository.saveAll(appliedRules);
    }

    private void saveItems(UUID careResultId, List<PlannedCareItem> items) {
        careResultItemRepository.saveAll(items.stream()
                .map(item -> CareResultItem.snapshot(
                        careResultId,
                        item.sourceRuleActionId(),
                        item.itemType(),
                        item.content(),
                        item.displayOrder()
                ))
                .toList());
    }

    /**
     * 저장 전 결과 본문.
     *
     * @param clinicianMessage 의료진 확인 결과에서만 값이 있다.
     */
    private record CareResultDraft(
            AiGenerationStatus aiGenerationStatus,
            String summary,
            String clinicianMessage,
            List<PlannedCareItem> items
    ) {

        private static CareResultDraft generated(String summary, List<PlannedCareItem> items) {
            return new CareResultDraft(AiGenerationStatus.GENERATED, summary, null, items);
        }

        private static CareResultDraft fallback(String summary, List<PlannedCareItem> items) {
            return new CareResultDraft(AiGenerationStatus.FALLBACK, summary, null, items);
        }

        private static CareResultDraft clinicianCheck(
                String summary,
                String clinicianMessage,
                List<PlannedCareItem> items
        ) {
            return new CareResultDraft(AiGenerationStatus.NOT_APPLICABLE, summary, clinicianMessage, items);
        }
    }
}
