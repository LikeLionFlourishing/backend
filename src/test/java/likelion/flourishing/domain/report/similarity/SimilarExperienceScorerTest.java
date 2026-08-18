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

        // 부위 3 + 겉모습 1 + 불편 1 + 직전 상황 2 + 관리 상태 1 = 8
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

        // 부위 3 + 겉모습 상한 3 + 불편 1 + 관리 상태 1 = 8. 겉모습 네 개가 4점이 되지 않는다.
        // 직전 상황은 NONE_RECALLED라 빠진다.
        assertThat(scorer.score(query, candidate)).isEqualTo(8);
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

        // 부위가 다르고 상황은 NONE_RECALLED라 빠진다. 겉모습 1 + 불편 1 + 관리 상태 1 = 3
        assertThat(scorer.score(query, candidate)).isEqualTo(3);
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

    /**
     * 명세 SimilarExperience.similarityScore가 정한 배점을 항목별로 고정한다.
     *
     * <p>겉모습과 직전 상황의 가중치가 서로 뒤바뀐 채로 있었고 마지막 항이 다른 필드였다.
     * 상수만 보고는 알아차리기 어려워 각 항목을 하나씩 떼어 확인한다.
     */
    @Test
    void eachWeightMatchesTheSpec() {
        Set<Appearance> none = Set.of();
        Set<Sensation> noSensation = Set.of();
        Set<Situation> noSituation = Set.of();

        // 같은 대표 부위 +3 (관리 상태는 다르게 둬서 섞이지 않게 한다)
        assertThat(scorer.score(
                new SimilarExperienceQuery(BodyArea.NOSE, none, noSensation, noSituation,
                        CareAvailability.ALREADY_WASHED),
                report(BodyArea.NOSE, none, noSensation, noSituation, CareAvailability.CAN_CARE_BEFORE_SLEEP)
        )).isEqualTo(3);

        // 같은 겉모습 항목당 +1
        assertThat(scorer.score(
                new SimilarExperienceQuery(BodyArea.NOSE, Set.of(Appearance.APP_REDNESS), noSensation, noSituation,
                        CareAvailability.ALREADY_WASHED),
                report(BodyArea.NECK, Set.of(Appearance.APP_REDNESS), noSensation, noSituation,
                        CareAvailability.CAN_CARE_BEFORE_SLEEP)
        )).isEqualTo(1);

        // 같은 느껴지는 불편 항목당 +1
        assertThat(scorer.score(
                new SimilarExperienceQuery(BodyArea.NOSE, none, Set.of(Sensation.BREAKOUT), noSituation,
                        CareAvailability.ALREADY_WASHED),
                report(BodyArea.NECK, none, Set.of(Sensation.BREAKOUT), noSituation,
                        CareAvailability.CAN_CARE_BEFORE_SLEEP)
        )).isEqualTo(1);

        // 같은 직전 상황 항목당 +2 — 가장 큰 가중치
        assertThat(scorer.score(
                new SimilarExperienceQuery(BodyArea.NOSE, none, noSensation, Set.of(Situation.SHAVING),
                        CareAvailability.ALREADY_WASHED),
                report(BodyArea.NECK, none, noSensation, Set.of(Situation.SHAVING),
                        CareAvailability.CAN_CARE_BEFORE_SLEEP)
        )).isEqualTo(2);

        // 같은 현재 관리 상태 +1
        assertThat(scorer.score(
                new SimilarExperienceQuery(BodyArea.NOSE, none, noSensation, noSituation,
                        CareAvailability.ALREADY_WASHED),
                report(BodyArea.NECK, none, noSensation, noSituation, CareAvailability.ALREADY_WASHED)
        )).isEqualTo(1);
    }

    /** 직전 상황 상한이 겉모습 상한보다 커야 명세가 말한 "가장 큰 가중치"가 유지된다. */
    @Test
    void situationCapStaysAboveAppearanceCap() {
        Set<Situation> threeSituations = Set.of(
                Situation.SHAVING, Situation.NEW_PRODUCT, Situation.SWEAT_OR_SEBUM
        );
        Set<Appearance> fourAppearances = Set.of(
                Appearance.APP_REDNESS, Appearance.APP_BUMP, Appearance.APP_DRYNESS, Appearance.APP_OILINESS
        );

        int situationOnly = scorer.score(
                new SimilarExperienceQuery(BodyArea.NOSE, Set.of(), Set.of(), threeSituations,
                        CareAvailability.ALREADY_WASHED),
                report(BodyArea.NECK, Set.of(), Set.of(), threeSituations, CareAvailability.CAN_CARE_BEFORE_SLEEP)
        );
        int appearanceOnly = scorer.score(
                new SimilarExperienceQuery(BodyArea.NOSE, fourAppearances, Set.of(), Set.of(),
                        CareAvailability.ALREADY_WASHED),
                report(BodyArea.NECK, fourAppearances, Set.of(), Set.of(), CareAvailability.CAN_CARE_BEFORE_SLEEP)
        );

        assertThat(situationOnly).isEqualTo(6);
        assertThat(appearanceOnly).isEqualTo(3);
        assertThat(situationOnly).isGreaterThan(appearanceOnly);
    }

    private SimilarExperienceQuery query(
            BodyArea primaryArea,
            Set<Appearance> appearances,
            Set<Sensation> sensations,
            Set<Situation> situations
    ) {
        return new SimilarExperienceQuery(
                primaryArea, appearances, sensations, situations, CareAvailability.ALREADY_WASHED
        );
    }

    private SkinReport report(
            BodyArea primaryArea,
            Set<Appearance> appearances,
            Set<Sensation> sensations,
            Set<Situation> situations
    ) {
        return report(primaryArea, appearances, sensations, situations, CareAvailability.ALREADY_WASHED);
    }

    private SkinReport report(
            BodyArea primaryArea,
            Set<Appearance> appearances,
            Set<Sensation> sensations,
            Set<Situation> situations,
            CareAvailability careAvailability
    ) {
        return SkinReport.create(
                UUID.randomUUID(),
                LocalDate.of(2026, 8, 1),
                new byte[]{1},
                primaryArea,
                null,
                careAvailability,
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
