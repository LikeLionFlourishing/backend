package likelion.flourishing.domain.home.entity;

/**
 * 홈 화면에서 먼저 보여줄 항목. 명세 Home.priority와 값이 같다.
 *
 * <p>명세에 판정 규칙이 적혀 있지 않아 enum에 적힌 순서를 우선순위로 해석했다.
 * 채워진 항목 중 가장 앞선 것을 고르고, 셋 다 비면 EMPTY다.
 *
 * <ol>
 *   <li>FOLLOW_UP — 미완료 경과가 있다. 입력 기한이 있어 가장 급하다.
 *   <li>TODAY_CHECK_IN — 오늘 상태가 이미 저장돼 있다.
 *   <li>RECENT_RECORD — 최근 기록이 있다.
 *   <li>EMPTY — 보여줄 것이 없다. 가입 직후가 여기 해당한다.
 * </ol>
 */
public enum HomePriority {
    FOLLOW_UP,
    TODAY_CHECK_IN,
    RECENT_RECORD,
    EMPTY
}
