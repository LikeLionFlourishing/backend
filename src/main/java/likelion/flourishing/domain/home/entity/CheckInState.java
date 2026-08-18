package likelion.flourishing.domain.home.entity;

/**
 * 하루 상태. 명세 DailyCheckIn.state와 값이 같다.
 *
 * <p>NO_DISCOMFORT는 사용자가 직접 "오늘 불편 없음"을 저장한 것이고,
 * SKIN_REPORT는 같은 날 피부 보고가 확정되어 서버가 교체한 것이다.
 * 미응답은 행 자체를 만들지 않는다. 불편이 없었는지 답을 안 한 것인지 구분해야 하기 때문이다.
 */
public enum CheckInState {
    NO_DISCOMFORT,
    SKIN_REPORT
}
