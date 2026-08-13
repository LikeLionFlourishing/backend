package likelion.flourishing.report.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Situation {

    SHAVING("면도"),
    SWEAT_OR_DUST_AFTER_TRAINING("훈련·운동 후 땀 또는 먼지"),
    PROTECTIVE_GEAR_OR_MASK("보호장비·마스크 착용"),
    DELAYED_WASHING("세안·샤워 지연"),
    NEW_PRODUCT("새로운 제품 사용"),
    TOUCHED_OR_SQUEEZED("피부를 만지거나 짬"),
    SLEEP_DEPRIVATION("수면 부족"),
    OTHER("기타"),
    NONE_RECALLED("특별히 떠오르는 상황 없음");

    private final String label;
}
