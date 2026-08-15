package likelion.flourishing.domain.report.rule;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import likelion.flourishing.domain.report.entity.Appearance;
import likelion.flourishing.domain.report.entity.BodyArea;
import likelion.flourishing.domain.report.entity.CareAvailability;
import likelion.flourishing.domain.report.entity.PreCareCheck;
import likelion.flourishing.domain.report.entity.RuleConditionField;
import likelion.flourishing.domain.report.entity.Sensation;
import likelion.flourishing.domain.report.entity.Situation;

/**
 * 규칙 조건이 들여다보는 사실값. 사용자가 최종 확인한 값과 과거 기록에서만 만든다.
 *
 * <p>원문은 들어오지 않는다. 규칙은 문장이 아니라 선택값으로만 판단한다.
 *
 * @param completedHistory 완료된 과거 기록에서 뽑은 코드. 지금은 과거 보고의 겉모습 코드와
 *                         유사 경험을 찾았는지를 알리는 표시로 채운다. 어떤 코드를 조건으로 쓸지는
 *                         관리 규칙 최종본에서 확정된다.
 */
public record RuleEvaluationFacts(
        BodyArea primaryArea,
        Set<Appearance> appearances,
        Set<Sensation> sensations,
        Set<Situation> situations,
        CareAvailability careAvailability,
        Set<PreCareCheck> preCareChecks,
        Set<String> completedHistory
) {

    /** 유사 경험을 찾았을 때 completedHistory에 넣는 표시. */
    public static final String SIMILAR_EXPERIENCE_FOUND = "SIMILAR_EXPERIENCE_FOUND";

    public RuleEvaluationFacts {
        appearances = Set.copyOf(appearances);
        sensations = Set.copyOf(sensations);
        situations = Set.copyOf(situations);
        preCareChecks = Set.copyOf(preCareChecks);
        completedHistory = Set.copyOf(completedHistory);
    }

    /** 조건 필드에 해당하는 값을 코드 집합으로 돌려준다. 단일 값 필드는 원소 하나짜리 집합이다. */
    public Set<String> valuesOf(RuleConditionField field) {
        return switch (field) {
            case PRIMARY_AREA -> single(primaryArea);
            case CARE_AVAILABILITY -> single(careAvailability);
            case APPEARANCES -> names(appearances);
            case SENSATIONS -> names(sensations);
            case SITUATIONS -> names(situations);
            case PRE_CARE_CHECKS -> names(preCareChecks);
            case COMPLETED_HISTORY -> completedHistory;
        };
    }

    private Set<String> single(Enum<?> value) {
        return value == null ? Set.of() : Set.of(value.name());
    }

    private Set<String> names(Set<? extends Enum<?>> values) {
        return values.stream().map(Enum::name).collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
