package likelion.flourishing.domain.report.similarity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import likelion.flourishing.domain.report.entity.Appearance;
import likelion.flourishing.domain.report.entity.BodyArea;
import likelion.flourishing.domain.report.entity.CareAvailability;
import likelion.flourishing.domain.report.entity.PreCareCheck;
import likelion.flourishing.domain.report.entity.ResultType;
import likelion.flourishing.domain.report.entity.Sensation;
import likelion.flourishing.domain.report.entity.Situation;
import likelion.flourishing.domain.report.entity.SkinReport;
import org.junit.jupiter.api.Test;

/**
 * 유사 완료 기록 점수 계산기 테스트.
 *
 * <p>확인하는 것: 배점과 상한이 맞는지, 하한 미만은 고르지 않는지, "모름"과 "없음"이 겹쳐도
 * 점수가 붙지 않는지, 점수가 같으면 넘겨받은 순서(최근 우선)를 지키는지.
 */
class SimilarExperienceScorerTest {

    private final SimilarExperienceScorer scorer = new SimilarExperienceScorer();

    @Test
    void sameAreaAndAppearancesReachThreshold() {
        SimilarExperienceQuery query = query(
                BodyArea.RIGHT_CHIN, Set.of(Appearance.APP_REDNESS), Set.of(Sensation.EXCESS_SEBUM), Set.of(Situation.SHAVING)
        );
        SkinReport candidate = report(
                BodyArea.RIGHT_CHIN, Set.of(Appearance.APP_REDNESS), Set.of(Sensation.EXCESS_SEBUM), Set.of(Situation.SHAVING)
        );

        // 부위 3 + 겉모습 2 + 불편 1 + 직전 상황 1 + 결과 유형 1 = 8
        // 명세 v2_1 전에는 불편이 NONE이라 점수에서 빠져 7이었다. 이제 NONE이 없어 함께 센다.
        assertThat(scorer.score(query, candidate)).isEqualTo(8);
    }

    @Test
    void appearanceScoreIsCapped() {
        Set<Appearance> manyAppearances = Set.of(
                Appearance.APP_REDNESS,
                Appearance.APP_BUMP,
                Appearance.APP_PUS_BUMP,
                Appearance.APP_DRYNESS
        );
        SimilarExperienceQuery query = query(
                BodyArea.NOSE, manyAppearances, Set.of(Sensation.EXCESS_SEBUM), Set.of(Situation.NONE_RECALLED)
        );
        SkinReport candidate = report(
                BodyArea.NOSE, manyAppearances, Set.of(Sensation.EXCESS_SEBUM), Set.of(Situation.NONE_RECALLED)
        );

        // 부위 3 + 겉모습 상한 4 + 불편 1 + 결과 유형 1 = 9. 겉모습 네 개가 8점이 되지 않는다.
        assertThat(scorer.score(query, candidate)).isEqualTo(9);
    }

    /**
     * 직전 상황의 NONE_RECALLED만 점수에서 빠진다.
     *
     * <p>둘 다 "떠오르는 상황이 없다"고 답한 것은 같은 상황을 겪었다는 뜻이 아니라 둘 다 단서가
     * 없다는 뜻이라 유사 신호로 세지 않는다. 겉모습과 느껴지는 불편에는 명세 v2_1에서 그런 값이
     * 사라져, 겹치면 그대로 점수가 된다.
     */
    @Test
    void onlyNoneRecalledSituationEarnsNoScore() {
        SimilarExperienceQuery query = query(
                BodyArea.NECK,
                Set.of(Appearance.APP_OTHER),
                Set.of(Sensation.EXCESS_SEBUM),
                Set.of(Situation.NONE_RECALLED)
        );
        SkinReport candidate = report(
                BodyArea.LEFT_CHEEK,
                Set.of(Appearance.APP_OTHER),
                Set.of(Sensation.EXCESS_SEBUM),
                Set.of(Situation.NONE_RECALLED)
        );

        // 부위가 다르고 상황은 NONE_RECALLED라 빠진다. 겉모습 2 + 불편 1 + 결과 유형 1 = 4
        assertThat(scorer.score(query, candidate)).isEqualTo(4);
    }

    @Test
    void candidatesBelowMinimumScoreAreNotRanked() {
        SimilarExperienceQuery query = query(
                BodyArea.NECK, Set.of(Appearance.APP_REDNESS), Set.of(Sensation.BREAKOUT), Set.of(Situation.SHAVING)
        );
        SkinReport weakCandidate = report(
                BodyArea.NOSE, Set.of(Appearance.APP_OTHER), Set.of(Sensation.REDNESS), Set.of(Situation.NEW_PRODUCT)
        );

        assertThat(scorer.rank(query, List.of(weakCandidate))).isEmpty();
    }

    @Test
    void higherScoreWinsAndTiesKeepGivenOrder() {
        SimilarExperienceQuery query = query(
                BodyArea.RIGHT_CHIN,
                Set.of(Appearance.APP_REDNESS, Appearance.APP_BUMP),
                Set.of(Sensation.BREAKOUT),
                Set.of(Situation.SHAVING)
        );
        SkinReport recentTie = report(
                BodyArea.RIGHT_CHIN, Set.of(Appearance.APP_REDNESS), Set.of(Sensation.BREAKOUT), Set.of(Situation.SHAVING)
        );
        SkinReport olderTie = report(
                BodyArea.RIGHT_CHIN, Set.of(Appearance.APP_REDNESS), Set.of(Sensation.BREAKOUT), Set.of(Situation.SHAVING)
        );
        SkinReport best = report(
                BodyArea.RIGHT_CHIN,
                Set.of(Appearance.APP_REDNESS, Appearance.APP_BUMP),
                Set.of(Sensation.BREAKOUT),
                Set.of(Situation.SHAVING)
        );

        List<ScoredSimilarExperience> ranked = scorer.rank(query, List.of(recentTie, olderTie, best));

        assertThat(ranked).extracting(ScoredSimilarExperience::reportId)
                .containsExactly(best.getId(), recentTie.getId(), olderTie.getId());
    }

    private SimilarExperienceQuery query(
            BodyArea primaryArea,
            Set<Appearance> appearances,
            Set<Sensation> sensations,
            Set<Situation> situations
    ) {
        return new SimilarExperienceQuery(
                primaryArea, appearances, sensations, situations, ResultType.SELF_CARE_GUIDE
        );
    }

    private SkinReport report(
            BodyArea primaryArea,
            Set<Appearance> appearances,
            Set<Sensation> sensations,
            Set<Situation> situations
    ) {
        return SkinReport.create(
                UUID.randomUUID(),
                LocalDate.of(2026, 8, 1),
                new byte[]{1},
                primaryArea,
                null,
                CareAvailability.ALREADY_WASHED,
                ResultType.SELF_CARE_GUIDE,
                LocalDateTime.of(2026, 8, 2, 0, 0),
                LocalDateTime.of(2026, 8, 4, 0, 0),
                appearances,
                sensations,
                situations,
                Set.of(PreCareCheck.NONE)
        );
    }
}
