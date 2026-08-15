package likelion.flourishing.domain.report.similarity;

import java.util.Set;
import likelion.flourishing.domain.report.entity.Appearance;
import likelion.flourishing.domain.report.entity.BodyArea;
import likelion.flourishing.domain.report.entity.ResultType;
import likelion.flourishing.domain.report.entity.Sensation;
import likelion.flourishing.domain.report.entity.Situation;

/**
 * 유사도를 매길 기준값. 지금 보고의 확정 선택값과 서버가 정한 결과 유형이다.
 *
 * @param resultType 서버가 관리 전 확인값으로 결정한 유형. 사용자가 보낸 값이 아니다.
 */
public record SimilarExperienceQuery(
        BodyArea primaryArea,
        Set<Appearance> appearances,
        Set<Sensation> sensations,
        Set<Situation> situations,
        ResultType resultType
) {

    public SimilarExperienceQuery {
        appearances = Set.copyOf(appearances);
        sensations = Set.copyOf(sensations);
        situations = Set.copyOf(situations);
    }
}
