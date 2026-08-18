package likelion.flourishing.domain.report.similarity;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import likelion.flourishing.domain.report.entity.Appearance;
import likelion.flourishing.domain.report.entity.Sensation;
import likelion.flourishing.domain.report.entity.Situation;
import likelion.flourishing.domain.report.entity.SkinReport;
import org.springframework.stereotype.Component;

/**
 * 지금 보고와 과거 완료 기록이 얼마나 닮았는지 점수로 매긴다.
 *
 * <p>배점은 겉으로 확인할 수 있는 것에 무게를 둔다. 부위가 같으면 3점, 겉모습이 겹치면 개당 2점,
 * 불편은 개당 1점, 직전 상황은 개당 1점, 결과 유형이 같으면 1점이다. 항목별 상한을 둬서 다중
 * 선택을 많이 고른 기록이 점수만으로 앞서지 않게 한다.
 *
 * <p>"모름", "없음", "기억나는 게 없음"은 겹쳐도 점수를 주지 않는다. 둘 다 모른다는 사실은
 * 닮았다는 근거가 아니다.
 *
 * <p>{@link #MINIMUM_SCORE} 미만은 고르지 않는다. 근거가 약한 기록을 "비슷한 경험"으로 보여 주면
 * 사용자가 잘못된 기준으로 지금 상태를 판단하게 된다. DDL도 5점 미만 저장을 막는다.
 */
@Component
public class SimilarExperienceScorer {

    /** care_results.similarity_score의 CHECK 제약과 같은 하한. */
    public static final int MINIMUM_SCORE = 5;

    private static final int PRIMARY_AREA_SCORE = 3;
    private static final int APPEARANCE_SCORE_PER_MATCH = 2;
    private static final int APPEARANCE_SCORE_CAP = 4;
    private static final int SENSATION_SCORE_PER_MATCH = 1;
    private static final int SENSATION_SCORE_CAP = 3;
    private static final int SITUATION_SCORE_PER_MATCH = 1;
    private static final int SITUATION_SCORE_CAP = 2;
    private static final int RESULT_TYPE_SCORE = 1;

    /**
     * 하한을 넘는 후보를 점수가 높은 순으로 줄 세운다.
     *
     * <p>점수가 같으면 넘겨받은 순서를 지킨다. 호출하는 쪽이 최근 날짜부터 넘기므로 최근 기록이
     * 앞선다. 오래된 기록보다 최근 기록이 지금 상태를 견주기에 낫다.
     *
     * <p>하나만 고르지 않고 순위를 돌려주는 이유는, 고른 기록에 결과나 경과가 없으면 다음 후보로
     * 넘어가야 하기 때문이다. 그 판단은 저장소를 아는 쪽에서 한다.
     */
    public List<ScoredSimilarExperience> rank(SimilarExperienceQuery query, List<SkinReport> candidates) {
        return candidates.stream()
                .map(candidate -> new ScoredSimilarExperience(candidate.getId(), score(query, candidate)))
                .filter(scored -> scored.score() >= MINIMUM_SCORE)
                .sorted(Comparator.comparingInt(ScoredSimilarExperience::score).reversed())
                .toList();
    }

    /** 점수 계산. 테스트가 배점을 직접 확인할 수 있게 공개한다. */
    public int score(SimilarExperienceQuery query, SkinReport candidate) {
        int score = 0;
        if (query.primaryArea() == candidate.getPrimaryArea()) {
            score += PRIMARY_AREA_SCORE;
        }
        score += capped(
                overlapCount(query.appearances(), candidate.getAppearances()),
                APPEARANCE_SCORE_PER_MATCH,
                APPEARANCE_SCORE_CAP
        );
        score += capped(
                overlapCount(query.sensations(), candidate.getSensations()),
                SENSATION_SCORE_PER_MATCH,
                SENSATION_SCORE_CAP
        );
        score += capped(
                overlapCountIgnoring(query.situations(), candidate.getSituations(), Situation.NONE_RECALLED),
                SITUATION_SCORE_PER_MATCH,
                SITUATION_SCORE_CAP
        );
        if (query.resultType() == candidate.getResultType()) {
            score += RESULT_TYPE_SCORE;
        }
        return score;
    }

    /**
     * 두 집합에 함께 있는 값의 수.
     *
     * <p>겉모습과 느껴지는 불편이 이 형태를 쓴다. 명세 v2_1에서 두 그룹의 "모름/없음" 값이
     * 사라져 점수에서 걸러 낼 대상이 없어졌다.
     */
    private <E extends Enum<E>> int overlapCount(Set<E> current, Set<E> candidate) {
        return (int) current.stream()
                .filter(candidate::contains)
                .count();
    }

    /**
     * 지정한 값을 뺀 겹침 수.
     *
     * <p>직전 상황의 NONE_RECALLED 를 위해 남겨 둔다. 둘 다 "떠오르는 상황이 없다"고 답한 것은
     * 같은 상황을 겪었다는 뜻이 아니라 둘 다 단서가 없다는 뜻이라, 유사 신호로 세지 않는다.
     */
    private <E extends Enum<E>> int overlapCountIgnoring(Set<E> current, Set<E> candidate, E ignored) {
        return (int) current.stream()
                .filter(value -> value != ignored)
                .filter(candidate::contains)
                .count();
    }

    private int capped(int matchCount, int scorePerMatch, int cap) {
        return Math.min(matchCount * scorePerMatch, cap);
    }
}
