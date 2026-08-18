package likelion.flourishing.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Set;
import likelion.flourishing.domain.report.entity.Appearance;
import likelion.flourishing.domain.report.entity.PreCareCheck;
import likelion.flourishing.domain.report.entity.ResultType;
import likelion.flourishing.domain.report.entity.Sensation;
import likelion.flourishing.domain.report.entity.Situation;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

/**
 * 서버가 정하는 값과 조합 규칙 테스트.
 *
 * <p>확인하는 것: 위험 신호 하나로 결과 유형이 의료진 확인으로 넘어가는지, 하루 경계가
 * Asia/Seoul인지, 배타 선택을 다른 값과 함께 보낼 수 없는지.
 */
class SkinReportPolicyTest {

    @Test
    void riskSignalMakesClinicianCheck() {
        ResultType resultType = SkinReportPolicy.decideResultType(
                Set.of(PreCareCheck.SPREADING_RAPIDLY)
            );

        assertThat(resultType).isEqualTo(ResultType.CLINICIAN_CHECK);
    }

    @Test
    void onlyNoneMakesSelfCareGuide() {
        ResultType resultType = SkinReportPolicy.decideResultType(Set.of(PreCareCheck.NONE));

        assertThat(resultType).isEqualTo(ResultType.SELF_CARE_GUIDE);
    }

    /** UTC로 날짜를 끊으면 한국 시간 오전 9시 이전이 전날로 잡힌다. */
    @Test
    void todayFollowsSeoulDateNotUtcDate() {
        Clock beforeSeoulNoon = Clock.fixed(Instant.parse("2026-08-15T16:30:00Z"), ZoneOffset.UTC);

        assertThat(SkinReportPolicy.today(beforeSeoulNoon)).isEqualTo(LocalDate.of(2026, 8, 16));
    }

    @Test
    void followUpWindowOpensNextDayInSeoulAndStaysOpenTwoDays() {
        LocalDate reportDate = LocalDate.of(2026, 8, 15);

        LocalDateTime availableAt = SkinReportPolicy.followUpAvailableAt(reportDate);
        LocalDateTime expiresAt = SkinReportPolicy.followUpExpiresAt(reportDate);

        // 2026-08-16T00:00+09:00 == 2026-08-15T15:00Z
        assertThat(availableAt).isEqualTo(LocalDateTime.of(2026, 8, 15, 15, 0));
        assertThat(expiresAt).isEqualTo(LocalDateTime.of(2026, 8, 17, 15, 0));
        assertThat(expiresAt).isAfter(availableAt);
    }

    @Test
    void noneRecalledCannotBeChosenWithOtherSituations() {
        assertThatThrownBy(() -> SkinReportPolicy.assertExclusiveSelections(
                Set.of(Situation.NONE_RECALLED, Situation.SHAVING),
                Set.of(PreCareCheck.NONE)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SELECTION_COMBINATION_INVALID);
    }

    @Test
    void noneCannotBeChosenWithRiskSignals() {
        assertThatThrownBy(() -> SkinReportPolicy.assertExclusiveSelections(
                Set.of(Situation.SHAVING),
                Set.of(PreCareCheck.NONE, PreCareCheck.PUS_OOZING_BLISTER)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SELECTION_COMBINATION_INVALID);
    }

    @Test
    void severalNonExclusiveValuesArePermitted() {
        SkinReportPolicy.assertExclusiveSelections(
                Set.of(Situation.SHAVING, Situation.NEW_PRODUCT),
                Set.of(PreCareCheck.SPREADING_RAPIDLY, PreCareCheck.SEVERE_PAIN_HEAT_SWELLING)
        );
    }

    /**
     * 명세 v2_1이 겉모습과 느껴지는 불편에서 단독 선택 개념을 걷어냈다. 두 그룹은 이제 어떤
     * 조합이든 허용되며, 정책이 그 값을 아예 받지 않는다는 것을 여기서 고정한다.
     */
    @Test
    void appearancesAndSensationsNoLongerHaveAnExclusiveValue() {
        assertThat(Arrays.stream(Appearance.values()).map(Enum::name))
                .doesNotContain("UNSURE");
        assertThat(Arrays.stream(Sensation.values()).map(Enum::name))
                .doesNotContain("NONE");
    }
}
