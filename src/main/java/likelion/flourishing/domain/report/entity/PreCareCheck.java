package likelion.flourishing.domain.report.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PreCareCheck {

    SPREADING_RAPIDLY("짧은 시간에 빠르게 넓어지고 있어요."),
    SEVERE_PAIN_HEAT_SWELLING("평소보다 통증·열감·붓기가 심해요."),
    PUS_OOZING_BLISTER("고름·진물·물집이 보여요."),
    NONE("해당하는 변화가 없어요.");

    private final String label;
}
