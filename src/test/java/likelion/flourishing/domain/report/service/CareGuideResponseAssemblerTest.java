package likelion.flourishing.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import likelion.flourishing.domain.report.dto.response.CareGuideResponse;
import likelion.flourishing.domain.report.dto.response.GuideSectionResponse;
import likelion.flourishing.domain.report.dto.response.RecommendedIngredientResponse;
import likelion.flourishing.domain.report.entity.AiGenerationStatus;
import likelion.flourishing.domain.report.entity.CareResult;
import likelion.flourishing.domain.report.entity.CareResultItemType;
import likelion.flourishing.domain.report.entity.GuideSectionKey;
import likelion.flourishing.domain.report.rule.CareRuleFixtures;
import likelion.flourishing.domain.report.rule.CareRuleSnapshot;
import org.junit.jupiter.api.Test;

/**
 * 명세 v2_1 이 CareResult 에 추가한 guideSections 와 recommendedIngredients 의 계약을 고정한다.
 *
 * <p>특히 두 가지다. 의료진 확인 안내에는 둘 다 나가지 않아야 하고, 성분의 근거 규칙은 그 결과에
 * 적용된 규칙 안에 있어야 한다.
 */
class CareGuideResponseAssemblerTest {

    private static final UUID REPORT_ID = UUID.fromString("0198a31f-f33f-7000-8000-0000000000a1");
    private static final UUID USER_ID = UUID.fromString("0198a31f-f33f-7000-8000-0000000000b1");
    private static final UUID RULE_SET_ID = UUID.fromString("0198a31f-f33f-7000-8000-0000000000d1");
    private static final LocalDateTime GENERATED_AT = LocalDateTime.of(2026, 8, 18, 3, 0);

    private final CareGuideResponseAssembler assembler =
            new CareGuideResponseAssembler(GuideSectionFixtures.assembler());

    @Test
    void selfCareGuideCarriesSixSectionsAndTheChosenIngredients() {
        CareRuleSnapshot rule = CareRuleFixtures.commonRule();

        CareGuideResponse response = assembler.assemble(
                selfCareResult(),
                "v0.1",
                List.of(rule),
                List.of(new PlannedCareItem(CareResultItemType.DO_TODAY, "미지근한 물로 세안하기", null, 1)),
                List.of(ingredient("ING_PANTHENOL", List.of(rule.ruleCode()))),
                null
        );

        assertThat(response.getGuideSections()).hasSize(GuideSectionKey.SECTION_COUNT);
        assertThat(response.getRecommendedIngredients())
                .extracting(RecommendedIngredientResponse::getId)
                .containsExactly("ING_PANTHENOL");
        assertThat(response.getRecommendedIngredients().getFirst().getSourceRuleIds())
                .containsExactly(rule.ruleCode());
        assertThat(emptyOf(response.getGuideSections(), GuideSectionKey.DO_TODAY)).isFalse();
        assertThat(emptyOf(response.getGuideSections(), GuideSectionKey.RECOMMENDED_INGREDIENTS)).isFalse();
    }

    @Test
    void ruleWithoutIngredientsMarksTheIngredientSectionEmpty() {
        CareGuideResponse response = assembler.assemble(
                selfCareResult(),
                "v0.1",
                List.of(CareRuleFixtures.commonRule()),
                List.of(new PlannedCareItem(CareResultItemType.DO_TODAY, "미지근한 물로 세안하기", null, 1)),
                List.of(),
                null
        );

        assertThat(response.getRecommendedIngredients()).isEmpty();
        assertThat(emptyOf(response.getGuideSections(), GuideSectionKey.RECOMMENDED_INGREDIENTS)).isTrue();
        // 섹션 자체는 남는다. 결과마다 화면 구성이 달라지지 않게 하려는 것이다.
        assertThat(response.getGuideSections()).hasSize(GuideSectionKey.SECTION_COUNT);
    }

    @Test
    void clinicianCheckCarriesNeitherSectionsNorIngredients() {
        CareRuleSnapshot rule = CareRuleFixtures.safetyRule();

        CareGuideResponse response = assembler.assemble(
                clinicianResult(),
                "v0.1",
                List.of(rule),
                List.of(new PlannedCareItem(CareResultItemType.CLINICIAN_MESSAGE, "진료를 받아 보세요.", null, 1)),
                List.of(ingredient("ING_PANTHENOL", List.of(rule.ruleCode()))),
                null
        );

        // 명세가 CLINICIAN_CHECK 에 두 필드 모두 maxItems 0 을 걸어 두었다. 성분을 넘겨도
        // 조립부가 비운다. 결과를 정하는 쪽이 이미 걸러도 여기서 한 번 더 막는다.
        assertThat(response.getGuideSections()).isEmpty();
        assertThat(response.getRecommendedIngredients()).isEmpty();
        assertThat(response.getClinicianMessage()).isEqualTo("진료를 받아 보세요.");
    }

    @Test
    void ingredientWhoseSourceRuleIsNotAppliedIsDropped() {
        CareRuleSnapshot applied = CareRuleFixtures.commonRule();

        CareGuideResponse response = assembler.assemble(
                selfCareResult(),
                "v0.1",
                List.of(applied),
                List.of(new PlannedCareItem(CareResultItemType.DO_TODAY, "미지근한 물로 세안하기", null, 1)),
                List.of(ingredient("ING_GHOST", List.of("NOT-APPLIED-999"))),
                null
        );

        // 근거가 적용 규칙 밖만 가리키면 남는 근거가 없다. 근거 없는 성분 추천은 내보내지 않는다.
        assertThat(response.getRecommendedIngredients()).isEmpty();
    }

    @Test
    void onlyTheSourceRulesInsideMatchedRulesSurvive() {
        CareRuleSnapshot applied = CareRuleFixtures.commonRule();

        CareGuideResponse response = assembler.assemble(
                selfCareResult(),
                "v0.1",
                List.of(applied),
                List.of(new PlannedCareItem(CareResultItemType.DO_TODAY, "미지근한 물로 세안하기", null, 1)),
                List.of(ingredient("ING_PANTHENOL", List.of(applied.ruleCode(), "NOT-APPLIED-999"))),
                null
        );

        assertThat(response.getRecommendedIngredients()).hasSize(1);
        assertThat(response.getRecommendedIngredients().getFirst().getSourceRuleIds())
                .containsExactly(applied.ruleCode())
                .allSatisfy(ruleId -> assertThat(response.getMatchedRuleIds()).contains(ruleId));
    }

    private CareResult selfCareResult() {
        return CareResult.selfCareGuide(
                REPORT_ID, USER_ID, RULE_SET_ID, null, null,
                AiGenerationStatus.GENERATED, "오늘 상태 요약", GENERATED_AT
        );
    }

    private CareResult clinicianResult() {
        return CareResult.clinicianCheck(
                REPORT_ID, USER_ID, RULE_SET_ID, null, null,
                "오늘 상태 요약", "진료를 받아 보세요.", GENERATED_AT
        );
    }

    private PlannedIngredient ingredient(String code, List<String> sourceRuleCodes) {
        return new PlannedIngredient(UUID.randomUUID(), code, "판테놀", "진정에 쓰이는 성분입니다.", null, sourceRuleCodes, 1);
    }

    private boolean emptyOf(List<GuideSectionResponse> sections, GuideSectionKey key) {
        return sections.stream()
                .filter(section -> section.getKey() == key)
                .findFirst()
                .orElseThrow()
                .isEmpty();
    }
}
