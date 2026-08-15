package likelion.flourishing.domain.report.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.home.entity.DailyCheckIn;
import likelion.flourishing.domain.home.repository.DailyCheckInRepository;
import likelion.flourishing.domain.report.crypto.ReportTextCipher;
import likelion.flourishing.domain.report.dto.request.ConfirmedSelectionsRequest;
import likelion.flourishing.domain.report.dto.request.CreateSkinReportRequest;
import likelion.flourishing.domain.report.dto.response.SkinReportCreatedResponse;
import likelion.flourishing.domain.report.dto.response.StructuredSelectionsResponse;
import likelion.flourishing.domain.report.entity.Appearance;
import likelion.flourishing.domain.report.entity.PreCareCheck;
import likelion.flourishing.domain.report.entity.ResultType;
import likelion.flourishing.domain.report.entity.Sensation;
import likelion.flourishing.domain.report.entity.Situation;
import likelion.flourishing.domain.report.entity.SkinReport;
import likelion.flourishing.domain.report.idempotency.IdempotencyService;
import likelion.flourishing.domain.report.idempotency.IdempotentResponse;
import likelion.flourishing.domain.report.repository.SkinReportRepository;
import likelion.flourishing.domain.report.rule.RuleEvaluationFacts;
import likelion.flourishing.domain.report.similarity.FoundSimilarExperience;
import likelion.flourishing.domain.report.similarity.ScoredSimilarExperience;
import likelion.flourishing.domain.report.similarity.SimilarExperienceFinder;
import likelion.flourishing.domain.report.similarity.SimilarExperienceLookup;
import likelion.flourishing.domain.report.similarity.SimilarExperienceQuery;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자가 확정한 보고를 저장하고 관리 결과를 함께 만든다.
 *
 * <p>보고와 결과를 한 트랜잭션에서 만든다. 결과 없는 보고가 남으면 기록 상세 조회가 불변식 위반이
 * 되고, 하루 한 건 제약 때문에 사용자가 그날 다시 보고할 수도 없다. 규칙이 준비되지 않아 503이
 * 나가는 경우에도 보고는 저장되지 않는다.
 *
 * <p>결과 유형과 보고 날짜는 요청에서 받지 않고 서버가 정한다. 저장하는 값은 사용자가 최종 확인한
 * 선택값뿐이다.
 *
 * <p>같은 Idempotency-Key로 같은 본문이 다시 오면 처음 만든 응답을 그대로 돌려준다. AI 호출과 결과
 * 생성이 붙어 있어 재실행되면 사용자에게 다른 결과가 두 개 생긴다.
 */
@Service
public class SkinReportSubmissionService {

    /** idempotency_records.operation_id에 남기는 작업 이름. */
    public static final String OPERATION_ID = "POST /v1/skin-reports";

    private final SkinReportRepository skinReportRepository;
    private final DailyCheckInRepository dailyCheckInRepository;
    private final ReportTextCipher reportTextCipher;
    private final SimilarExperienceFinder similarExperienceFinder;
    private final CareResultGenerator careResultGenerator;
    private final CareGuideResponseAssembler careGuideResponseAssembler;
    private final IdempotencyService idempotencyService;
    private final SensitiveDataConsentGuard consentGuard;
    private final Clock clock;

    public SkinReportSubmissionService(
            SkinReportRepository skinReportRepository,
            DailyCheckInRepository dailyCheckInRepository,
            ReportTextCipher reportTextCipher,
            SimilarExperienceFinder similarExperienceFinder,
            CareResultGenerator careResultGenerator,
            CareGuideResponseAssembler careGuideResponseAssembler,
            IdempotencyService idempotencyService,
            SensitiveDataConsentGuard consentGuard,
            Clock clock
    ) {
        this.skinReportRepository = skinReportRepository;
        this.dailyCheckInRepository = dailyCheckInRepository;
        this.reportTextCipher = reportTextCipher;
        this.similarExperienceFinder = similarExperienceFinder;
        this.careResultGenerator = careResultGenerator;
        this.careGuideResponseAssembler = careGuideResponseAssembler;
        this.idempotencyService = idempotencyService;
        this.consentGuard = consentGuard;
        this.clock = clock;
    }

