package likelion.flourishing.domain.report.similarity;

import likelion.flourishing.domain.report.dto.response.SimilarExperienceSummaryResponse;

/**
 * 고른 유사 경험과 그것을 응답에 담을 모양.
 *
 * <p>저장에 쓸 값과 응답에 쓸 값을 함께 들고 다닌다. 결과를 만들 때는 ID와 점수만 필요하고,
 * 응답에는 그때의 요약과 사용자가 답한 변화까지 나가야 한다.
 */
public record FoundSimilarExperience(
        ScoredSimilarExperience scored,
        SimilarExperienceSummaryResponse response
) {
}
