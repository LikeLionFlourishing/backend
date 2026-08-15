package likelion.flourishing.domain.report.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import likelion.flourishing.domain.report.entity.Appearance;
import likelion.flourishing.domain.report.entity.BodyArea;
import likelion.flourishing.domain.report.entity.CareAvailability;
import likelion.flourishing.domain.report.entity.PreCareCheck;
import likelion.flourishing.domain.report.entity.RuleCategory;
import likelion.flourishing.domain.report.entity.RuleConditionField;
import likelion.flourishing.domain.report.entity.RuleOperator;
import likelion.flourishing.domain.report.entity.Sensation;
import likelion.flourishing.domain.report.entity.Situation;
import org.junit.jupiter.api.Test;

/**
 * 규칙 엔진 테스트. 테스트 전용 fixture만 쓰고 운영 규칙 데이터에 의존하지 않는다.
 *
 * <p>확인하는 것: 조건 없는 규칙이 항상 걸리는지, 같은 그룹은 모두 만족해야 하고 그룹 사이는
 * 하나만 만족하면 되는지, 연산자와 negated가 뜻대로 동작하는지, 적용 순서가 카테고리와
 * 우선순위로 정해지는지.
 */
class CareRuleEngineTest {

    private final CareRuleEngine engine = new CareRuleEngine();

    @Test
    void ruleWithoutConditionsAlwaysMatches() {
        List<CareRuleSnapshot> matched = engine.match(
                CareRuleFixtures.activeCatalog(CareRuleFixtures.commonRule()),
                CareRuleFixtures.selfCareFacts()
        );

        assertThat(matched).extracting(CareRuleSnapshot::ruleCode).containsExactly("GEN-001");
    }

    @Test
    void safetyRuleComesBeforeStateAndCommonRules() {
        List<CareRuleSnapshot> matched = engine.match(
                CareRuleFixtures.activeCatalog(
                        CareRuleFixtures.commonRule(),
                        CareRuleFixtures.rednessRule(),
                        CareRuleFixtures.safetyRule()
                ),
                CareRuleFixtures.clinicianCheckFacts()
        );

        assertThat(matched).extracting(CareRuleSnapshot::ruleCode)
                .containsExactly("SAF-001", "STA-001", "GEN-001");
    }

    @Test
    void stateRuleIsSkippedWhenAppearanceIsAbsent() {
        RuleEvaluationFacts withoutRedness = facts(
                Set.of(Appearance.CRUST), Set.of(Sensation.NONE), Set.of(PreCareCheck.NONE)
        );

        List<CareRuleSnapshot> matched = engine.match(
                CareRuleFixtures.activeCatalog(CareRuleFixtures.rednessRule()), withoutRedness
        );

        assertThat(matched).isEmpty();
    }

    @Test
    void conditionsInOneGroupMustAllHold() {
        CareRuleSnapshot rule = ruleWith(List.of(
                condition(1, RuleConditionField.APPEARANCES, RuleOperator.CONTAINS, Appearance.REDNESS.name()),
                condition(1, RuleConditionField.SENSATIONS, RuleOperator.CONTAINS, Sensation.ITCHING.name())
        ));
        RuleEvaluationFacts onlyRedness = facts(
                Set.of(Appearance.REDNESS), Set.of(Sensation.HEAT), Set.of(PreCareCheck.NONE)
        );

        assertThat(engine.match(CareRuleFixtures.activeCatalog(rule), onlyRedness)).isEmpty();
    }

    @Test
    void separateGroupsBehaveAsAlternatives() {
        CareRuleSnapshot rule = ruleWith(List.of(
                condition(1, RuleConditionField.APPEARANCES, RuleOperator.CONTAINS, Appearance.OOZING.name()),
                condition(2, RuleConditionField.SENSATIONS, RuleOperator.CONTAINS, Sensation.HEAT.name())
        ));
        RuleEvaluationFacts secondGroupOnly = facts(
                Set.of(Appearance.REDNESS), Set.of(Sensation.HEAT), Set.of(PreCareCheck.NONE)
        );

        assertThat(engine.match(CareRuleFixtures.activeCatalog(rule), secondGroupOnly)).hasSize(1);
    }

