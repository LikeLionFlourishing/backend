package likelion.flourishing.domain.followup.entity;

/**
 * 안내받은 행동을 얼마나 실행했는지. 다 실행했어요 / 일부만 실행했어요 / 실행하지 못했어요.
 *
 * <p>명세 v2_1에서 두 종류의 경과가 모두 이 값을 받는다. 의료진 확인 안내를 받은 경우에도
 * "확인했는지"와 "안내받은 행동을 실행했는지"는 다른 질문이라 따로 묻는다.
 */
public enum ActionCompletion {
    MOSTLY_DONE,
    PARTLY_DONE,
    NOT_DONE
}
