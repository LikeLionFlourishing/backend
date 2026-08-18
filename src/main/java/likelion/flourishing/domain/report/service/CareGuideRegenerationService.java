package likelion.flourishing.domain.report.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import likelion.flourishing.domain.report.entity.CareResultIngredient;
import likelion.flourishing.domain.report.entity.CareResultIngredientRule;
import likelion.flourishing.domain.report.repository.CareResultIngredientRepository;
import likelion.flourishing.domain.report.repository.CareResultIngredientRuleRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.report.ai.CareGuideNarrationPort;
import likelion.flourishing.domain.report.ai.NarrationCommand;
import likelion.flourishing.domain.report.ai.NarrationOutcome;
import likelion.flourishing.domain.report.dto.response.CareGuideResponse;
import likelion.flourishing.domain.report.dto.response.SimilarExperienceSummaryResponse;
import likelion.flourishing.domain.report.entity.AiGenerationStatus;
import likelion.flourishing.domain.report.entity.CareResult;
import likelion.flourishing.domain.report.entity.RuleActionType;
import likelion.flourishing.domain.report.entity.SkinReport;
import likelion.flourishing.domain.report.idempotency.IdempotencyService;
import likelion.flourishing.domain.report.idempotency.IdempotentResponse;
import likelion.flourishing.domain.report.repository.CareResultItemRepository;
import likelion.flourishing.domain.report.repository.CareResultRepository;
import likelion.flourishing.domain.report.repository.CareResultRuleRepository;
import likelion.flourishing.domain.report.repository.SkinReportRepository;
import likelion.flourishing.domain.report.rule.AppliedRuleSet;
import likelion.flourishing.domain.report.rule.CareActionAllowList;
import likelion.flourishing.domain.report.rule.CareRuleCatalogPort;
import likelion.flourishing.domain.report.similarity.SimilarExperienceFinder;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import org.springframework.stereotype.Service;

/**
 * 대체 문구로 저장된 관리 설명을 한 번 더 만들어 본다.
 *
 * <p>다시 만드는 것은 문구뿐이다. 어떤 규칙이 걸렸는지는 처음 결정한 그대로 쓴다. 지금 활성 규칙으로
 * 다시 판단하면 같은 보고의 근거가 바뀌어 사용자가 이전에 본 설명을 되짚을 수 없게 된다.
 *
 * <p>재생성은 결과당 한 번이다. 성공했든 또 실패했든 시도하면 기회를 쓴 것으로 본다. 실패를 세지
 * 않으면 같은 요청을 계속 보내 AI를 무한히 호출할 수 있다. 실패해도 응답은 200이고, 대체 문구로
 * 남은 결과가 그대로 나간다.
 *
 * <p>기회를 가져가는 것은 {@link CareGuideRewriter}의 조건부 갱신이다. 여기서 미리 보는 확인은
 * 불필요한 AI 호출을 줄이기 위한 것이고, 동시 요청에서 한 번만 통과시키는 판정은 DB가 한다.
 *
 * <p>의료진 확인 결과는 대상이 아니다. AI가 만든 설명이 아니라 승인된 문구를 그대로 전달한 결과라
 * 다시 만들 것이 없다.
 */
@Service
public class CareGuideRegenerationService {

    /** idempotency_records.operation_id에 남기는 작업 이름. */
    public static final String OPERATION_ID = "POST /v1/skin-reports/{reportId}/care-guide-generations";

    private final SkinReportRepository skinReportRepository;
    private final CareResultRepository careResultRepository;
    private final CareResultRuleRepository careResultRuleRepository;
    private final CareResultItemRepository careResultItemRepository;
    private final CareResultIngredientRepository careResultIngredientRepository;
    private final CareResultIngredientRuleRepository careResultIngredientRuleRepository;
    private final CareRuleCatalogPort careRuleCatalogPort;
    private final CareGuideNarrationPort narrationPort;
    private final CareGuideItemPlanner itemPlanner;
    private final CareGuideRewriter careGuideRewriter;
    private final CareGuideResponseAssembler careGuideResponseAssembler;
    private final SimilarExperienceFinder similarExperienceFinder;
    private final IdempotencyService idempotencyService;
    private final SensitiveDataConsentGuard consentGuard;
    private final Clock clock;