    @Test
    void containsAnyAcceptsCommaSeparatedValues() {
        CareRuleSnapshot rule = ruleWith(List.of(condition(
                1,
                RuleConditionField.SITUATIONS,
                RuleOperator.CONTAINS_ANY,
                Situation.NEW_PRODUCT.name() + "," + Situation.SHAVING.name()
        )));

        assertThat(engine.match(CareRuleFixtures.activeCatalog(rule), CareRuleFixtures.selfCareFacts()))
                .hasSize(1);
    }

    @Test
    void notEqualsTreatsExclusiveNoneAsTheWholeAnswer() {
        CareRuleSnapshot rule = ruleWith(List.of(condition(
                1, RuleConditionField.PRE_CARE_CHECKS, RuleOperator.NOT_EQUALS, PreCareCheck.NONE.name()
        )));

        assertThat(engine.match(CareRuleFixtures.activeCatalog(rule), CareRuleFixtures.selfCareFacts()))
                .isEmpty();
        assertThat(engine.match(CareRuleFixtures.activeCatalog(rule), CareRuleFixtures.clinicianCheckFacts()))
                .hasSize(1);
    }

    @Test
    void negatedFlipsTheConditionResult() {
        CareRuleSnapshot rule = ruleWith(List.of(new RuleConditionSpec(
                1, RuleConditionField.APPEARANCES, RuleOperator.CONTAINS, Appearance.REDNESS.name(), true
        )));

        assertThat(engine.match(CareRuleFixtures.activeCatalog(rule), CareRuleFixtures.selfCareFacts()))
                .isEmpty();
    }

    @Test
    void existsChecksThatCompletedHistoryHasAnyCode() {
        CareRuleSnapshot rule = ruleWith(List.of(condition(
                1, RuleConditionField.COMPLETED_HISTORY, RuleOperator.EXISTS, "*"
        )));
        RuleEvaluationFacts withHistory = new RuleEvaluationFacts(
                BodyArea.RIGHT_CHIN,
                Set.of(Appearance.REDNESS),
                Set.of(Sensation.NONE),
                Set.of(Situation.NONE_RECALLED),
                CareAvailability.ALREADY_WASHED,
                Set.of(PreCareCheck.NONE),
                Set.of(RuleEvaluationFacts.SIMILAR_EXPERIENCE_FOUND)
        );

        assertThat(engine.match(CareRuleFixtures.activeCatalog(rule), CareRuleFixtures.selfCareFacts()))
                .isEmpty();
        assertThat(engine.match(CareRuleFixtures.activeCatalog(rule), withHistory)).hasSize(1);
    }

    private RuleConditionSpec condition(
            int group,
            RuleConditionField field,
            RuleOperator operator,
            String valueCode
    ) {
        return new RuleConditionSpec(group, field, operator, valueCode, false);
    }

    private CareRuleSnapshot ruleWith(List<RuleConditionSpec> conditions) {
        return new CareRuleSnapshot(
                UUID.fromString("0198a31f-f33f-7000-8000-000000000701"),
                UUID.fromString("0198a31f-f33f-7000-8000-000000000700"),
                "TST-001",
                RuleCategory.SITUATION,
                100,
                "테스트 규칙",
                "테스트 대체 문구",
                List.of(),
                conditions,
                List.of()
        );
    }

    private RuleEvaluationFacts facts(
            Set<Appearance> appearances,
            Set<Sensation> sensations,
            Set<PreCareCheck> preCareChecks
    ) {
        return new RuleEvaluationFacts(
                BodyArea.RIGHT_CHIN,
                appearances,
                sensations,
                Set.of(Situation.SHAVING),
                CareAvailability.ALREADY_WASHED,
                preCareChecks,
                Set.of()
        );
    }
}
