package likelion.flourishing.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.report.crypto.RecordCryptoProperties;
import likelion.flourishing.domain.report.crypto.ReportTextCipher;
import likelion.flourishing.domain.report.dto.request.ConfirmedSelectionsRequest;
import likelion.flourishing.domain.report.dto.request.CreateSkinReportRequest;
import likelion.flourishing.domain.report.entity.AiGenerationStatus;
import likelion.flourishing.domain.report.entity.Appearance;
import likelion.flourishing.domain.report.entity.BodyArea;
import likelion.flourishing.domain.report.entity.CareAvailability;
import likelion.flourishing.domain.report.entity.CareResult;
import likelion.flourishing.domain.report.entity.PreCareCheck;
import likelion.flourishing.domain.report.entity.ResultType;
import likelion.flourishing.domain.report.entity.Sensation;
import likelion.flourishing.domain.report.entity.Situation;
import likelion.flourishing.domain.report.entity.SkinReport;
import likelion.flourishing.domain.report.idempotency.IdempotencyService;
import likelion.flourishing.domain.report.idempotency.IdempotentResponse;
import likelion.flourishing.domain.report.repository.SkinReportRepository;
import likelion.flourishing.domain.report.rule.CareRuleFixtures;
import likelion.flourishing.domain.report.similarity.SimilarExperienceFinder;
import likelion.flourishing.domain.report.similarity.SimilarExperienceLookup;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * 보고 생성 서비스 테스트.
 *
 * <p>확인하는 것: 결과 유형과 날짜를 서버가 정하는지, 같은 날 두 번째 보고를 409로 막는지,
 * 재전송을 하루 한 건 검사보다 먼저 보는지, 규칙 조회와 AI 호출을 저장보다 먼저 끝내는지,
 * 규칙이 없어 503이 날 때 저장을 시작하지 않는지.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SkinReportSubmissionServiceTest {

    private static final UUID USER_ID = UUID.fromString("2c56fe08-ea1f-45fc-915d-c35b7c0bca39");
    private static final UUID SESSION_ID = UUID.fromString("5ecb88d8-6a21-4a54-8967-72599f078963");
    private static final UUID IDEMPOTENCY_KEY = UUID.fromString("11111111-2222-4333-8444-555555555555");
    private static final UUID RULE_SET_ID = UUID.fromString("0198a31f-f33f-7000-8000-000000000901");
    private static final Instant NOW = Instant.parse("2026-08-15T03:00:00Z");
    private static final LocalDate TODAY_IN_SEOUL = LocalDate.of(2026, 8, 15);

    @Mock
    private SkinReportRepository skinReportRepository;

    @Mock
    private SimilarExperienceFinder similarExperienceFinder;

    @Mock
    private CareResultGenerator careResultGenerator;

    @Mock
    private SkinReportWriter skinReportWriter;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private SensitiveDataConsentGuard consentGuard;

    private SkinReportSubmissionService service;

    @BeforeEach
    void setUp() {
        ReportTextCipher cipher = new ReportTextCipher(new RecordCryptoProperties(
                "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
        ));
        service = new SkinReportSubmissionService(
                skinReportRepository,
                cipher,
                similarExperienceFinder,
                careResultGenerator,
                new CareGuideResponseAssembler(),
                skinReportWriter,
                idempotencyService,
                consentGuard,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        when(skinReportRepository.existsByUserIdAndReportDate(any(), any())).thenReturn(false);
        when(similarExperienceFinder.lookup(any(), any(), any())).thenReturn(SimilarExperienceLookup.empty());
        when(idempotencyService.findReplay(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(idempotencyService.serialize(any())).thenAnswer(call -> serialize(call.getArgument(0)));
        when(careResultGenerator.plan(any(), any()))
                .thenAnswer(call -> plan(call.getArgument(0)));
        when(skinReportWriter.write(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(call -> {
                    SkinReport report = call.getArgument(4);
                    CareResultPlan plan = call.getArgument(5);
                    SkinReportWriter.SubmissionResponseFactory factory = call.getArgument(7);
                    Object body = factory.create(report, generated(report.getId(), plan));
                    return IdempotentResponse.created(serialize(body), report.getId());
                });
    }

    @Test
    void serverDecidesReportDateAndResultTypeAndReturnsCreated() {
        IdempotentResponse response = service.submit(principal(), IDEMPOTENCY_KEY, request(
                List.of(PreCareCheck.NONE)
        ));

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.replayed()).isFalse();
        assertThat(response.jsonBody()).contains("\"resultType\":\"SELF_CARE_GUIDE\"");
        assertThat(response.jsonBody()).contains("\"reportDate\":\"2026-08-15\"");

        SkinReport written = capturedReport();
        assertThat(written.getReportDate()).isEqualTo(TODAY_IN_SEOUL);
        assertThat(written.getResultType()).isEqualTo(ResultType.SELF_CARE_GUIDE);
        assertThat(written.getFollowUpAvailableAt()).isEqualTo(LocalDateTime.of(2026, 8, 15, 15, 0));
        assertThat(written.getFollowUpExpiresAt()).isEqualTo(LocalDateTime.of(2026, 8, 17, 15, 0));
    }

    @Test
    void riskSignalIsPlannedAsClinicianCheck() {
        service.submit(principal(), IDEMPOTENCY_KEY, request(List.of(PreCareCheck.SPREADING_RAPIDLY)));

        assertThat(capturedReport().getResultType()).isEqualTo(ResultType.CLINICIAN_CHECK);
        verify(careResultGenerator).plan(eq(ResultType.CLINICIAN_CHECK), any());
    }

    @Test
    void rawTextIsHandedToTheWriterOnlyAsCiphertext() {
        service.submit(principal(), IDEMPOTENCY_KEY, request(List.of(PreCareCheck.NONE)));

        assertThat(new String(capturedReport().getRawTextEncrypted())).doesNotContain("턱");
    }

    @Test
    void secondReportOnTheSameDayIsRejected() {
        when(skinReportRepository.existsByUserIdAndReportDate(USER_ID, TODAY_IN_SEOUL)).thenReturn(true);

        assertThatThrownBy(() -> service.submit(principal(), IDEMPOTENCY_KEY, request(List.of(PreCareCheck.NONE))))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.REPORT_ALREADY_EXISTS);
        verifyNothingWritten();
    }

    /** 처음 요청이 성공한 뒤 응답을 못 받아 다시 보낸 경우는 409가 아니라 처음 응답이 나가야 한다. */
    @Test
    void replayIsCheckedBeforeTheOneReportPerDayRule() {
        when(skinReportRepository.existsByUserIdAndReportDate(USER_ID, TODAY_IN_SEOUL)).thenReturn(true);
        IdempotentResponse stored = IdempotentResponse.replay(201, "{\"id\":\"stored\"}", null);
        when(idempotencyService.findReplay(any(), any(), any(), any())).thenReturn(Optional.of(stored));

        IdempotentResponse response = service.submit(
                principal(), IDEMPOTENCY_KEY, request(List.of(PreCareCheck.NONE))
        );

        assertThat(response).isEqualTo(stored);
        verifyNothingWritten();
    }

    /** 24시간 보관이라 날짜가 지문에 없으면 자정을 넘긴 재시도가 어제 응답을 되돌려 준다. */
    @Test
    void fingerprintIncludesTheReportDate() {
        service.submit(principal(), IDEMPOTENCY_KEY, request(List.of(PreCareCheck.NONE)));

        ArgumentCaptor<Object> fingerprint = ArgumentCaptor.forClass(Object.class);
        verify(idempotencyService).findReplay(any(), any(), any(), fingerprint.capture());
        assertThat(serialize(fingerprint.getValue())).contains("2026-08-15");
    }

    @Test
    void invalidSelectionCombinationIsRejectedBeforeAnyLookup() {
        CreateSkinReportRequest invalid = new CreateSkinReportRequest(
                "오른쪽 턱이 빨개요.",
                new ConfirmedSelectionsRequest(
                        BodyArea.RIGHT_CHIN,
                        null,
                        List.of(Appearance.REDNESS),
                        List.of(Sensation.NONE, Sensation.ITCHING),
                        List.of(Situation.SHAVING),
                        CareAvailability.ALREADY_WASHED
                ),
                List.of(PreCareCheck.NONE)
        );

        assertThatThrownBy(() -> service.submit(principal(), IDEMPOTENCY_KEY, invalid))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SELECTION_COMBINATION_INVALID);
        verify(idempotencyService, never()).findReplay(any(), any(), any(), any());
        verifyNothingWritten();
    }

    @Test
    void unavailableRulesStopTheRequestBeforeWriting() {
        when(careResultGenerator.plan(any(), any()))
                .thenThrow(new BusinessException(ErrorCode.RULE_ENGINE_UNAVAILABLE));

        assertThatThrownBy(() -> service.submit(principal(), IDEMPOTENCY_KEY, request(List.of(PreCareCheck.NONE))))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RULE_ENGINE_UNAVAILABLE);
        verifyNothingWritten();
    }

    /** 외부 호출이 쓰기 트랜잭션 안에 들어가면 보고 유니크 인덱스 락을 응답이 올 때까지 붙잡는다. */
    @Test
    void rulesAndModelAreResolvedBeforeTheWritePhase() {
        service.submit(principal(), IDEMPOTENCY_KEY, request(List.of(PreCareCheck.NONE)));

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(careResultGenerator, skinReportWriter);
        order.verify(careResultGenerator).plan(any(), any());
        order.verify(skinReportWriter).write(any(), any(), any(), any(), any(), any(), any(), any());
    }

    private SkinReport capturedReport() {
        ArgumentCaptor<SkinReport> report = ArgumentCaptor.forClass(SkinReport.class);
        verify(skinReportWriter).write(
                any(), any(), any(), any(), report.capture(), any(), any(), any()
        );
        return report.getValue();
    }

    private void verifyNothingWritten() {
        verify(skinReportWriter, never()).write(any(), any(), any(), any(), any(), any(), any(), any());
    }

    private CareResultPlan plan(ResultType resultType) {
        return new CareResultPlan(
                RULE_SET_ID,
                "2026-08-15-v1",
                resultType,
                resultType == ResultType.CLINICIAN_CHECK
                        ? AiGenerationStatus.NOT_APPLICABLE
                        : AiGenerationStatus.GENERATED,
                "오늘은 자극을 줄여 주세요.",
                resultType == ResultType.CLINICIAN_CHECK ? "가까운 의료기관에서 확인해 주세요." : null,
                List.of(CareRuleFixtures.commonRule()),
                List.of()
        );
    }

    private GeneratedCareResult generated(UUID reportId, CareResultPlan plan) {
        CareResult careResult = plan.resultType() == ResultType.CLINICIAN_CHECK
                ? CareResult.clinicianCheck(
                        reportId,
                        USER_ID,
                        RULE_SET_ID,
                        null,
                        null,
                        plan.summary(),
                        plan.clinicianMessage(),
                        LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)
                )
                : CareResult.selfCareGuide(
                        reportId,
                        USER_ID,
                        RULE_SET_ID,
                        null,
                        null,
                        plan.aiGenerationStatus(),
                        plan.summary(),
                        LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)
                );
        return new GeneratedCareResult(careResult, plan.ruleVersion(), plan.matchedRules(), plan.items());
    }

    private CreateSkinReportRequest request(List<PreCareCheck> preCareChecks) {
        return new CreateSkinReportRequest(
                "오른쪽 턱이 빨갛고 따가워요.",
                new ConfirmedSelectionsRequest(
                        BodyArea.RIGHT_CHIN,
                        null,
                        List.of(Appearance.REDNESS),
                        List.of(Sensation.STINGING_BURNING),
                        List.of(Situation.SHAVING),
                        CareAvailability.ALREADY_WASHED
                ),
                preCareChecks
        );
    }

    /** 애플리케이션과 같은 설정의 ObjectMapper를 써야 날짜가 ISO 문자열로 나간다. */
    private String serialize(Object body) {
        try {
            return Jackson2ObjectMapperBuilder.json()
                    .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .build()
                    .writeValueAsString(body);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private AuthenticatedUser principal() {
        return new AuthenticatedUser(
                USER_ID, SESSION_ID, LocalDateTime.of(2026, 8, 24, 0, 0), "csrf-token-value-that-is-long-enough"
        );
    }
}
