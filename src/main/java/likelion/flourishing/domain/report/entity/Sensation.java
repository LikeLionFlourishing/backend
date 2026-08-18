package likelion.flourishing.domain.report.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 느껴지는 불편.
 *
 * <p>명세 v2_1에서 의미가 통째로 바뀌었다. v1은 "감각"(가려움, 따가움, 통증 같은 것)을 물었고
 * v2_1은 "불편 유형"을 묻는다. 값 사이에 대응 관계가 없어 v1 기록을 v2_1 값으로 옮길 수 없다.
 * 기존 데이터는 별도 보존 컬럼으로 옮기고 유사도 계산에서 제외한다.
 *
 * <p>v1의 NONE(특별한 느낌 없음)이 사라져 최소 한 개는 반드시 골라야 한다. 불편이 없으면
 * 피부 보고 자체를 하지 않고 홈의 "오늘 불편 없음"으로 남기기 때문이다.
 *
 * <p>유사도 계산에서 항목당 +1점이다.
 */
@Getter
@RequiredArgsConstructor
public enum Sensation {

    REDNESS("붉어짐"),
    EXCESS_SEBUM("피지 과다 분비"),
    BREAKOUT("트러블");

    /** 명세 SensationSelection.maxItems. */
    public static final int MAX_SELECTIONS = 3;

    private final String label;
}