    public CareGuideRegenerationService(
            SkinReportRepository skinReportRepository,
            CareResultRepository careResultRepository,
            CareResultRuleRepository careResultRuleRepository,
            CareResultItemRepository careResultItemRepository,
            CareResultIngredientRepository careResultIngredientRepository,
            CareResultIngredientRuleRepository careResultIngredientRuleRepository,
            CareRuleCatalogPort careRuleCatalogPort,
            CareGuideNarrationPort narrationPort,
            CareGuideItemPlanner itemPlanner,
            CareGuideRewriter careGuideRewriter,
            CareGuideResponseAssembler careGuideResponseAssembler,
            SimilarExperienceFinder similarExperienceFinder,
            IdempotencyService idempotencyService,
            SensitiveDataConsentGuard consentGuard,
            Clock clock
    ) {
        this.skinReportRepository = skinReportRepository;
        this.careResultRepository = careResultRepository;
        this.careResultRuleRepository = careResultRuleRepository;
        this.careResultItemRepository = careResultItemRepository;
        this.careResultIngredientRepository = careResultIngredientRepository;
        this.careResultIngredientRuleRepository = careResultIngredientRuleRepository;
        this.careRuleCatalogPort = careRuleCatalogPort;
        this.narrationPort = narrationPort;
        this.itemPlanner = itemPlanner;
        this.careGuideRewriter = careGuideRewriter;
        this.careGuideResponseAssembler = careGuideResponseAssembler;
        this.similarExperienceFinder = similarExperienceFinder;
        this.idempotencyService = idempotencyService;
        this.consentGuard = consentGuard;
        this.clock = clock;
    }

