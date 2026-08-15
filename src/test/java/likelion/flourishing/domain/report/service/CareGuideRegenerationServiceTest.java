package likelion.flourishing.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
import java.util.Set;
import java.util.UUID;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.report.ai.AiFailureCode;
import likelion.flourishing.domain.report.ai.CareGuideNarrationPort;
import likelion.flourishing.domain.report.ai.NarrationOutcome;
import likelion.flourishing.domain.report.entity.AiGenerationStatus;
import likelion.flourishing.domain.report.entity.Appearance;
import likelion.flourishing.domain.report.entity.BodyArea;
import likelion.flourishing.domain.report.entity.CareAvailability;
import likelion.flourishing.domain.report.entity.CareResult;
import likelion.flourishing.domain.report.entity.CareResultItem;
import likelion.flourishing.domain.report.entity.CareResultItemType;
import likelion.flourishing.domain.report.entity.CareResultRule;
import likelion.flourishing.domain.report.entity.MatchReason;
import likelion.flourishing.domain.report.entity.PreCareCheck;
import likelion.flourishing.domain.report.entity.ResultType;
import likelion.flourishing.domain.report.entity.Sensation;
import likelion.flourishing.domain.report.entity.Situation;
import likelion.flourishing.domain.report.entity.SkinReport;
import likelion.flourishing.domain.report.idempotency.IdempotencyService;
import likelion.flourishing.domain.report.idempotency.IdempotentResponse;
import likelion.flourishing.domain.report.repository.CareResultItemRepository;
import likelion.flourishing.domain.report.repository.CareResultRepository;
import likelion.flourishing.domain.report.repository.CareResultRuleRepository;
import likelion.flourishing.domain.report.repository.SkinReportRepository;
import likelion.flourishing.domain.report.rule.AppliedRuleSet;
import likelion.flourishing.domain.report.rule.CareRuleCatalogPort;
import likelion.flourishing.domain.report.rule.CareRuleFixtures;
import likelion.flourishing.domain.report.similarity.SimilarExperienceFinder;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * 관리 설명 재생성 테스트.
 *
 * <p>확인하는 것: 남의 보고를 404로 막는지, 대체 문구가 아닌 결과를 422로 막는지, 이미 쓴 재생성을
 * 409로 막는지, 성공하면 항목을 갈아 끼우고 실패하면 그대로 두는지, 어느 쪽이든 기회를 쓴 것으로
 * 표시하는지, 규칙을 다시 판단하지 않고 당시 적용 규칙을 쓰는지, 조건부 갱신에서 밀린 요청이
 * 409가 되는지.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CareGuideRegenerationServiceTest {

    private static final UUID USER_ID = UUID.fromString("2c56fe08-ea1f-45fc-915d-c35b7c0bca39");
    private static final UUID SESSION_ID = UUID.fromString("5ecb88d8-6a21-4a54-8967-72599f078963");
    private static final UUID REPORT_ID = UUID.fromString("0198a31f-f33f-7000-8000-000000000011");
    private static final UUID RULE_SET_ID = UUID.fromString("0198a31f-f33f-7000-8000-000000000901");
    private static final Instant NOW = Instant.parse("2026-08-15T06:00:00Z");
    private static final String FALLBACK_SUMMARY = "오늘은 자극을 줄이고 상태를 지켜봐 주세요.";

    @Mock
    private SkinReportRepository skinReportRepository;

    @Mock
    private CareResultRepository careResultRepository;

    @Mock
    private CareResultRuleRepository careResultRuleRepository;

    @Mock
    private CareResultItemRepository careResultItemRepository;

    @Mock
    private CareRuleCatalogPort careRuleCatalogPort;

    @Mock
    private CareGuideNarrationPort narrationPort;

    @Mock
    private CareGuideRewriter careGuideRewriter;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private SensitiveDataConsentGuard consentGuard;

    @Mock
    private SimilarExperienceFinder similarExperienceFinder;

    private CareGuideRegenerationService service;

    @BeforeEach
    void setUp() {
        service = new CareGuideRegenerationService(
                skinReportRepository,
                careResultRepository,
                careResultRuleRepository,
                careResultItemRepository,
                careRuleCatalogPort,
                narrationPort,
                new CareGuideItemPlanner(),
                careGuideRewriter,
                new CareGuideResponseAssembler(),
                similarExperienceFinder,
                idempotencyService,
                consentGuard,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        when(skinReportRepository.findWithSelectionsByIdAndUserId(REPORT_ID, USER_ID)).thenReturn(Optional.of(report()));
        stubCareResult(selfCareResult(AiGenerationStatus.FALLBACK));
        when(careResultRuleRepository.findAllByIdCareResultIdOrderByApplicationOrder(any()))
                .thenReturn(List.of(CareResultRule.of(
                        UUID.randomUUID(),
                        CareRuleFixtures.rednessRule().ruleVersionId(),
                        1,
                        MatchReason.CURRENT_STATE
                )));
        when(careRuleCatalogPort.loadAppliedRules(any(), any())).thenReturn(Optional.of(new AppliedRuleSet(
                "2026-08-15-v1", List.of(CareRuleFixtures.rednessRule(), CareRuleFixtures.commonRule())
        )));
        when(careResultItemRepository.findAllByCareResultIdOrderByItemTypeAscDisplayOrderAsc(any()))
                .thenReturn(List.of(CareResultItem.snapshot(
                        UUID.randomUUID(),
                        null,
                        CareResultItemType.DO_TODAY,
                        "미지근한 물로 씻기",
                        1
                )));
        when(idempotencyService.serialize(any())).thenAnswer(call -> serialize(call.getArgument(0)));
        when(idempotencyService.findReplay(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(careGuideRewriter.rewrite(any(), any(), any(), any(), anyList()))
                .thenAnswer(call -> Optional.of(rewritten(call.getArgument(1), call.getArgument(2))));
    }

    @Test
    void otherUsersReportIsNotFound() {
        when(skinReportRepository.findWithSelectionsByIdAndUserId(REPORT_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.regenerate(principal(), REPORT_ID, null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        verify(narrationPort, never()).narrate(any());
    }

    @Test
    void generatedResultCannotBeRegenerated() {
        stubCareResult(selfCareResult(AiGenerationStatus.GENERATED));

        assertThatThrownBy(() -> service.regenerate(principal(), REPORT_ID, null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AI_RETRY_NOT_AVAILABLE);
    }

    @Test
    void clinicianCheckResultCannotBeRegenerated() {
        stubCareResult(CareResult.clinicianCheck(
                REPORT_ID,
                USER_ID,
                RULE_SET_ID,
                null,
                null,
                "의료진 확인이 필요합니다.",
                "가까운 의료기관에서 확인해 주세요.",
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)
        ));

        assertThatThrownBy(() -> service.regenerate(principal(), REPORT_ID, null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AI_RETRY_NOT_AVAILABLE);
    }

    @Test
    void alreadyUsedRetryIsRejected() {
        CareResult careResult = selfCareResult(AiGenerationStatus.FALLBACK);
        careResult.applyRegeneratedGuide(
                AiGenerationStatus.FALLBACK, FALLBACK_SUMMARY, LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)
        );
        stubCareResult(careResult);

        assertThatThrownBy(() -> service.regenerate(principal(), REPORT_ID, null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AI_RETRY_ALREADY_USED);
        verify(narrationPort, never()).narrate(any());
    }

    /** 재생성이 성공하면 상태가 GENERATED로 바뀐다. 그때 답은 "대상 아님"이 아니라 "이미 썼음"이다. */
    @Test
    void regeneratedResultReportsRetryUsedRatherThanNotAvailable() {
        CareResult careResult = selfCareResult(AiGenerationStatus.FALLBACK);
        careResult.applyRegeneratedGuide(
                AiGenerationStatus.GENERATED, "다시 만든 설명", LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)
        );
        stubCareResult(careResult);

        assertThatThrownBy(() -> service.regenerate(principal(), REPORT_ID, null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AI_RETRY_ALREADY_USED);
    }

    /** 조건부 갱신에서 밀린 요청은 다른 요청이 기회를 가져간 것이다. */
    @Test
    void losingTheConditionalUpdateIsReportedAsAlreadyUsed() {
        stubSuccessfulNarration();
        when(careGuideRewriter.rewrite(any(), any(), any(), any(), anyList())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.regenerate(principal(), REPORT_ID, null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AI_RETRY_ALREADY_USED);
    }

    @Test
    void successfulRegenerationReplacesItemsAndMarksRetryUsed() {
        stubSuccessfulNarration();

        IdempotentResponse response = service.regenerate(principal(), REPORT_ID, null);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.jsonBody()).contains("\"retryUsed\":true");
        assertThat(response.jsonBody()).contains("찬 물수건으로 진정하기");
        assertThat(response.jsonBody()).contains("\"aiGenerationStatus\":\"GENERATED\"");
        verify(careGuideRewriter).rewrite(
                any(),
                eq(AiGenerationStatus.GENERATED),
                eq("붉은 자리를 건드리지 않고 진정에 집중해 주세요."),
                any(),
                anyList()
        );
    }

    @Test
    void failedRegenerationKeepsFallbackAndStillConsumesTheRetry() {
        when(narrationPort.narrate(any())).thenReturn(NarrationOutcome.failed(AiFailureCode.AI_TIMEOUT));

        IdempotentResponse response = service.regenerate(principal(), REPORT_ID, null);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.jsonBody()).contains("\"aiGenerationStatus\":\"FALLBACK\"");
        assertThat(response.jsonBody()).contains("미지근한 물로 씻기");
        verify(careGuideRewriter).rewrite(
                any(), eq(AiGenerationStatus.FALLBACK), eq(FALLBACK_SUMMARY), any(), eq(List.of())
        );
    }

    @Test
    void contentOutsideTheOriginalAllowListIsNotStored() {
        when(narrationPort.narrate(any())).thenReturn(NarrationOutcome.succeeded(
                "연고를 바르세요.", List.of("스테로이드 연고 바르기"), List.of(), List.of()
        ));

        service.regenerate(principal(), REPORT_ID, null);

        verify(careGuideRewriter).rewrite(
                any(), eq(AiGenerationStatus.FALLBACK), eq(FALLBACK_SUMMARY), any(), eq(List.of())
        );
    }

    @Test
    void brokenAppliedRuleSnapshotStopsRegeneration() {
        when(careRuleCatalogPort.loadAppliedRules(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.regenerate(principal(), REPORT_ID, null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RULE_ENGINE_UNAVAILABLE);
    }

    /** 고를 문구가 없으면 실패로 처리해 단 한 번의 기회를 쓰게 하지 않는다. */
    @Test
    void noAllowedActionStopsRegenerationWithoutConsumingTheRetry() {
        when(careRuleCatalogPort.loadAppliedRules(any(), any())).thenReturn(Optional.of(new AppliedRuleSet(
                "2026-08-15-v1", List.of(CareRuleFixtures.ruleWithoutActions())
        )));

        assertThatThrownBy(() -> service.regenerate(principal(), REPORT_ID, null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RULE_ENGINE_UNAVAILABLE);
        verify(narrationPort, never()).narrate(any());
        verify(careGuideRewriter, never()).rewrite(any(), any(), any(), any(), anyList());
    }

    @Test
    void activeCatalogIsNotConsultedForRegeneration() {
        when(narrationPort.narrate(any())).thenReturn(NarrationOutcome.failed(AiFailureCode.AI_HTTP_ERROR));

        service.regenerate(principal(), REPORT_ID, null);

        verify(careRuleCatalogPort, never()).loadActiveCatalog();
        verify(careRuleCatalogPort).loadAppliedRules(RULE_SET_ID, List.of(
                CareRuleFixtures.rednessRule().ruleVersionId()
        ));
    }

    @Test
    void idempotencyKeyReplaysStoredResponse() {
        UUID key = UUID.fromString("11111111-2222-4333-8444-555555555555");
        IdempotentResponse stored = IdempotentResponse.replay(200, "{\"summary\":\"stored\"}", REPORT_ID);
        when(idempotencyService.findReplay(any(), any(), any(), any())).thenReturn(Optional.of(stored));

        assertThat(service.regenerate(principal(), REPORT_ID, key)).isEqualTo(stored);
        verify(narrationPort, never()).narrate(any());
    }

    private void stubSuccessfulNarration() {
        when(narrationPort.narrate(any())).thenReturn(NarrationOutcome.succeeded(
                "붉은 자리를 건드리지 않고 진정에 집중해 주세요.",
                List.of("찬 물수건으로 진정하기"),
                List.of("각질 제거하지 않기"),
                List.of()
        ));
    }

    private void stubCareResult(CareResult careResult) {
        when(careResultRepository.findByReportIdAndUserId(REPORT_ID, USER_ID))
                .thenReturn(Optional.of(careResult));
    }

    /** 조건부 갱신을 통과한 뒤 다시 읽은 결과. */
    private CareResult rewritten(AiGenerationStatus aiGenerationStatus, String summary) {
        CareResult careResult = selfCareResult(AiGenerationStatus.FALLBACK);
        careResult.applyRegeneratedGuide(
                aiGenerationStatus, summary, LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)
        );
        return careResult;
    }

    private CareResult selfCareResult(AiGenerationStatus aiGenerationStatus) {
        return CareResult.selfCareGuide(
                REPORT_ID,
                USER_ID,
                RULE_SET_ID,
                null,
                null,
                aiGenerationStatus,
                FALLBACK_SUMMARY,
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)
        );
    }

    private SkinReport report() {
        return SkinReport.create(
                USER_ID,
                LocalDate.of(2026, 8, 15),
                new byte[]{1},
                BodyArea.RIGHT_CHIN,
                null,
                CareAvailability.ALREADY_WASHED,
                ResultType.SELF_CARE_GUIDE,
                LocalDateTime.of(2026, 8, 15, 15, 0),
                LocalDateTime.of(2026, 8, 17, 15, 0),
                Set.of(Appearance.REDNESS),
                Set.of(Sensation.STINGING_BURNING),
                Set.of(Situation.SHAVING),
                Set.of(PreCareCheck.NONE)
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
