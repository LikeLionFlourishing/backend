package likelion.flourishing.domain.record.service;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import likelion.flourishing.domain.report.dto.response.GuideSectionResponse;
import likelion.flourishing.domain.report.dto.response.RecommendedIngredientResponse;
import likelion.flourishing.domain.report.entity.CareResultIngredient;
import likelion.flourishing.domain.report.entity.CareResultIngredientRule;
import likelion.flourishing.domain.report.repository.CareResultIngredientRepository;
import likelion.flourishing.domain.report.repository.CareResultIngredientRuleRepository;
import likelion.flourishing.domain.report.service.GuideSectionAssembler;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.followup.dto.response.FollowUpResponse;
import likelion.flourishing.domain.followup.entity.FollowUp;
import likelion.flourishing.domain.followup.repository.FollowUpRepository;
import likelion.flourishing.domain.record.cursor.SkinReportCursor;
import likelion.flourishing.domain.record.cursor.SkinReportCursorCodec;
import likelion.flourishing.domain.record.dto.response.CareResultResponse;
import likelion.flourishing.domain.record.dto.response.ConfirmedStructuredReportResponse;
import likelion.flourishing.domain.record.dto.response.CursorPageResponse;
import likelion.flourishing.domain.record.dto.response.SimilarExperienceResponse;
import likelion.flourishing.domain.record.dto.response.SkinReportDetailResponse;
import likelion.flourishing.domain.record.dto.response.SkinReportListResponse;
import likelion.flourishing.domain.record.dto.response.SkinReportSummaryResponse;
import likelion.flourishing.domain.report.crypto.ReportTextCipher;
import likelion.flourishing.domain.report.entity.CareResult;
import likelion.flourishing.domain.report.entity.CareResultItem;
import likelion.flourishing.domain.report.entity.CareResultItemType;
import likelion.flourishing.domain.report.entity.CareResultRule;
import likelion.flourishing.domain.report.entity.CareRule;
import likelion.flourishing.domain.report.entity.CareRuleVersion;
import likelion.flourishing.domain.report.entity.MatchReason;
import likelion.flourishing.domain.report.entity.ReportStatus;
import likelion.flourishing.domain.report.entity.ResultType;
import likelion.flourishing.domain.report.entity.RuleSet;
import likelion.flourishing.domain.report.entity.SkinReport;
import likelion.flourishing.domain.report.repository.CareResultItemRepository;
import likelion.flourishing.domain.report.repository.CareResultRepository;
import likelion.flourishing.domain.report.repository.CareResultRuleRepository;
import likelion.flourishing.domain.report.repository.CareRuleRepository;
import likelion.flourishing.domain.report.repository.CareRuleVersionRepository;
import likelion.flourishing.domain.report.repository.RuleSetRepository;
import likelion.flourishing.domain.report.repository.SkinReportRepository;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 피부 기록 목록과 상세 응답을 사용자별로 격리해 조립한다. */
@Service
public class SkinRecordService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 100;

    private final SkinReportRepository skinReportRepository;
    private final CareResultRepository careResultRepository;
    private final CareResultRuleRepository careResultRuleRepository;
    private final CareResultItemRepository careResultItemRepository;
    private final CareRuleVersionRepository careRuleVersionRepository;
    private final CareRuleRepository careRuleRepository;
    private final RuleSetRepository ruleSetRepository;
    private final FollowUpRepository followUpRepository;
    private final CareResultIngredientRepository careResultIngredientRepository;
    private final CareResultIngredientRuleRepository careResultIngredientRuleRepository;
    private final GuideSectionAssembler guideSectionAssembler;
    private final ReportTextCipher reportTextCipher;
    private final SkinReportCursorCodec cursorCodec;

    public SkinRecordService(
            SkinReportRepository skinReportRepository,
            CareResultRepository careResultRepository,
            CareResultRuleRepository careResultRuleRepository,
            CareResultItemRepository careResultItemRepository,
            CareRuleVersionRepository careRuleVersionRepository,
            CareRuleRepository careRuleRepository,
            RuleSetRepository ruleSetRepository,
            FollowUpRepository followUpRepository,
            CareResultIngredientRepository careResultIngredientRepository,
            CareResultIngredientRuleRepository careResultIngredientRuleRepository,
            GuideSectionAssembler guideSectionAssembler,
            ReportTextCipher reportTextCipher,
            SkinReportCursorCodec cursorCodec
    ) {
        this.skinReportRepository = skinReportRepository;
        this.careResultRepository = careResultRepository;
        this.careResultRuleRepository = careResultRuleRepository;
        this.careResultItemRepository = careResultItemRepository;
        this.careRuleVersionRepository = careRuleVersionRepository;
        this.careRuleRepository = careRuleRepository;
        this.ruleSetRepository = ruleSetRepository;
        this.followUpRepository = followUpRepository;
        this.careResultIngredientRepository = careResultIngredientRepository;
        this.careResultIngredientRuleRepository = careResultIngredientRuleRepository;
        this.guideSectionAssembler = guideSectionAssembler;
        this.reportTextCipher = reportTextCipher;
        this.cursorCodec = cursorCodec;
    }

    @Transactional(readOnly = true)
    public SkinReportListResponse getRecords(
            AuthenticatedUser principal,
            String encodedCursor,
            Integer requestedLimit,
            ReportStatus status,
            ResultType resultType
    ) {
        int limit = requestedLimit == null ? DEFAULT_LIMIT : requestedLimit;
        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        SkinReportCursor cursor = cursorCodec.decode(encodedCursor);
        List<SkinReport> fetched = skinReportRepository.findOwnedPage(
                principal.userId(), status, resultType, cursor, limit + 1
        );
        boolean hasMore = fetched.size() > limit;
        List<SkinReport> page = hasMore
                ? new ArrayList<>(fetched.subList(0, limit))
                : new ArrayList<>(fetched);

        Map<UUID, FollowUp> followUps = findFollowUps(page, principal.userId());
        List<SkinReportSummaryResponse> data = page.stream()
                .map(report -> toSummary(report, followUps.get(report.getId())))
                .toList();

        String nextCursor = hasMore
                ? cursorCodec.encode(toCursor(page.get(page.size() - 1)))
                : null;
        return SkinReportListResponse.of(data, CursorPageResponse.of(nextCursor, hasMore, limit));
    }

    @Transactional(readOnly = true)
    public SkinReportDetailResponse getRecord(AuthenticatedUser principal, UUID reportId) {
        UUID userId = principal.userId();
        SkinReport report = skinReportRepository.findByIdAndUserId(reportId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        CareResult careResult = careResultRepository.findByReportIdAndUserId(reportId, userId)
                .orElseThrow(() -> brokenInvariant("피부 보고에 관리 결과가 없습니다."));
        assertCareResultMatchesReport(report, careResult);
        FollowUp followUp = followUpRepository.findByReportIdAndUserId(reportId, userId).orElse(null);
        assertFollowUpMatchesStatus(report, followUp);

        List<likelion.flourishing.domain.report.entity.Appearance> appearances = sorted(report.getAppearances());
        List<likelion.flourishing.domain.report.entity.Sensation> sensations = sorted(report.getSensations());
        List<likelion.flourishing.domain.report.entity.Situation> situations = sorted(report.getSituations());
        List<likelion.flourishing.domain.report.entity.PreCareCheck> preCareChecks =
                sorted(report.getPreCareChecks());
        FollowUpResponse followUpResponse = followUp == null ? null : FollowUpResponse.from(followUp);

        return SkinReportDetailResponse.of(
                report.getId(),
                report.getReportDate(),
                report.getPrimaryArea(),
                appearances,
                sensations,
                situations,
                report.getResultType(),
                report.getStatus(),
                followUp == null ? null : followUp.getSkinChange(),
                reportTextCipher.decrypt(report.getRawTextEncrypted()),
                ConfirmedStructuredReportResponse.of(
                        report.getPrimaryArea(),
                        reportTextCipher.decrypt(report.getOtherAreasNoteEncrypted()),
                        appearances,
                        sensations,
                        situations,
                        report.getCareAvailability()
                ),
                preCareChecks,
                toCareResult(careResult, userId),
                followUpResponse,
                report.getCreatedAt().atOffset(ZoneOffset.UTC)
        );
    }

    private Map<UUID, FollowUp> findFollowUps(List<SkinReport> reports, UUID userId) {
        if (reports.isEmpty()) {
            return Map.of();
        }
        List<UUID> reportIds = reports.stream().map(SkinReport::getId).toList();
        return followUpRepository.findAllByReportIdInAndUserId(reportIds, userId).stream()
                .collect(Collectors.toMap(FollowUp::getReportId, Function.identity()));
    }

    private SkinReportSummaryResponse toSummary(SkinReport report, FollowUp followUp) {
        return SkinReportSummaryResponse.of(
                report.getId(),
                report.getReportDate(),
                report.getPrimaryArea(),
                sorted(report.getAppearances()),
                sorted(report.getSensations()),
                sorted(report.getSituations()),
                report.getResultType(),
                report.getStatus(),
                followUp == null ? null : followUp.getSkinChange()
        );
    }

    private CareResultResponse toCareResult(CareResult careResult, UUID userId) {
        List<CareResultRule> appliedRules = careResultRuleRepository
                .findAllByIdCareResultIdOrderByApplicationOrder(careResult.getId());
        AppliedRuleData appliedRuleData = resolveAppliedRules(careResult, appliedRules);
        Map<CareResultItemType, List<String>> items = careResultItemRepository
                .findAllByCareResultIdOrderByItemTypeAscDisplayOrderAsc(careResult.getId()).stream()
                .collect(Collectors.groupingBy(
                        CareResultItem::getItemType,
                        () -> new EnumMap<>(CareResultItemType.class),
                        Collectors.mapping(CareResultItem::getContentSnapshot, Collectors.toList())
                ));

        List<String> doToday = items.getOrDefault(CareResultItemType.DO_TODAY, List.of());
        List<String> avoidToday = items.getOrDefault(CareResultItemType.AVOID_TODAY, List.of());
        List<String> checkNext = items.getOrDefault(CareResultItemType.CHECK_NEXT, List.of());
        SimilarExperienceResponse similarExperience = toSimilarExperience(careResult, userId);

        // 명세가 CLINICIAN_CHECK 에 두 필드 모두 maxItems 0 을 걸어 두었다. 보고 생성 응답과
        // 같은 판단을 여기서도 한다. 같은 보고인데 경로에 따라 화면이 달라지면 안 된다.
        boolean clinicianCheck = careResult.getResultType() == ResultType.CLINICIAN_CHECK;

        List<RecommendedIngredientResponse> recommendedIngredients = clinicianCheck
                ? List.of()
                : storedIngredients(careResult.getId(), appliedRuleData.ruleCodes());

        List<GuideSectionResponse> guideSections = clinicianCheck
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
                appliedRuleData.ruleCodes(),
                guideSections,
                appliedRuleData.ruleSetVersion(),
                careResult.getSummary(),
                doToday,
                avoidToday,
                checkNext,
                recommendedIngredients,
                appliedRuleData.reasonTags(),
                careResult.getClinicianMessage(),
                similarExperience,
                careResult.getAiGenerationStatus(),
                careResult.getGeneratedAt().atOffset(ZoneOffset.UTC),
                careResult.isRetryUsed()
        );
    }

    /**
     * 저장해 둔 추천 성분 스냅샷.
     *
     * <p>결과를 만들 때 복사해 둔 값을 그대로 읽는다. 성분 사전이 나중에 바뀌어도 과거 기록에
     * 안내한 내용이 달라지지 않게 하려고 스냅샷을 남긴 것이라, 여기서 사전을 다시 읽으면 그
     * 목적이 사라진다.
     *
     * <p>근거 규칙이 적용 규칙 밖을 가리키면 그 성분을 뺀다. 보고 생성 쪽과 같은 판단이다.
     * 명세가 sourceRuleIds 에 minItems 1 을 요구해서 빈 배열로 둘 수 없다.
     */
    private List<RecommendedIngredientResponse> storedIngredients(UUID careResultId, List<String> matchedRuleIds) {
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

        List<RecommendedIngredientResponse> responses = new ArrayList<>();
        for (CareResultIngredient ingredient : stored) {
            List<String> sourceRuleIds = ruleCodesByIngredient.getOrDefault(ingredient.getId(), List.of()).stream()
                    .filter(matchedRuleIds::contains)
                    .toList();
            if (sourceRuleIds.isEmpty()) {
                continue;
            }
            responses.add(RecommendedIngredientResponse.of(
                    ingredient.getIngredientCode(),
                    ingredient.getNameSnapshot(),
                    ingredient.getDescriptionSnapshot(),
                    ingredient.getCautionNoteSnapshot(),
                    sourceRuleIds
            ));
        }
        return List.copyOf(responses);
    }

    private AppliedRuleData resolveAppliedRules(CareResult careResult, List<CareResultRule> appliedRules) {
        RuleSet ruleSet = ruleSetRepository.findById(careResult.getRuleSetId())
                .orElseThrow(() -> brokenInvariant("관리 결과의 규칙 세트가 없습니다."));
        if (appliedRules.isEmpty()) {
            return new AppliedRuleData(List.of(), ruleSet.getVersionCode(), List.of());
        }

        List<UUID> versionIds = appliedRules.stream().map(rule -> rule.getId().getRuleVersionId()).toList();
        Map<UUID, CareRuleVersion> versions = careRuleVersionRepository.findAllById(versionIds).stream()
                .collect(Collectors.toMap(CareRuleVersion::getId, Function.identity()));
        List<UUID> ruleIds = versions.values().stream().map(CareRuleVersion::getRuleId).distinct().toList();
        Map<UUID, CareRule> rules = careRuleRepository.findAllById(ruleIds).stream()
                .collect(Collectors.toMap(CareRule::getId, Function.identity()));

        LinkedHashSet<String> ruleCodes = new LinkedHashSet<>();
        LinkedHashSet<MatchReason> reasons = new LinkedHashSet<>();
        for (CareResultRule appliedRule : appliedRules) {
            CareRuleVersion version = versions.get(appliedRule.getId().getRuleVersionId());
            if (version == null || !version.getRuleSetId().equals(careResult.getRuleSetId())) {
                throw brokenInvariant("적용 규칙 버전과 규칙 세트가 일치하지 않습니다.");
            }
            CareRule rule = rules.get(version.getRuleId());
            if (rule == null) {
                throw brokenInvariant("적용 규칙 정의가 없습니다.");
            }
            ruleCodes.add(rule.getRuleCode());
            reasons.add(appliedRule.getMatchReason());
        }
        return new AppliedRuleData(List.copyOf(ruleCodes), ruleSet.getVersionCode(), List.copyOf(reasons));
    }

    private SimilarExperienceResponse toSimilarExperience(CareResult careResult, UUID userId) {
        UUID similarReportId = careResult.getSimilarReportId();
        Integer score = careResult.getSimilarityScore();
        if (similarReportId == null && score == null) {
            return null;
        }
        if (similarReportId == null || score == null) {
            throw brokenInvariant("유사 경험 ID와 점수가 함께 저장되지 않았습니다.");
        }

        SkinReport similarReport = skinReportRepository.findByIdAndUserId(similarReportId, userId)
                .orElseThrow(() -> brokenInvariant("현재 사용자에게 속한 유사 경험이 없습니다."));
        CareResult similarCareResult = careResultRepository.findByReportIdAndUserId(similarReportId, userId)
                .orElseThrow(() -> brokenInvariant("유사 경험의 관리 결과가 없습니다."));
        FollowUp similarFollowUp = followUpRepository.findByReportIdAndUserId(similarReportId, userId)
                .orElseThrow(() -> brokenInvariant("유사 경험의 경과가 없습니다."));

        return SimilarExperienceResponse.of(
                similarReportId,
                similarReport.getReportDate(),
                score,
                similarCareResult.getSummary(),
                similarFollowUp.getSkinChange()
        );
    }

    private void assertFollowUpMatchesStatus(SkinReport report, FollowUp followUp) {
        if (report.getStatus() == ReportStatus.COMPLETED && followUp == null) {
            throw brokenInvariant("완료된 피부 보고에 경과가 없습니다.");
        }
        if (report.getStatus() != ReportStatus.COMPLETED && followUp != null) {
            throw brokenInvariant("완료되지 않은 피부 보고에 경과가 연결되어 있습니다.");
        }
    }

    private void assertCareResultMatchesReport(SkinReport report, CareResult careResult) {
        if (careResult.getResultType() != report.getResultType()) {
            throw brokenInvariant("피부 보고와 관리 결과의 결과 유형이 일치하지 않습니다.");
        }
    }

    private SkinReportCursor toCursor(SkinReport report) {
        return new SkinReportCursor(report.getCreatedAt(), report.getId());
    }

    private <E extends Enum<E>> List<E> sorted(Collection<E> values) {
        return values.stream()
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .toList();
    }

    private IllegalStateException brokenInvariant(String message) {
        return new IllegalStateException(message);
    }

    private record AppliedRuleData(
            List<String> ruleCodes,
            String ruleSetVersion,
            List<MatchReason> reasonTags
    ) {
    }
}
