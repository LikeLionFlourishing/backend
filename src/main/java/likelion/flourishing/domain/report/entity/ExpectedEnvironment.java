package likelion.flourishing.domain.report.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 예상 환경. 관리규칙 v0.3의 ENV-* 규칙이 보는 값이다.
 *
 * <p>매 기록마다 받지 않는다. 규칙 문서가 온보딩에서 1회 설정하고 이후 변경 가능한 값으로 정했다.
 * 하루의 상황({@link Situation})과 달리 한동안 유지되는 생활 조건이라 보고마다 다시 물으면
 * 같은 답을 반복해서 받게 된다.
 *
 * <p>확정 명세 v2.0.0에는 이 값을 받는 필드가 아직 없다. 그래서 지금은 규칙 조건에서 쓸 수 있게
 * 값만 정의해 두고, 사용자 입력은 비어 있는 상태로 평가한다. 비어 있으면 ENV 규칙은 걸리지 않고
 * 환경 보정 없이 진행한다. 규칙 문서 11-1이 예상 환경을 선택값으로 둔 것과 같은 동작이다.
 *
 * <p>{@link #HOT_AND_HUMID}는 기온·습도 수치로 자동 판정하지 않는다. 규칙 문서가 사용자가
 * 직접 고른 경우에만 적용하도록 제외 조건에 못 박았다.
 */
@Getter
@RequiredArgsConstructor
public enum ExpectedEnvironment {

    /** ENV-001. 야외훈련·행군 등 땀·먼지·자외선 노출이 있는 환경. */
    OUTDOOR_TRAINING("야외활동·훈련"),

    /** ENV-002. 야간근무·당직 등 수면·생활시간이 달라지는 환경. */
    NIGHT_OR_SHIFT_DUTY("야간·교대 일정"),

    /** ENV-003. 여름철·고온다습한 실내외 환경. */
    HOT_AND_HUMID("덥고 습한 환경");

    private final String label;
}
