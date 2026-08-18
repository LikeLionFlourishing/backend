package likelion.flourishing.domain.report.similarity;

import java.util.Set;
import likelion.flourishing.domain.report.entity.Appearance;
import likelion.flourishing.domain.report.entity.BodyArea;
import likelion.flourishing.domain.report.entity.CareAvailability;
import likelion.flourishing.domain.report.entity.Sensation;
import likelion.flourishing.domain.report.entity.Situation;

/**
 * 유사도를 매길 기준값. 지금 보고의 확정 선택값이다.
 *
 * @param careAvailability 지금 관리할 수 있는 상태. 명세가 유사도 배점에 넣은 값이다.
 */
public record SimilarExperienceQuery(
        BodyArea primaryArea,
        Set<Appearance> appearances,
        Set<Sensation> sensations,
        Set<Situation> situations,
        CareAvailability careAvailability
) {

    public SimilarExperienceQuery {
        appearances = Set.copyOf(appearances);
        sensations = Set.copyOf(sensations);
        situations = Set.copyOf(situations);
    }
}
