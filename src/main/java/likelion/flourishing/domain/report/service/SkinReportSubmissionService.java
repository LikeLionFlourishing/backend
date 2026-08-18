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

/**
 * 사용자가 확정한 보고를 저장하고 관리 결과를 함께 만든다.
 *
 * <p>이 메서드에는 트랜잭션을 걸지 않는다. 규칙 조회와 AI 호출까지 끝낸 다음 저장만
 * {@link SkinReportWriter}에 맡긴다. 외부 호출을 쓰기 트랜잭션 안에 두면 보고 유니크 인덱스 락과
 * DB 커넥션을 응답이 올 때까지 붙잡아, 같은 사용자의 다음 요청이 그만큼 기다리고 커넥션 풀도
 * 빨리 마른다.
 *
 * <p>저장은 한 트랜잭션에서 함께 끝난다. 규칙이 준비되지 않아 503이 나가는 경우는 저장을 시작하기
 * 전이라 아무것도 남지 않는다.
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
    private final ReportTextCipher reportTextCipher;
    private final SimilarExperienceFinder similarExperienceFinder;
    private final CareResultGenerator careResultGenerator;
    private final CareGuideResponseAssembler careGuideResponseAssembler;
    private final SkinReportWriter skinReportWriter;
    private final IdempotencyService idempotencyService;
    private final SensitiveDataConsentGuard consentGuard;
    private final Clock clock;

    public SkinReportSubmissionService(
            SkinReportRepository skinReportRepository,
            ReportTextCipher reportTextCipher,
            SimilarExperienceFinder similarExperienceFinder,
            CareResultGenerator careResultGenerator,
            CareGuideResponseAssembler careGuideResponseAssembler,
            SkinReportWriter skinReportWriter,
            IdempotencyService idempotencyService,
            SensitiveDataConsentGuard consentGuard,
            Clock clock
    ) {
        this.skinReportRepository = skinReportRepository;
        this.reportTextCipher = reportTextCipher;
        this.similarExperienceFinder = similarExperienceFinder;
        this.careResultGenerator = careResultGenerator;
        this.careGuideResponseAssembler = careGuideResponseAssembler;
        this.skinReportWriter = skinReportWriter;
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
        SkinReportPolicy.assertExclusiveSelections(situations, preCareChecks);

        LocalDate reportDate = SkinReportPolicy.today(clock);
        Object fingerprint = fingerprintOf(
                request, reportDate, appearances, sensations, situations, preCareChecks
        );
        Optional<IdempotentResponse> replay = idempotencyService
                .findReplay(userId, OPERATION_ID, idempotencyKey, fingerprint);
        if (replay.isPresent()) {
            return replay.get();
        }

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

        // 규칙 조회와 AI 호출은 여기서 끝낸다. 아래 저장 단계는 외부 응답을 기다리지 않는다.
        CareResultPlan plan = careResultGenerator.plan(resultType, new RuleEvaluationFacts(
                confirmed.primaryArea(),
                appearances,
                sensations,
                situations,
                confirmed.careAvailability(),
                preCareChecks,
                lookup.completedHistory()
        ));

        SkinReport report = SkinReport.create(
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
        );

        try {
            return skinReportWriter.write(
                    userId,
                    OPERATION_ID,
                    idempotencyKey,
                    fingerprint,
                    report,
                    plan,
                    similarExperience,
                    (savedReport, generated) -> toResponse(savedReport, request, generated, lookup)
            );
        } catch (BusinessException exception) {
            return replayAfterConflict(userId, idempotencyKey, fingerprint, exception);
        }
    }

    /**
     * 하루 한 건 제약에 걸린 뒤, 같은 키의 다른 요청이 먼저 저장한 것인지 확인한다.
     *
     * <p>재전송 확인과 저장 사이에 같은 키의 요청이 겹치면 둘 다 처음 조회에서 빈 값을 본다. 뒤늦은
     * 쪽은 (user_id, report_date) 유니크 제약에 걸리는데, 같은 키라면 명세대로 처음 응답이 나가야 하고
     * 409는 맞지 않다. 쓰기 트랜잭션이 되돌아간 뒤 다시 읽으면 먼저 커밋된 멱등 기록이 보인다.
     *
     * <p>키가 다른 요청이 같은 날 두 번째 보고를 시도한 경우에는 기록이 없어 원래 409가 그대로 나간다.
     */
    private IdempotentResponse replayAfterConflict(
            UUID userId,
            UUID idempotencyKey,
            Object fingerprint,
            BusinessException conflict
    ) {
        if (conflict.getErrorCode() != ErrorCode.REPORT_ALREADY_EXISTS) {
            throw conflict;
        }
        return idempotencyService.findReplay(userId, OPERATION_ID, idempotencyKey, fingerprint)
                .orElseThrow(() -> conflict);
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
                        generated.ingredients(),
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
     *
     * <p>보고 날짜도 함께 담는다. 기록은 24시간 보관하므로 자정을 넘겨 같은 키로 재시도하면 어제
     * 응답이 그대로 나갈 수 있다. 날짜가 지문에 있으면 다른 요청으로 보아 409로 막힌다.
     */
    private Object fingerprintOf(
            CreateSkinReportRequest request,
            LocalDate reportDate,
            Set<Appearance> appearances,
            Set<Sensation> sensations,
            Set<Situation> situations,
            Set<PreCareCheck> preCareChecks
    ) {
        ConfirmedSelectionsRequest confirmed = request.confirmed();
        return new SubmissionFingerprint(
                reportDate.toString(),
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
            String reportDate,
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
