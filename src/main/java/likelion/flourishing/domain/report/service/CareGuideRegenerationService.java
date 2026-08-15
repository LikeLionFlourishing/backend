package likelion.flourishing.domain.report.service;

import java.time.Clock;
import java.time.LocalDateTime;
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
import likelion.flourishing.domain.report.entity.CareResultItem;
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
import org.springframework.transaction.annotation.Transactional;

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
    private final CareRuleCatalogPort careRuleCatalogPort;
    private final CareGuideNarrationPort narrationPort;
    private final CareGuideItemPlanner itemPlanner;
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
            CareRuleCatalogPort careRuleCatalogPort,
            CareGuideNarrationPort narrationPort,
            CareGuideItemPlanner itemPlanner,
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
        this.careRuleCatalogPort = careRuleCatalogPort;
        this.narrationPort = narrationPort;
        this.itemPlanner = itemPlanner;
        this.careGuideResponseAssembler = careGuideResponseAssembler;
        this.similarExperienceFinder = similarExperienceFinder;
        this.idempotencyService = idempotencyService;
        this.consentGuard = consentGuard;
        this.clock = clock;
    }

    /**
     * 관리 설명을 다시 만든다.
     *
     * <p>확인 순서는 동의 → 소유권 → 재생성 가능 여부 → 사용 여부다. 소유권을 먼저 걸러야 남의
     * 보고 상태가 오류 메시지로 새어 나가지 않는다.
     *
     * @param idempotencyKey 없으면 null. 있으면 같은 키의 재전송에 저장된 응답을 그대로 돌려준다.
     */
    @Transactional
    public IdempotentResponse regenerate(AuthenticatedUser principal, UUID reportId, UUID idempotencyKey) {
        UUID userId = principal.userId();
        consentGuard.assertConsented(userId);

        SkinReport report = skinReportRepository.findByIdAndUserId(reportId, userId)
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
        List<PlannedCareItem> items = applyNarration(report, careResult, allowList);

        CareGuideResponse response = careGuideResponseAssembler.assemble(
                careResult,
                appliedRuleSet.versionCode(),
                appliedRuleSet.rules(),
                items,
                describeSimilarExperience(userId, careResult)
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
     * 설명을 다시 만들고 결과에 반영한다.
     *
     * <p>성공하면 항목을 지우고 새로 넣는다. (결과, 유형, 순서) 유니크 제약이 있어 덮어쓸 수 없고,
     * 지우고 넣는 두 단계가 같은 트랜잭션에서 끝나야 한다.
     *
     * <p>실패하면 저장된 항목과 요약을 그대로 둔다. 이미 승인된 대체 문구가 들어 있어 사용자에게
     * 보여 줄 것이 없어지지 않는다.
     */
    private List<PlannedCareItem> applyNarration(
            SkinReport report,
            CareResult careResult,
            CareActionAllowList allowList
    ) {
        LocalDateTime now = LocalDateTime.now(clock);
        NarrationOutcome narration = narrationPort.narrate(new NarrationCommand(
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
        ));

        if (narration.isSucceeded()) {
            List<PlannedCareItem> planned = itemPlanner.planFromNarration(allowList, narration);
            if (!planned.isEmpty()) {
                careResultItemRepository.deleteAllByCareResultId(careResult.getId());
                careResultItemRepository.flush();
                careResultItemRepository.saveAll(planned.stream()
                        .map(item -> CareResultItem.snapshot(
                                careResult.getId(),
                                item.sourceRuleActionId(),
                                item.itemType(),
                                item.content(),
                                item.displayOrder()
                        ))
                        .toList());
                careResult.applyRegeneratedGuide(AiGenerationStatus.GENERATED, narration.summary(), now);
                return planned;
            }
        }

        careResult.applyRegeneratedGuide(AiGenerationStatus.FALLBACK, careResult.getSummary(), now);
        return storedItems(careResult.getId());
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
