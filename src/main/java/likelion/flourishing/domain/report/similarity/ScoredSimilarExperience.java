package likelion.flourishing.domain.report.similarity;

import java.util.UUID;

/**
 * 유사 경험으로 고른 과거 보고와 그 점수.
 *
 * <p>care_results에는 두 값이 항상 함께 저장되거나 함께 비어야 한다. 한쪽만 남으면 조회에서
 * 불변식 위반으로 처리되므로 짝을 이 타입으로 묶어 다닌다.
 */
public record ScoredSimilarExperience(UUID reportId, int score) {
}
