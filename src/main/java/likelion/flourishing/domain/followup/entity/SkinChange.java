package likelion.flourishing.domain.followup.entity;

/**
 * 다음 날 피부가 어떻게 달라졌는지. 명세 v2_1 SkinChange와 값이 같다.
 *
 * <p>중증도나 호전 여부를 판정하는 값이 아니라 사용자가 느낀 변화를 그대로 받는 값이다.
 *
 * <p>v2_1에서 NEW_AREA(새로운 부위에도 생겼어요)와 UNSURE(잘 모르겠어요)가 빠져 세 개가 됐다.
 * 이미 저장된 기록에 두 값이 남아 있으면 새 CHECK 제약을 만들 수 없으므로 배포 전
 * {@code db/migration/2026-08-18_v2_1_follow_ups.sql}을 먼저 돌려야 한다. 같은 스크립트가
 * action_completion이 NULL인 v1 CLINICIAN_CHECK 행도 함께 옮긴다.
 */
public enum SkinChange {
    IMPROVED,
    SIMILAR,
    WORSENED
}
