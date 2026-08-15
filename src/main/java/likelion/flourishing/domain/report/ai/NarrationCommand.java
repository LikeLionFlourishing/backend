package likelion.flourishing.domain.report.ai;

import java.util.List;
import java.util.Set;
import likelion.flourishing.domain.report.entity.Appearance;
import likelion.flourishing.domain.report.entity.BodyArea;
import likelion.flourishing.domain.report.entity.CareAvailability;
import likelion.flourishing.domain.report.entity.Sensation;
import likelion.flourishing.domain.report.entity.Situation;

/**
 * 관리 설명 생성에 넘기는 입력.
 *
 * <p>사용자 원문은 들어가지 않는다. 설명을 쓰는 데 필요한 것은 확정된 선택값과 규칙이 허용한
 * 문구이고, 원문을 한 번 더 외부로 보낼 이유가 없다.
 *
 * <p>allowed로 시작하는 세 목록이 이 결과에 들어갈 수 있는 문구 전부다. 모델은 여기서 고르기만
 * 하고 새 문장을 만들지 못한다. 규칙에 없는 관리 행동을 만들지 않기 위한 경계다.
 *
 * @param ruleSummaries        걸린 규칙의 적용 요약. 모델이 무엇을 골라야 하는지 판단하는 근거다.
 * @param forbiddenExpressions 규칙이 금지한 표현. 요약 문장에 들어가면 실패로 처리한다.
 * @param maxItemsPerType      항목 유형별 최대 개수.
 */
public record NarrationCommand(
        BodyArea primaryArea,
        Set<Appearance> appearances,
        Set<Sensation> sensations,
        Set<Situation> situations,
        CareAvailability careAvailability,
        List<String> ruleSummaries,
        List<String> allowedDoToday,
        List<String> allowedAvoidToday,
        List<String> allowedCheckNext,
        List<String> forbiddenExpressions,
        int maxItemsPerType
) {

    public NarrationCommand {
        appearances = Set.copyOf(appearances);
        sensations = Set.copyOf(sensations);
        situations = Set.copyOf(situations);
        ruleSummaries = List.copyOf(ruleSummaries);
        allowedDoToday = List.copyOf(allowedDoToday);
        allowedAvoidToday = List.copyOf(allowedAvoidToday);
        allowedCheckNext = List.copyOf(allowedCheckNext);
        forbiddenExpressions = List.copyOf(forbiddenExpressions);
    }

    /** 고를 수 있는 문구가 하나도 없으면 모델을 부를 이유가 없다. */
    public boolean hasAllowedActions() {
        return !allowedDoToday.isEmpty() || !allowedAvoidToday.isEmpty() || !allowedCheckNext.isEmpty();
    }
}
