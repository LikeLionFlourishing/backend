package likelion.flourishing.domain.report.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Sensation {

    ITCHING("가려움"),
    STINGING_BURNING("따가움·화끈거림"),
    PAIN_WHEN_PRESSED("누르면 아픔"),
    PAIN_AT_REST("가만히 있어도 아픔"),
    HEAT("열감"),
    TIGHTNESS("당김"),
    NONE("특별한 느낌 없음");

    private final String label;
}
