package likelion.flourishing.domain.report.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 겉모습.
 *
 * <p>명세 v2_1은 이 목록을 확정하지 못해 {@code AppearanceSelection.items.enum}을 비워 두었다.
 * 아래 여섯 개는 팀이 따로 확정한 값이며, 명세가 채워질 때까지 이 enum과
 * {@code GET /v1/reference-data/skin-report-options}의 응답이 유일한 원본이다.
 *
 * <p><b>명세 TODO와 어긋나는 지점이 셋 있다.</b> 이슈 #28에서 확인 중이며, 결론에 따라 값이
 * 바뀔 수 있다. 지금은 확정 목록을 그대로 반영해 두었다.
 *
 * <ol>
 *   <li>TODO 2번은 진물(OOZING)을 반드시 포함하라고 한다. 관리 전 확인
 *       {@code PUS_OOZING_BLISTER} 자동 승격 로직이 그 값에 묶여 있어서다. 확정 목록에는
 *       진물이 없고 {@link #APP_PUS_BUMP}만 있다.
 *   <li>TODO 3번은 딱지(CRUST)와 진물의 분리를 유지하라고 한다. 확정 목록에는 둘 다 없다.
 *   <li>TODO 4번은 REDNESS가 {@link Sensation}으로 옮겨졌으니 겉모습에서 중복 정의하지 말라고
 *       한다. {@link #APP_REDNESS}는 {@code Sensation.REDNESS}와 라벨까지 같다. 사용자가 같은
 *       화면에서 "붉어짐"을 두 번 보게 되고, 유사도 계산에서 겉모습 +1과 불편 +1로 두 번 가산된다.
 * </ol>
 *
 * <p>값에 {@code APP_} 접두사가 붙은 것도 다른 선택값 enum과 다르다. 3번을 정리하면 접두사를
 * 붙일 이유가 없어진다.
 *
 * <p>유사도 계산에서 항목당 +1점이다.
 */
@Getter
@RequiredArgsConstructor
public enum Appearance {

    APP_REDNESS("붉어짐"),
    APP_BUMP("돌기·울퉁불퉁함"),
    APP_PUS_BUMP("고름이 찬 돌기"),
    APP_DRYNESS("건조·각질"),
    APP_OILINESS("번들거림·유분"),
    APP_OTHER("기타");

    /** 명세 AppearanceSelection.maxItems. */
    public static final int MAX_SELECTIONS = 6;

    private final String label;
}
