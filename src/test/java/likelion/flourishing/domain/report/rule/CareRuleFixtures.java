package likelion.flourishing.domain.report.rule;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import likelion.flourishing.domain.report.entity.Appearance;
import likelion.flourishing.domain.report.entity.BodyArea;
import likelion.flourishing.domain.report.entity.CareAvailability;
import likelion.flourishing.domain.report.entity.PreCareCheck;
import likelion.flourishing.domain.report.entity.RuleActionType;
import likelion.flourishing.domain.report.entity.RuleCategory;
import likelion.flourishing.domain.report.entity.RuleConditionField;
import likelion.flourishing.domain.report.entity.RuleOperator;
import likelion.flourishing.domain.report.entity.Sensation;
import likelion.flourishing.domain.report.entity.Situation;

/**
 * 테스트 전용 관리 규칙 fixture.
 *
 * <p>전문가 검토가 끝난 관리 규칙 최종본을 아직 받지 못했으므로 운영 데이터를 쓰지 않는다.
 * 여기 있는 문구는 규칙 엔진과 저장 흐름을 확인하기 위한 가짜 값이고, 실제 안내 문구는 최종본을
 * 초기 데이터로 넣을 때 들어온다.
 */
public final class CareRuleFixtures {

    private CareRuleFixtures() {
    }

    /** 조건 없는 공통 규칙. 모든 보고에 걸린다. */
    public static CareRuleSnapshot commonRule() {
        return new CareRuleSnapshot(
                UUID.fromString("0198a31f-f33f-7000-8000-0000000000c1"),
                UUID.fromString("0198a31f-f33f-7000-8000-0000000000c0"),
                "GEN-001",
                RuleCategory.COMMON,
                500,
                "모든 보고에 적용하는 공통 관리",
                "오늘은 자극을 줄이고 상태를 지켜봐 주세요.",
                List.of("완치", "반드시 낫습니다"),
                List.of(),
                List.of(
                        action("0198a31f-f33f-7000-8000-0000000000a1", RuleActionType.DO_TODAY, "미지근한 물로 씻기", 100, 1),
                        action("0198a31f-f33f-7000-8000-0000000000a2", RuleActionType.AVOID_TODAY, "손으로 만지지 않기", 100, 1),
                        action("0198a31f-f33f-7000-8000-0000000000a3", RuleActionType.CHECK_NEXT, "붉은 범위가 넓어졌는지 보기", 100, 1)
                )
        );
    }

    /** 겉모습에 붉음이 있을 때 걸리는 현재 상태 규칙. */
    public static CareRuleSnapshot rednessRule() {
        return new CareRuleSnapshot(
                UUID.fromString("0198a31f-f33f-7000-8000-0000000000d1"),
                UUID.fromString("0198a31f-f33f-7000-8000-0000000000d0"),
                "STA-001",
                RuleCategory.CURRENT_STATE,
                200,
                "붉음이 있을 때의 관리",
                "붉은 자리를 건드리지 않고 진정에 집중해 주세요.",
                List.of(),
                List.of(new RuleConditionSpec(
                        1, RuleConditionField.APPEARANCES, RuleOperator.CONTAINS, Appearance.REDNESS.name(), false
                )),
                List.of(
                        action("0198a31f-f33f-7000-8000-0000000000b1", RuleActionType.DO_TODAY, "찬 물수건으로 진정하기", 50, 1),
                        action("0198a31f-f33f-7000-8000-0000000000b2", RuleActionType.AVOID_TODAY, "각질 제거하지 않기", 50, 1)
                )
        );
    }

    /** 관리 전 확인에서 위험 신호를 골랐을 때 걸리는 안전 규칙. */
    public static CareRuleSnapshot safetyRule() {
        return new CareRuleSnapshot(
                UUID.fromString("0198a31f-f33f-7000-8000-0000000000e1"),
                UUID.fromString("0198a31f-f33f-7000-8000-0000000000e0"),
                "SAF-001",
                RuleCategory.SAFETY,
                10,
                "위험 신호가 있을 때의 안내",
                "지금은 스스로 관리하기보다 의료진 확인이 필요한 상태로 보입니다.",
                List.of(),
                List.of(new RuleConditionSpec(
                        1,
                        RuleConditionField.PRE_CARE_CHECKS,
                        RuleOperator.NOT_EQUALS,
                        PreCareCheck.NONE.name(),
                        false
                )),
                List.of(
                        action("0198a31f-f33f-7000-8000-0000000000f1", RuleActionType.AVOID_TODAY, "짜거나 뜯지 않기", 10, 1),
                        action(
                                "0198a31f-f33f-7000-8000-0000000000f2",
                                RuleActionType.CLINICIAN_MESSAGE,
                                "부대 의무실이나 가까운 의료기관에서 확인해 주세요.",
                                10,
                                1
                        )
                )
        );
    }

    public static ActiveRuleCatalog activeCatalog(CareRuleSnapshot... rules) {
        return new ActiveRuleCatalog(
                UUID.fromString("0198a31f-f33f-7000-8000-000000000901"),
                "2026-08-15-v1",
                List.of(rules)
        );
    }

    /** 붉음과 따가움을 고른 일반 관리 상황의 사실값. */
    public static RuleEvaluationFacts selfCareFacts() {
        return new RuleEvaluationFacts(
                BodyArea.RIGHT_CHIN,
                Set.of(Appearance.REDNESS),
                Set.of(Sensation.STINGING_BURNING),
                Set.of(Situation.SHAVING),
                CareAvailability.ALREADY_WASHED,
                Set.of(PreCareCheck.NONE),
                Set.of()
        );
    }

    /** 위험 신호를 고른 의료진 확인 상황의 사실값. */
    public static RuleEvaluationFacts clinicianCheckFacts() {
        return new RuleEvaluationFacts(
                BodyArea.RIGHT_CHIN,
                Set.of(Appearance.REDNESS),
                Set.of(Sensation.PAIN_AT_REST),
                Set.of(Situation.SHAVING),
                CareAvailability.ALREADY_WASHED,
                Set.of(PreCareCheck.PUS_OOZING_BLISTER),
                Set.of()
        );
    }

    private static RuleActionSnapshot action(
            String id,
            RuleActionType type,
            String content,
            int priority,
            int displayOrder
    ) {
        return new RuleActionSnapshot(UUID.fromString(id), type, content, priority, displayOrder);
    }
}
