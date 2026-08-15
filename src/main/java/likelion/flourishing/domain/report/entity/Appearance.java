package likelion.flourishing.domain.report.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Appearance {

    REDNESS("붉어짐"),
    SMALL_BUMPS("작은 돌기"),
    WHITE_TIPPED_BUMPS("하얀 끝이 보이는 돌기"),
    RED_BUMPS_AROUND_HAIR("털 주변의 붉은 돌기"),
    ROUGHNESS_FLAKING("거칠어짐·각질"),
    OOZING("진물"),
    CRUST("딱지"),
    UNSURE("잘 모르겠음");

    private final String label;
}
