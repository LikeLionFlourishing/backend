package likelion.flourishing.domain.report.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 직전 상황.
 *
 * <p>명세 v2_1에서 9개가 6개로 줄었다. 삭제된 값은 DELAYED_WASHING(세안·샤워 지연),
 * SLEEP_DEPRIVATION(수면 부족), OTHER(기타)다. 둘은 이름이 바뀌었다.
 *
 * <ul>
 *   <li>SWEAT_OR_DUST_AFTER_TRAINING(훈련 후 땀) → {@link #SWEAT_OR_SEBUM}(땀·과피지)
 *   <li>TOUCHED_OR_SQUEEZED(만지거나 짬) → {@link #SQUEEZED_ACNE}(여드름을 짬)
 * </ul>
 *
 * <p>유사도 계산에서 항목당 +2점으로 가장 큰 가중치를 가진다. 관리 규칙 매칭의 주 축이기도 하다.
 *
 * <p>{@link #NONE_RECALLED}는 단독 선택이다. 떠오르는 상황이 없다는 말과 특정 상황을 함께
 * 고르는 것은 뜻이 맞지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum Situation {

    PROTECTIVE_GEAR_OR_MASK("보호장비·마스크 착용"),
    SHAVING("면도"),
    SQUEEZED_ACNE("여드름을 짬"),
    NEW_PRODUCT("새 제품 사용"),
    SWEAT_OR_SEBUM("땀·과피지"),
    NONE_RECALLED("해당 상황 없음");

    /** 명세 SituationSelection.maxItems. NONE_RECALLED가 단독이라 실질 최대는 5개다. */
    public static final int MAX_SELECTIONS = 5;

    private final String label;

    /** 다른 값과 함께 고를 수 없는 값인지. */
    public boolean isExclusive() {
        return this == NONE_RECALLED;
    }
}
