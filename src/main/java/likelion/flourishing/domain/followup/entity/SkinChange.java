package likelion.flourishing.domain.followup.entity;

/**
 * 다음 날 피부가 어떻게 달라졌는지. 명세 SkinChange와 값이 같다.
 *
 * <p>중증도나 호전 여부를 판정하는 값이 아니라 사용자가 느낀 변화를 그대로 받는 값이다.
 * 확신이 없으면 UNSURE를 고를 수 있게 해서 억지로 고르지 않도록 한다.
 */
public enum SkinChange {
    IMPROVED,
    SIMILAR,
    WORSENED,
    NEW_AREA,
    UNSURE
}