    /**
     * 관리 설명을 다시 만든다.
     *
     * <p>확인 순서는 동의 → 소유권 → 사용 여부 → 재생성 가능 여부다. 소유권을 먼저 걸러야 남의
     * 보고 상태가 오류 메시지로 새어 나가지 않는다.
     *
     * <p>트랜잭션을 걸지 않는다. AI 호출까지 끝낸 다음 저장만 {@link CareGuideRewriter}에 맡긴다.
     *
     * @param idempotencyKey 없으면 null. 있으면 같은 키의 재전송에 저장된 응답을 그대로 돌려준다.
     */
    public IdempotentResponse regenerate(AuthenticatedUser principal, UUID reportId, UUID idempotencyKey) {
        UUID userId = principal.userId();
        consentGuard.assertConsented(userId);

        // 선택값까지 함께 읽는다. 아래 AI 호출이 트랜잭션 밖이라 그 뒤에는 지연 로딩이 되지 않는다.
        SkinReport report = skinReportRepository.findWithSelectionsByIdAndUserId(reportId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        CareResult careResult = careResultRepository.findByReportIdAndUserId(reportId, userId)
                .orElseThrow(() -> new IllegalStateException("피부 보고에 관리 결과가 없습니다."));

        // 사용 여부를 먼저 본다. 재생성이 성공하면 상태가 GENERATED로 바뀌어 재생성 대상이
        // 아니게 되는데, 그때 나가야 하는 답은 "대상이 아니다"가 아니라 "이미 한 번 썼다"다.
        if (careResult.isRetryUsed()) {
            throw new BusinessException(ErrorCode.AI_RETRY_ALREADY_USED);
        }
        if (!careResult.isRegenerable()) {
            throw new BusinessException(ErrorCode.AI_RETRY_NOT_AVAILABLE);
        }

        Object fingerprint = new RegenerationFingerprint(report.getId());
        Optional<IdempotentResponse> replay = idempotencyKey == null
                ? Optional.empty()
                : idempotencyService.findReplay(userId, OPERATION_ID, idempotencyKey, fingerprint);
        if (replay.isPresent()) {
            return replay.get();
        }

        List<UUID> appliedVersionIds = careResultRuleRepository
                .findAllByIdCareResultIdOrderByApplicationOrder(careResult.getId()).stream()
                .map(rule -> rule.getId().getRuleVersionId())
                .toList();
        AppliedRuleSet appliedRuleSet = careRuleCatalogPort
                .loadAppliedRules(careResult.getRuleSetId(), appliedVersionIds)
                .orElseThrow(() -> new BusinessException(ErrorCode.RULE_ENGINE_UNAVAILABLE));

        CareActionAllowList allowList = CareActionAllowList.from(appliedRuleSet.rules());
        NarrationOutcome narration = narrate(report, allowList);
        List<PlannedCareItem> planned = narration.isSucceeded()
                ? itemPlanner.planFromNarration(allowList, narration)
                : List.of();

        CareResult updated = applyOutcome(careResult, narration, planned);
        List<PlannedCareItem> items = planned.isEmpty() ? storedItems(careResult.getId()) : planned;

        CareGuideResponse response = careGuideResponseAssembler.assemble(
                updated,
                appliedRuleSet.versionCode(),
                appliedRuleSet.rules(),
                items,
                // 재생성은 설명만 다시 만든다. 명세가 규칙 ID·버전·허용 문구와 함께 추천 성분도
                // 바꾸지 않는다고 정하므로 저장된 스냅샷을 그대로 읽는다.
                storedIngredients(careResult.getId()),
                describeSimilarExperience(userId, updated)
        );
        IdempotentResponse regenerated = IdempotentResponse.ok(
                idempotencyService.serialize(response), report.getId()
        );
        if (idempotencyKey != null) {
            idempotencyService.store(userId, OPERATION_ID, idempotencyKey, fingerprint, regenerated);
        }
        return regenerated;
    }

    /**
     * 설명 생성을 시도한다.
     *
     * <p>고를 문구가 하나도 없으면 부르지 않고 503으로 돌린다. 다시 만들 재료가 없는 상황이라
     * 실패로 처리해 단 한 번의 기회를 쓰게 하면 사용자가 손해를 본다.
     */
    private NarrationOutcome narrate(SkinReport report, CareActionAllowList allowList) {
        NarrationCommand command = new NarrationCommand(
                report.getPrimaryArea(),
                report.getAppearances(),
                report.getSensations(),
                report.getSituations(),
                report.getCareAvailability(),
                allowList.ruleSummaries(),
                allowList.contentsOf(RuleActionType.DO_TODAY),
                allowList.contentsOf(RuleActionType.AVOID_TODAY),
                allowList.contentsOf(RuleActionType.CHECK_NEXT),
                allowList.forbiddenExpressions(),
                CareGuideItemPlanner.MAX_ITEMS_PER_TYPE
        );
        if (!command.hasAllowedActions()) {
            throw new BusinessException(ErrorCode.RULE_ENGINE_UNAVAILABLE);
        }
        return narrationPort.narrate(command);
    }

    /**
     * 결과를 갱신한다.
     *
     * <p>성공하면 새 요약과 항목으로 바꾼다. 실패하면 저장된 요약과 항목을 그대로 두고 상태만
     * FALLBACK으로 유지한다. 어느 쪽이든 기회를 쓴 것으로 표시한다.
     */
    private CareResult applyOutcome(
            CareResult careResult,
            NarrationOutcome narration,
            List<PlannedCareItem> planned
    ) {
        boolean succeeded = narration.isSucceeded() && !planned.isEmpty();
        return careGuideRewriter.rewrite(
                careResult.getId(),
                succeeded ? AiGenerationStatus.GENERATED : AiGenerationStatus.FALLBACK,
                succeeded ? narration.summary() : careResult.getSummary(),
                LocalDateTime.now(clock),
                planned
        ).orElseThrow(() -> new BusinessException(ErrorCode.AI_RETRY_ALREADY_USED));
    }

    private List<PlannedCareItem> storedItems(UUID careResultId) {
        return careResultItemRepository
                .findAllByCareResultIdOrderByItemTypeAscDisplayOrderAsc(careResultId).stream()
                .map(item -> new PlannedCareItem(
                        item.getItemType(),
                        item.getContentSnapshot(),
                        item.getSourceRuleActionId(),
                        item.getDisplayOrder()
                ))
                .toList();
    }

    /** 저장해 둔 추천 성분과 그 근거 규칙을 다시 읽는다. 재생성이 성분을 바꾸지 않게 하는 경로다. */
    private List<PlannedIngredient> storedIngredients(UUID careResultId) {
        List<CareResultIngredient> stored = careResultIngredientRepository
                .findAllByCareResultIdOrderByDisplayOrderAsc(careResultId);
        if (stored.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<String>> ruleCodesByIngredient = careResultIngredientRuleRepository
                .findAllByIdCareResultIngredientIdIn(stored.stream().map(CareResultIngredient::getId).toList())
                .stream()
                .sorted(Comparator.comparingInt(CareResultIngredientRule::getDisplayOrder))
                .collect(Collectors.groupingBy(
                        CareResultIngredientRule::careResultIngredientId,
                        LinkedHashMap::new,
                        Collectors.mapping(CareResultIngredientRule::ruleCode, Collectors.toList())
                ));

        return stored.stream()
                .map(ingredient -> new PlannedIngredient(
                        ingredient.getSourceIngredientId(),
                        ingredient.getIngredientCode(),
                        ingredient.getNameSnapshot(),
                        ingredient.getDescriptionSnapshot(),
                        ingredient.getCautionNoteSnapshot(),
                        ruleCodesByIngredient.getOrDefault(ingredient.getId(), List.of()),
                        ingredient.getDisplayOrder()
                ))
                .toList();
    }

    private SimilarExperienceSummaryResponse describeSimilarExperience(UUID userId, CareResult careResult) {
        if (careResult.getSimilarReportId() == null || careResult.getSimilarityScore() == null) {
            return null;
        }
        return similarExperienceFinder
                .describe(userId, careResult.getSimilarReportId(), careResult.getSimilarityScore())
                .orElse(null);
    }

    /**
     * 재전송 판단에 쓸 지문.
     *
     * <p>이 요청에는 본문이 없어 경로의 보고 식별자만 담는다. 같은 키를 다른 보고에 쓰면 409가 된다.
     */
    private record RegenerationFingerprint(UUID reportId) {
    }
}
