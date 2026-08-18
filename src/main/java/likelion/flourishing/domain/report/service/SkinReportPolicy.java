package likelion.flourishing.domain.report.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;
import likelion.flourishing.domain.report.entity.Appearance;
import likelion.flourishing.domain.report.entity.PreCareCheck;
import likelion.flourishing.domain.report.entity.ResultType;
import likelion.flourishing.domain.report.entity.Sensation;
import likelion.flourishing.domain.report.entity.Situation;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;

/**
 * 보고를 저장할 때 서버가 정하는 값과 서버가 지키는 조합 규칙.
 *
 * <p>날짜와 결과 유형을 요청에서 받지 않는 이유가 여기에 있다. 날짜는 사용자가 사는 시간대의
 * 오늘이어야 하고, 결과 유형은 관리 전 확인값이 정한다. 둘 중 하나라도 요청 값을 믿으면 위험
 * 신호가 있는데도 일반 관리로 저장되거나 지난 날짜에 보고가 끼어들 수 있다.
 */
public final class SkinReportPolicy {

    /**
     * 하루 경계는 Asia/Seoul로 판단한다. 저장하는 시각은 UTC지만 "오늘"은 사용자가 사는 시간대
     * 기준이어야 한다. UTC로 날짜를 끊으면 한국 시간 오전 9시 이전이 전날로 잡힌다.
     */
    public static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    /** 경과를 입력할 수 있게 되는 날. 보고 다음 날이다. */
    private static final int FOLLOW_UP_OPEN_DAYS = 1;

    /** 경과 입력이 닫히는 날. 다음 날 하루를 다 쓰고 그 다음 날 자정까지 여유를 준다. */
    private static final int FOLLOW_UP_CLOSE_DAYS = 3;

    private SkinReportPolicy() {
    }

    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(SERVICE_ZONE));
    }

    /**
     * 결과 유형 결정.
     *
     * <p>관리 전 확인에서 위험 신호를 하나라도 골랐으면 의료진 확인이다. NONE만 골랐을 때에만
     * 일반 관리 안내로 간다. 판단을 사용자 선택 하나에 걸어 두어 어느 코드에서도 뒤집히지 않는다.
     */
    public static ResultType decideResultType(Set<PreCareCheck> preCareChecks) {
        boolean hasRiskSignal = preCareChecks.stream().anyMatch(check -> check != PreCareCheck.NONE);
        return hasRiskSignal ? ResultType.CLINICIAN_CHECK : ResultType.SELF_CARE_GUIDE;
    }

    /** 경과 입력 가능 시각. UTC LocalDateTime으로 돌려 DB에 그대로 저장한다. */
    public static LocalDateTime followUpAvailableAt(LocalDate reportDate) {
        return startOfDayUtc(reportDate.plusDays(FOLLOW_UP_OPEN_DAYS));
    }

    /** 경과 입력 기한. */
    public static LocalDateTime followUpExpiresAt(LocalDate reportDate) {
        return startOfDayUtc(reportDate.plusDays(FOLLOW_UP_CLOSE_DAYS));
    }

    /**
     * 함께 고를 수 없는 조합을 막는다.
     *
     * <p>"기억나는 게 없음"과 "해당하는 변화가 없음"은 다른 값과 같이 올 수 없다. 없다면서
     * 무엇이 있었다고 하는 답은 규칙 판단에서 서로 반대 방향으로 작용해 결과를 뒤집는다.
     * DDL은 행 단위 CHECK만 걸 수 있어 이 조합은 서비스가 지킨다.
     *
     * <p>겉모습과 느껴지는 불편은 받지 않는다. 명세 v2_1이 두 그룹에서 단독 선택 개념을
     * 걷어냈기 때문이다. AppearanceSelection 과 SensationSelection 에는 not/allOf 제약이 없고,
     * 짝이 되던 값(v1의 Appearance.UNSURE, Sensation.NONE)도 사라졌다. 남은 값끼리는 서로
     * 모순되지 않는다. "붉어짐"과 "기타"를 함께 고르는 것은 이상하지 않다.
     */
    public static void assertExclusiveSelections(
            Set<Situation> situations,
            Set<PreCareCheck> preCareChecks
    ) {
        assertExclusive(situations, Situation.NONE_RECALLED);
        assertExclusive(preCareChecks, PreCareCheck.NONE);
    }

    private static <E extends Enum<E>> void assertExclusive(Set<E> values, E exclusiveValue) {
        if (values.size() > 1 && values.contains(exclusiveValue)) {
            throw new BusinessException(ErrorCode.SELECTION_COMBINATION_INVALID);
        }
    }

    private static LocalDateTime startOfDayUtc(LocalDate date) {
        ZonedDateTime startOfDay = date.atStartOfDay(SERVICE_ZONE);
        return startOfDay.withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime();
    }
}
