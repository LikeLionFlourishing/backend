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
import likelion.flourishing.domain.report.entity.RuleCategory;
import likelion.flourishing.domain.report.entity.CareResultIngredient;
import likelion.flourishing.domain.report.entity.CareResultIngredientRule;
import likelion.flourishing.domain.report.repository.CareResultIngredientRepository;
import likelion.flourishing.domain.report.repository.CareResultIngredientRuleRepository;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 확정된 보고에 붙는 관리 결과를 정하고 저장한다.
 *
 * <p>정하는 단계({@link #plan})와 저장하는 단계({@link #persist})를 나눈다. 규칙 조회와 AI 호출은
 * 정하는 단계에서 끝나고, 쓰기 트랜잭션에는 저장만 남는다. 외부 호출을 트랜잭션 안에 두면 보고
 * 유니크 인덱스 락과 DB 커넥션을 응답이 올 때까지 붙잡게 된다.
 *
 * <p>결과를 만들 근거는 승인된 활성 규칙뿐이다. 규칙 세트가 없거나 걸리는 규칙이 없으면
 * {@link ErrorCode#RULE_ENGINE_UNAVAILABLE}을 던진다. 정하는 단계에서 던지므로 아무것도 저장되지
 * 않는다.
 *
 * <p>AI는 문구를 고르고 요약을 쓰는 데만 쓴다. 실패하면 규칙이 정한 순서로 앞에서부터 채우고
 * 승인된 fallbackText를 요약으로 쓴다. 사용자에게는 결과가 나가고 상태만 FALLBACK이 된다.
 *
 * <p>상황 규칙이 하나도 걸리지 않으면 폴백 규칙만 단독으로 쓴다. 이때는 AI를 부르지 않고 성분도
 * 내보내지 않는다. 자세한 이유는 {@link #planFallbackOnly}에 적어 두었다.
 */
@Component
public class CareResultGenerator {

    private static final Logger log = LoggerFactory.getLogger(CareResultGenerator.class);

    /** care_results.summary 컬럼 길이. */
    private static final int SUMMARY_MAX_LENGTH = 500;

    /** care_result_items.content_snapshot 컬럼 길이. */
    private static final int ITEM_CONTENT_MAX_LENGTH = 500;

    /** care_results.clinician_message 컬럼 길이. */
    private static final int CLINICIAN_MESSAGE_MAX_LENGTH = 1000;

    private final CareRuleCatalogPort careRuleCatalogPort;
    private final CareRuleEngine careRuleEngine;
    private final CareGuideNarrationPort narrationPort;
    private final CareGuideItemPlanner itemPlanner;
    private final RecommendedIngredientPlanner ingredientPlanner;
    private final CareResultRepository careResultRepository;
    private final CareResultRuleRepository careResultRuleRepository;
    private final CareResultItemRepository careResultItemRepository;
    private final CareResultIngredientRepository careResultIngredientRepository;
    private final CareResultIngredientRuleRepository careResultIngredientRuleRepository;
    private final Clock clock;

    public CareResultGenerator(
            CareRuleCatalogPort careRuleCatalogPort,
            CareRuleEngine careRuleEngine,
            CareGuideNarrationPort narrationPort,
            CareGuideItemPlanner itemPlanner,
            RecommendedIngredientPlanner ingredientPlanner,
            CareResultRepository careResultRepository,
            CareResultRuleRepository careResultRuleRepository,
            CareResultItemRepository careResultItemRepository,
            CareResultIngredientRepository careResultIngredientRepository,
            CareResultIngredientRuleRepository careResultIngredientRuleRepository,
            Clock clock
    ) {
        this.careRuleCatalogPort = careRuleCatalogPort;
        this.careRuleEngine = careRuleEngine;
        this.narrationPort = narrationPort;
        this.itemPlanner = itemPlanner;
        this.ingredientPlanner = ingredientPlanner;
        this.careResultRepository = careResultRepository;
        this.careResultRuleRepository = careResultRuleRepository;
        this.careResultItemRepository = careResultItemRepository;
        this.careResultIngredientRepository = careResultIngredientRepository;
        this.careResultIngredientRuleRepository = careResultIngredientRuleRepository;
        this.clock = clock;
    }

    /** 규칙을 맞춰 보고 문구까지 정한다. 여기서는 아무것도 저장하지 않는다. */
    public CareResultPlan plan(ResultType resultType, RuleEvaluationFacts facts) {
        ActiveRuleCatalog catalog = careRuleCatalogPort.loadActiveCatalog()
                .orElseThrow(() -> new BusinessException(ErrorCode.RULE_ENGINE_UNAVAILABLE));

        List<CareRuleSnapshot> matchedRules = careRuleEngine.match(catalog, facts);
        if (matchedRules.isEmpty()) {
            // 걸리는 규칙이 없으면 안내할 근거가 없다. 빈 결과를 만들거나 문구를 지어내지 않는다.
            throw new BusinessException(ErrorCode.RULE_ENGINE_UNAVAILABLE);
        }

        CareResultPlan plan = resultType == ResultType.CLINICIAN_CHECK
                ? planClinicianCheck(catalog, withoutFallback(matchedRules))
                : planSelfCare(catalog, matchedRules, facts);
        assertStorable(plan);
        return plan;
    }

    /**
     * 일반 관리 안내를 정한다. 폴백을 단독으로 쓸 상황인지에 따라 두 갈래로 나뉜다.
     *
     * <p>상황 규칙이 하나도 걸리지 않고 폴백 규칙이 있으면 폴백만 단독으로 쓴다. 규칙 문서가
     * 폴백을 "다른 관리 규칙과 조합하지 않는 독립 규칙"으로 정했다. 원인을 특정하지 못한 상태에서
     * 공통 안내나 겉모습 규칙을 함께 얹으면 무엇 때문에 이 안내가 나왔는지 읽을 수 없게 된다.
     *
     * <p>폴백 규칙이 정의되지 않은 규칙 세트라면 평소대로 조합한다. 이때 남는 것은 대개 공통
     * 규칙인데, 문서도 공통 안내 단독 출력을 "안전하지만 완결성이 떨어지는 상태"로 보고 폴백을
     * 따로 둔 것이다. 즉 폴백이 없으면 공통 안내가 그 자리를 대신하고, 결과를 못 내보낼 이유는
     * 없다.
     *
     * <p>상황 규칙이 걸렸다면 폴백은 빠진다. 폴백 규칙은 조건이 없어 항상 후보에 들어오지만
     * 조합 대상이 아니다.
     */
    private CareResultPlan planSelfCare(
            ActiveRuleCatalog catalog,
            List<CareRuleSnapshot> matchedRules,
            RuleEvaluationFacts facts
    ) {
        if (usesFallbackOnly(matchedRules)) {
            return planFallbackOnly(catalog, matchedRules);
        }
        List<CareRuleSnapshot> rules = withoutFallback(matchedRules);
        return planSelfCareGuide(catalog, rules, CareActionAllowList.from(rules), facts);
    }

    private boolean usesFallbackOnly(List<CareRuleSnapshot> matchedRules) {
        return !hasCategory(matchedRules, RuleCategory.SITUATION)
                && hasCategory(matchedRules, RuleCategory.FALLBACK);
    }

    private boolean hasCategory(List<CareRuleSnapshot> matchedRules, RuleCategory category) {
        return matchedRules.stream().anyMatch(rule -> rule.category() == category);
    }

    /** 폴백은 단독 실행 전용이라 다른 규칙과 함께 쓰지 않는다. */
    private List<CareRuleSnapshot> withoutFallback(List<CareRuleSnapshot> matchedRules) {
        return matchedRules.stream()
                .filter(rule -> rule.category() != RuleCategory.FALLBACK)
                .toList();
    }

    /**
     * 폴백 규칙만으로 안내를 정한다.
     *
     * <p>AI를 부르지 않는다. 무엇 때문에 불편이 생겼는지 모르는 상태라, 요약을 새로 쓰게 하면
     * 원인을 짐작한 문장이 나올 여지가 생긴다. 규칙 문서도 폴백에서 임의 안심과 질환명 추측을
     * 금지 표현으로 못 박았다. 그래서 승인된 브릿지 문구를 요약으로 쓰고 행동은 규칙 순서대로
     * 채운다.
     *
     * <p>성분도 내보내지 않는다. 원인이 불특정한 상태에서 성분을 권하면 근거 없는 추천이 된다.
     */
    private CareResultPlan planFallbackOnly(
            ActiveRuleCatalog catalog,
            List<CareRuleSnapshot> matchedRules
    ) {
        List<CareRuleSnapshot> fallbackRules = matchedRules.stream()
                .filter(rule -> rule.category() == RuleCategory.FALLBACK)
                .toList();
        CareActionAllowList allowList = CareActionAllowList.from(fallbackRules);
        List<PlannedCareItem> items = itemPlanner.planFromRules(allowList);
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCode.RULE_ENGINE_UNAVAILABLE);
        }

        return new CareResultPlan(
                catalog.ruleSetId(),
                catalog.versionCode(),
                ResultType.SELF_CARE_GUIDE,
                AiGenerationStatus.FALLBACK,
                requireFallbackText(allowList),
                null,
                fallbackRules,
                items,
                List.of()
        );
    }

    /** 정해 둔 결과를 저장한다. 외부 호출이 끼지 않아 트랜잭션이 짧게 끝난다. */
    public GeneratedCareResult persist(
            UUID reportId,
            UUID userId,
            CareResultPlan plan,
            ScoredSimilarExperience similarExperience
    ) {
        LocalDateTime generatedAt = LocalDateTime.now(clock);
        UUID similarReportId = similarExperience == null ? null : similarExperience.reportId();
        Integer similarityScore = similarExperience == null ? null : similarExperience.score();

        CareResult careResult = careResultRepository.saveAndFlush(
                plan.resultType() == ResultType.CLINICIAN_CHECK
                        ? CareResult.clinicianCheck(
                                reportId,
                                userId,
                                plan.ruleSetId(),
                                similarReportId,
                                similarityScore,
                                plan.summary(),
                                plan.clinicianMessage(),
                                generatedAt
                        )
                        : CareResult.selfCareGuide(
                                reportId,
                                userId,
                                plan.ruleSetId(),
                                similarReportId,
                                similarityScore,
                                plan.aiGenerationStatus(),
                                plan.summary(),
                                generatedAt
                        )
        );

        saveAppliedRules(careResult.getId(), plan.matchedRules());
        saveItems(careResult.getId(), plan.items());
        saveIngredients(careResult.getId(), plan.ingredients());
        return new GeneratedCareResult(
                careResult, plan.ruleVersion(), plan.matchedRules(), plan.items(), plan.ingredients()
        );
    }

    /**
     * 일반 관리 안내를 정한다.
     *
     * <p>AI가 성공하면 고른 문구와 요약을 쓴다. 실패하면 규칙 순서대로 채우고 승인된 대체 문구를
     * 요약으로 쓴다. 대체 문구가 없으면 저장할 요약이 없어 결과를 만들 수 없으므로 503으로 돌린다.
     */
    private CareResultPlan planSelfCareGuide(
            ActiveRuleCatalog catalog,
            List<CareRuleSnapshot> matchedRules,
            CareActionAllowList allowList,
            RuleEvaluationFacts facts
    ) {
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
                return selfCareGuidePlan(
                        catalog, matchedRules, AiGenerationStatus.GENERATED, narration.summary(), items
                );
            }
        }

        // 대체 경로에도 보여 줄 항목이 있어야 한다. 항목 없는 안내는 사용자가 할 수 있는 일이
        // 없다는 뜻이라 결과로 내보내지 않고 규칙이 준비되지 않은 것으로 다룬다.
        List<PlannedCareItem> fallbackItems = itemPlanner.planFromRules(allowList);
        if (fallbackItems.isEmpty()) {
            throw new BusinessException(ErrorCode.RULE_ENGINE_UNAVAILABLE);
        }
        return selfCareGuidePlan(
                catalog,
                matchedRules,
                AiGenerationStatus.FALLBACK,
                requireFallbackText(allowList),
                fallbackItems
        );
    }

    /**
     * 의료진 확인 안내를 정한다.
     *
     * <p>AI를 부르지 않는다. 병원에 가 보라는 안내는 문장을 다듬을 대상이 아니라 승인된 문구를
     * 그대로 전달해야 하는 내용이다. 그래서 상태가 NOT_APPLICABLE이고 재생성도 열리지 않는다.
     */
    private CareResultPlan planClinicianCheck(
            ActiveRuleCatalog catalog,
            List<CareRuleSnapshot> matchedRules
    ) {
        CareActionAllowList allowList = CareActionAllowList.from(matchedRules);
        List<PlannedCareItem> clinicianMessage = itemPlanner.planClinicianMessage(allowList);
        if (clinicianMessage.isEmpty()) {
            throw new BusinessException(ErrorCode.RULE_ENGINE_UNAVAILABLE);
        }

        List<PlannedCareItem> items = new ArrayList<>(itemPlanner.planFromRules(allowList));
        items.addAll(clinicianMessage);
        return new CareResultPlan(
                catalog.ruleSetId(),
                catalog.versionCode(),
                ResultType.CLINICIAN_CHECK,
                AiGenerationStatus.NOT_APPLICABLE,
                requireFallbackText(allowList),
                clinicianMessage.getFirst().content(),
                matchedRules,
                items,
                // 명세가 CLINICIAN_CHECK 에 recommendedIngredients maxItems 0 을 걸어 두었다.
                // 병원에 가 보라는 안내에 성분을 함께 주면 자가 처치를 권하는 것으로 읽힌다.
                List.of()
        );
    }

    private CareResultPlan selfCareGuidePlan(
            ActiveRuleCatalog catalog,
            List<CareRuleSnapshot> matchedRules,
            AiGenerationStatus aiGenerationStatus,
            String summary,
            List<PlannedCareItem> items
    ) {
        return new CareResultPlan(
                catalog.ruleSetId(),
                catalog.versionCode(),
                ResultType.SELF_CARE_GUIDE,
                aiGenerationStatus,
                summary,
                null,
                matchedRules,
                items,
                // 성분은 규칙표에서만 온다. AI 성공 여부와 무관하게 같은 값이 나온다.
                ingredientPlanner.plan(matchedRules)
        );
    }

    private String requireFallbackText(CareActionAllowList allowList) {
        return Optional.ofNullable(allowList.fallbackText())
                .orElseThrow(() -> new BusinessException(ErrorCode.RULE_ENGINE_UNAVAILABLE));
    }

    /**
     * 컬럼에 들어갈 수 있는 길이인지 확인한다.
     *
     * <p>규칙 쪽 원본은 TEXT지만 결과 스냅샷은 VARCHAR다. 긴 문구를 잘라 내면 승인된 안내가 말이
     * 끊긴 채로 사용자에게 나가므로 자르지 않고 규칙이 준비되지 않은 것으로 다룬다. 규칙 데이터를
     * 고쳐야 하는 상황이라 규칙 코드를 로그에 남긴다.
     */
    private void assertStorable(CareResultPlan plan) {
        if (isTooLong(plan.summary(), SUMMARY_MAX_LENGTH)
                || isTooLong(plan.clinicianMessage(), CLINICIAN_MESSAGE_MAX_LENGTH)
                || plan.items().stream()
                        .anyMatch(item -> isTooLong(item.content(), ITEM_CONTENT_MAX_LENGTH))) {
            log.warn(
                    "관리 규칙 문구가 저장 한도를 넘습니다. ruleVersion={} ruleCodes={}",
                    plan.ruleVersion(),
                    plan.matchedRules().stream().map(CareRuleSnapshot::ruleCode).toList()
            );
            throw new BusinessException(ErrorCode.RULE_ENGINE_UNAVAILABLE);
        }
    }

    private boolean isTooLong(String value, int maxLength) {
        return value != null && value.length() > maxLength;
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

    /**
     * 추천 성분과 그 근거 규칙을 스냅샷으로 남긴다.
     *
     * <p>성분 사전이 나중에 바뀌어도 과거 결과에 안내한 내용이 달라지지 않아야 한다.
     * 항목 문구를 스냅샷하는 것과 같은 이유다.
     */
    private void saveIngredients(UUID careResultId, List<PlannedIngredient> ingredients) {
        if (ingredients.isEmpty()) {
            return;
        }

        List<CareResultIngredient> saved = careResultIngredientRepository.saveAll(ingredients.stream()
                .map(ingredient -> CareResultIngredient.snapshot(
                        careResultId,
                        ingredient.ingredientId(),
                        ingredient.code(),
                        ingredient.name(),
                        ingredient.description(),
                        ingredient.cautionNote(),
                        ingredient.displayOrder()
                ))
                .toList());

        List<CareResultIngredientRule> sourceRules = new ArrayList<>();
        for (int index = 0; index < saved.size(); index++) {
            CareResultIngredient stored = saved.get(index);
            List<String> ruleCodes = ingredients.get(index).sourceRuleCodes();
            for (int order = 0; order < ruleCodes.size(); order++) {
                sourceRules.add(CareResultIngredientRule.of(stored.getId(), ruleCodes.get(order), order + 1));
            }
        }
        careResultIngredientRuleRepository.saveAll(sourceRules);
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
}
