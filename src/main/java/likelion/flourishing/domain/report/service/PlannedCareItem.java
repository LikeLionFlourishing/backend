package likelion.flourishing.domain.report.service;

import java.util.UUID;
import likelion.flourishing.domain.report.entity.CareResultItemType;

/**
 * 저장하기 전 단계의 결과 항목.
 *
 * <p>엔티티를 바로 만들지 않는 이유는 결과 ID가 아직 없기 때문이다. 항목을 먼저 정하고 결과를
 * 만든 다음 그 ID로 엔티티를 찍어 낸다.
 *
 * @param sourceRuleActionId 문구가 온 규칙 행동. 문구를 규칙에서 가져왔음을 남기는 증거다.
 */
public record PlannedCareItem(
        CareResultItemType itemType,
        String content,
        UUID sourceRuleActionId,
        int displayOrder
) {
}