    /**
     * 보고를 저장한다.
     *
     * <p>확인 순서는 동의 → 조합 검증 → 재전송 확인 → 하루 한 건 확인이다. 재전송을 하루 한 건보다
     * 먼저 보는 이유는, 처음 요청이 성공한 뒤 응답을 못 받아 다시 보낸 경우에 409가 아니라 처음
     * 응답이 나가야 하기 때문이다.
     */
    @Transactional
    public IdempotentResponse submit(
            AuthenticatedUser principal,
            UUID idempotencyKey,
            CreateSkinReportRequest request
    ) {
        UUID userId = principal.userId();
        consentGuard.assertConsented(userId);

        ConfirmedSelectionsRequest confirmed = request.confirmed();
        Set<Appearance> appearances = confirmed.appearanceSet();
        Set<Sensation> sensations = confirmed.sensationSet();
        Set<Situation> situations = confirmed.situationSet();
        Set<PreCareCheck> preCareChecks = request.preCareCheckSet();
        SkinReportPolicy.assertExclusiveSelections(appearances, sensations, situations, preCareChecks);

        Object fingerprint = fingerprintOf(request, appearances, sensations, situations, preCareChecks);
        Optional<IdempotentResponse> replay = idempotencyService
                .findReplay(userId, OPERATION_ID, idempotencyKey, fingerprint);
        if (replay.isPresent()) {
            return replay.get();
        }

        LocalDate reportDate = SkinReportPolicy.today(clock);
        if (skinReportRepository.existsByUserIdAndReportDate(userId, reportDate)) {
            throw new BusinessException(ErrorCode.REPORT_ALREADY_EXISTS);
        }

        ResultType resultType = SkinReportPolicy.decideResultType(preCareChecks);
        SimilarExperienceLookup lookup = similarExperienceFinder.lookup(userId, reportDate, new SimilarExperienceQuery(
                confirmed.primaryArea(), appearances, sensations, situations, resultType
        ));
        ScoredSimilarExperience similarExperience = lookup.found()
                .map(FoundSimilarExperience::scored)
                .orElse(null);

        SkinReport report = skinReportRepository.saveAndFlush(SkinReport.create(
                userId,
                reportDate,
                reportTextCipher.encrypt(request.rawText()),
                confirmed.primaryArea(),
                reportTextCipher.encrypt(trimToNull(confirmed.otherAreasNote())),
                confirmed.careAvailability(),
                resultType,
                SkinReportPolicy.followUpAvailableAt(reportDate),
                SkinReportPolicy.followUpExpiresAt(reportDate),
                appearances,
                sensations,
                situations,
                preCareChecks
        ));

        GeneratedCareResult generated = careResultGenerator.generate(
                report.getId(),
                userId,
                resultType,
                new RuleEvaluationFacts(
                        confirmed.primaryArea(),
                        appearances,
                        sensations,
                        situations,
                        confirmed.careAvailability(),
                        preCareChecks,
                        lookup.completedHistory()
                ),
                similarExperience
        );
        markDailyCheckIn(userId, reportDate, report.getId());

        SkinReportCreatedResponse response = toResponse(report, request, generated, lookup);
        IdempotentResponse created = IdempotentResponse.created(
                idempotencyService.serialize(response), report.getId()
        );
        idempotencyService.store(userId, OPERATION_ID, idempotencyKey, fingerprint, created);
        return created;
    }

    /**
     * 그날의 피부 점호를 보고 상태로 바꾼다.
     *
     * <p>"오늘 불편 없음"을 먼저 저장했더라도 나중에 확정한 보고가 그날의 상태다. 홈 화면이 보고와
     * 점호를 따로 보여 주면 사용자는 같은 날에 두 답을 한 것처럼 보게 된다.
     */
    private void markDailyCheckIn(UUID userId, LocalDate reportDate, UUID reportId) {
        dailyCheckInRepository.findByUserIdAndCheckInDate(userId, reportDate)
                .ifPresentOrElse(
                        checkIn -> checkIn.replaceWithSkinReport(reportId),
                        () -> dailyCheckInRepository.save(
                                DailyCheckIn.skinReport(userId, reportDate, reportId)
                        )
                );
    }

    private SkinReportCreatedResponse toResponse(
            SkinReport report,
            CreateSkinReportRequest request,
            GeneratedCareResult generated,
            SimilarExperienceLookup lookup
    ) {
        StructuredSelectionsResponse confirmed = StructuredSelectionsResponse.of(
                report.getPrimaryArea(),
                trimToNull(request.confirmed().otherAreasNote()),
                sorted(report.getAppearances()),
                sorted(report.getSensations()),
                sorted(report.getSituations()),
                report.getCareAvailability()
        );

        return SkinReportCreatedResponse.of(
                report.getId(),
                report.getReportDate(),
                report.getResultType(),
                report.getStatus(),
                request.rawText(),
                confirmed,
                sorted(report.getPreCareChecks()),
                careGuideResponseAssembler.assemble(
                        generated.careResult(),
                        generated.ruleVersion(),
                        generated.appliedRules(),
                        generated.items(),
                        lookup.found().map(FoundSimilarExperience::response).orElse(null)
                ),
                report.getFollowUpAvailableAt().atOffset(ZoneOffset.UTC),
                report.getFollowUpExpiresAt().atOffset(ZoneOffset.UTC),
                createdAtOf(report).atOffset(ZoneOffset.UTC)
        );
    }

    /**
     * 재전송 판단에 쓸 본문 지문.
     *
     * <p>다중 선택은 정렬해서 담는다. 같은 값을 다른 순서로 보낸 요청은 뜻이 같으므로 같은 지문이
     * 나와야 한다. 순서까지 따지면 네트워크 재시도가 409로 막힌다.
     */
    private Object fingerprintOf(
            CreateSkinReportRequest request,
            Set<Appearance> appearances,
            Set<Sensation> sensations,
            Set<Situation> situations,
            Set<PreCareCheck> preCareChecks
    ) {
        ConfirmedSelectionsRequest confirmed = request.confirmed();
        return new SubmissionFingerprint(
                request.rawText().trim(),
                confirmed.primaryArea(),
                trimToNull(confirmed.otherAreasNote()),
                names(appearances),
                names(sensations),
                names(situations),
                confirmed.careAvailability(),
                names(preCareChecks)
        );
    }

    private LocalDateTime createdAtOf(SkinReport report) {
        return report.getCreatedAt() == null ? LocalDateTime.now(clock) : report.getCreatedAt();
    }

    private <E extends Enum<E>> List<E> sorted(Set<E> values) {
        return values.stream().sorted(Comparator.comparingInt(Enum::ordinal)).toList();
    }

    private <E extends Enum<E>> List<String> names(Set<E> values) {
        return sorted(values).stream().map(Enum::name).toList();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 해시 대상이 되는 정규화된 본문. 필드 순서가 곧 직렬화 순서라 값이 같으면 항상 같은 해시가 된다. */
    private record SubmissionFingerprint(
            String rawText,
            Enum<?> primaryArea,
            String otherAreasNote,
            List<String> appearances,
            List<String> sensations,
            List<String> situations,
            Enum<?> careAvailability,
            List<String> preCareChecks
    ) {
    }
}
