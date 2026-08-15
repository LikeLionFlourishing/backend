package likelion.flourishing.domain.report.similarity;

import java.util.Optional;
import java.util.Set;

/**
 * 과거 완료 기록을 한 번 읽어 얻는 두 가지 결과.
 *
 * <p>유사 경험 선택과 규칙 조건용 과거 기록 코드가 같은 후보 집합에서 나온다. 따로 조회하면 같은
 * 질의를 두 번 하게 되므로 함께 돌려준다.
 *
 * @param found            고른 유사 경험. 하한을 넘는 후보가 없으면 비어 있다.
 * @param completedHistory 규칙 조건 completedHistory가 보는 코드.
 */
public record SimilarExperienceLookup(Optional<FoundSimilarExperience> found, Set<String> completedHistory) {

    public static SimilarExperienceLookup empty() {
        return new SimilarExperienceLookup(Optional.empty(), Set.of());
    }
}
