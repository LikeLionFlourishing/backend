package likelion.flourishing.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import likelion.flourishing.domain.report.ai.AiFailureCode;
import likelion.flourishing.domain.report.ai.CareGuideNarrationPort;
import likelion.flourishing.domain.report.ai.NarrationOutcome;
import likelion.flourishing.domain.report.entity.AiGenerationStatus;
import likelion.flourishing.domain.report.entity.CareResultItemType;
import likelion.flourishing.domain.report.entity.MatchReason;
import likelion.flourishing.domain.report.entity.ResultType;
import likelion.flourishing.domain.report.entity.RuleActionType;
import likelion.flourishing.domain.report.entity.RuleCategory;
import likelion.flourishing.domain.report.repository.CareResultIngredientRepository;
import likelion.flourishing.domain.report.repository.CareResultIngredientRuleRepository;
import likelion.flourishing.domain.report.repository.CareResultItemRepository;
import likelion.flourishing.domain.report.repository.CareResultRepository;
import likelion.flourishing.domain.report.repository.CareResultRuleRepository;
import likelion.flourishing.domain.report.rule.CareRuleCatalogPort;
import likelion.flourishing.domain.report.rule.CareRuleEngine;
import likelion.flourishing.domain.report.rule.CareRuleFixtures;
import likelion.flourishing.domain.report.rule.CareRuleSnapshot;
import likelion.flourishing.domain.report.rule.RuleActionSnapshot;
import likelion.flourishing.domain.report.similarity.ScoredSimilarExperience;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 관리 결과 결정과 저장 테스트. 테스트 전용 규칙 fixture만 쓴다.
 *
 * <p>확인하는 것: 활성 규칙이 없으면 결과를 만들지 않고 503을 내는지, AI 성공과 실패가 각각
 * GENERATED와 FALLBACK이 되는지, 의료진 확인은 AI를 부르지 않는지, 승인된 문구가 없거나 컬럼에
 * 넣을 수 없는 길이면 결과를 만들지 않는지, 적용 규칙 순서가 남는지.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CareResultGeneratorTest {

    private static final UUID REPORT_ID = UUID.fromString("0198a31f-f33f-7000-8000-000000000011");
    private static final UUID USER_ID = UUID.fromString("2c56fe08-ea1f-45fc-915d-c35b7c0bca39");
    private static final Instant NOW = Instant.parse("2026-08-15T03:00:00Z");

    @Mock
    private CareRuleCatalogPort careRuleCatalogPort;

    @Mock
    private CareGuideNarrationPort narrationPort;

    @Mock
    private CareResultRepository careResultRepository;

    @Mock
    private CareResultRuleRepository careResultRuleRepository;

    @Mock
    private CareResultItemRepository careResultItemRepository;

    @Mock
    private CareResultIngredientRepository careResultIngredientRepository;

    @Mock
    private CareResultIngredientRuleRepository careResultIngredientRuleRepository;

    private CareResultGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new CareResultGenerator(
                careRuleCatalogPort,
                new CareRuleEngine(),
                narrationPort,
                new CareGuideItemPlanner(),
                new RecommendedIngredientPlanner(),
                careResultRepository,
                careResultRuleRepository,
                careResultItemRepository,
                careResultIngredientRepository,
                careResultIngredientRuleRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        when(careResultRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void noActiveRuleSetStopsPlanningWithServiceUnavailable() {
        when(careRuleCatalogPort.loadActiveCatalog()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> plan(ResultType.SELF_CARE_GUIDE))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RULE_ENGINE_UNAVAILABLE);
        verify(narrationPort, never()).narrate(any());
    }

    @Test
    void noMatchingRuleStopsPlanningWithServiceUnavailable() {
        when(careRuleCatalogPort.loadActiveCatalog())
                .thenReturn(Optional.of(CareRuleFixtures.activeCatalog(CareRuleFixtures.safetyRule())));

        assertThatThrownBy(() -> plan(ResultType.SELF_CARE_GUIDE))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RULE_ENGINE_UNAVAILABLE);
    }

    /** 저장 단계가 외부 응답을 기다리지 않아야 한다. AI 호출은 결정 단계에서 끝난다. */
    @Test
    void persistDoesNotCallTheModel() {
        stubCatalog();
        stubSuccessfulNarration();
        CareResultPlan plan = plan(ResultType.SELF_CARE_GUIDE);
        org.mockito.Mockito.clearInvocations(narrationPort);

        generator.persist(REPORT_ID, USER_ID, plan, null);

        verify(narrationPort, never()).narrate(any());
    }

    @Test
    void successfulNarrationIsStoredAsGenerated() {
        stubCatalog();
        stubSuccessfulNarration();

        GeneratedCareResult generated = generateSelfCare();

        assertThat(generated.careResult().getAiGenerationStatus()).isEqualTo(AiGenerationStatus.GENERATED);
        assertThat(generated.careResult().getSummary())
                .isEqualTo("붉은 자리를 건드리지 않고 진정에 집중해 주세요.");
        assertThat(generated.careResult().getClinicianMessage()).isNull();
        assertThat(generated.careResult().isRetryUsed()).isFalse();
        assertThat(generated.ruleVersion()).isEqualTo("2026-08-15-v1");
        assertThat(generated.items()).extracting(PlannedCareItem::content)
                .containsExactly("찬 물수건으로 진정하기", "각질 제거하지 않기", "붉은 범위가 넓어졌는지 보기");
        assertThat(generated.items()).allSatisfy(item ->
                assertThat(item.sourceRuleActionId()).isNotNull());
    }

    @Test
    void failedNarrationFallsBackToApprovedTextAndRuleOrder() {
        stubCatalog();
        when(narrationPort.narrate(any()))
                .thenReturn(NarrationOutcome.failed(AiFailureCode.AI_TIMEOUT));

        GeneratedCareResult generated = generateSelfCare();

        assertThat(generated.careResult().getAiGenerationStatus()).isEqualTo(AiGenerationStatus.FALLBACK);
        assertThat(generated.careResult().getSummary())
                .isEqualTo("붉은 자리를 건드리지 않고 진정에 집중해 주세요.");
        assertThat(generated.items()).extracting(PlannedCareItem::content)
                .containsExactly(
                        "찬 물수건으로 진정하기",
                        "미지근한 물로 씻기",
                        "각질 제거하지 않기",
                        "손으로 만지지 않기",
                        "붉은 범위가 넓어졌는지 보기"
                );
        assertThat(generated.items()).noneMatch(item -> item.itemType() == CareResultItemType.CLINICIAN_MESSAGE);
    }

    @Test
    void clinicianCheckUsesApprovedMessageWithoutCallingTheModel() {
        stubCatalog();

        GeneratedCareResult generated = generate(ResultType.CLINICIAN_CHECK);

        verify(narrationPort, never()).narrate(any());
        assertThat(generated.careResult().getResultType()).isEqualTo(ResultType.CLINICIAN_CHECK);
        assertThat(generated.careResult().getAiGenerationStatus())
                .isEqualTo(AiGenerationStatus.NOT_APPLICABLE);
        assertThat(generated.careResult().getClinicianMessage())
                .isEqualTo("부대 의무실이나 가까운 의료기관에서 확인해 주세요.");
        assertThat(generated.items()).extracting(PlannedCareItem::itemType)
                .contains(CareResultItemType.CLINICIAN_MESSAGE);
    }

    @Test
    void clinicianCheckWithoutApprovedMessageIsRefused() {
        when(careRuleCatalogPort.loadActiveCatalog()).thenReturn(Optional.of(
                CareRuleFixtures.activeCatalog(CareRuleFixtures.commonRule(), CareRuleFixtures.rednessRule())
        ));

        assertThatThrownBy(() -> plan(ResultType.CLINICIAN_CHECK))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RULE_ENGINE_UNAVAILABLE);
    }

    /** 규칙 문구는 TEXT지만 결과 스냅샷은 VARCHAR다. 자르지 않고 규칙이 준비되지 않은 것으로 다룬다. */
    @Test
    void ruleTextLongerThanTheColumnIsRefusedInsteadOfTruncated() {
        when(careRuleCatalogPort.loadActiveCatalog())
                .thenReturn(Optional.of(CareRuleFixtures.activeCatalog(overlongRule())));
        when(narrationPort.narrate(any()))
                .thenReturn(NarrationOutcome.failed(AiFailureCode.AI_TIMEOUT));

        assertThatThrownBy(() -> plan(ResultType.SELF_CARE_GUIDE))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RULE_ENGINE_UNAVAILABLE);
        verify(careResultRepository, never()).saveAndFlush(any());
    }

    @Test
    void appliedRulesAreStoredInApplicationOrder() {
        stubCatalog();
        when(narrationPort.narrate(any()))
                .thenReturn(NarrationOutcome.failed(AiFailureCode.AI_UNREACHABLE));

        GeneratedCareResult generated = generateSelfCare();

        assertThat(generated.appliedRules()).extracting(CareRuleSnapshot::matchReason)
                .containsExactly(MatchReason.CURRENT_STATE, MatchReason.COMMON);
        verify(careResultRuleRepository).saveAll(any());
        verify(careResultItemRepository).saveAll(any());
    }

    @Test
    void similarExperiencePairIsStoredTogether() {
        stubCatalog();
        when(narrationPort.narrate(any()))
                .thenReturn(NarrationOutcome.failed(AiFailureCode.AI_UNREACHABLE));
        UUID similarReportId = UUID.fromString("0198a31f-f33f-7000-8000-000000000022");

        GeneratedCareResult generated = generator.persist(
                REPORT_ID,
                USER_ID,
                plan(ResultType.SELF_CARE_GUIDE),
                new ScoredSimilarExperience(similarReportId, 7)
        );

        assertThat(generated.careResult().getSimilarReportId()).isEqualTo(similarReportId);
        assertThat(generated.careResult().getSimilarityScore()).isEqualTo(7);
    }

    private void stubCatalog() {
        when(careRuleCatalogPort.loadActiveCatalog()).thenReturn(Optional.of(CareRuleFixtures.activeCatalog(
                CareRuleFixtures.commonRule(),
                CareRuleFixtures.rednessRule(),
                CareRuleFixtures.safetyRule()
        )));
    }

    private void stubSuccessfulNarration() {
        when(narrationPort.narrate(any())).thenReturn(NarrationOutcome.succeeded(
                "붉은 자리를 건드리지 않고 진정에 집중해 주세요.",
                List.of("찬 물수건으로 진정하기"),
                List.of("각질 제거하지 않기"),
                List.of("붉은 범위가 넓어졌는지 보기")
        ));
    }

    private CareRuleSnapshot overlongRule() {
        String overlong = "가".repeat(501);
        return new CareRuleSnapshot(
                UUID.fromString("0198a31f-f33f-7000-8000-000000000801"),
                UUID.fromString("0198a31f-f33f-7000-8000-000000000800"),
                "LNG-001",
                RuleCategory.COMMON,
                100,
                "긴 문구 규칙",
                overlong,
                List.of(),
                List.of(),
                List.of(new RuleActionSnapshot(
                        UUID.fromString("0198a31f-f33f-7000-8000-000000000802"),
                        RuleActionType.DO_TODAY,
                        "미지근한 물로 씻기",
                        100,
                        1
                ))
        ,
                List.of());
    }

    private CareResultPlan plan(ResultType resultType) {
        return generator.plan(
                resultType,
                resultType == ResultType.CLINICIAN_CHECK
                        ? CareRuleFixtures.clinicianCheckFacts()
                        : CareRuleFixtures.selfCareFacts()
        );
    }

    private GeneratedCareResult generateSelfCare() {
        return generate(ResultType.SELF_CARE_GUIDE);
    }

    private GeneratedCareResult generate(ResultType resultType) {
        return generator.persist(REPORT_ID, USER_ID, plan(resultType), null);
    }
}
