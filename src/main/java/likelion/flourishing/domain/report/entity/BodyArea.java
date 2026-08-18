package likelion.flourishing.domain.report.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BodyArea {

    LEFT_FOREHEAD("왼쪽 이마"),
    CENTER_FOREHEAD("가운데 이마"),
    RIGHT_FOREHEAD("오른쪽 이마"),
    NOSE("코"),
    LEFT_CHEEK("왼쪽 볼"),
    RIGHT_CHEEK("오른쪽 볼"),
    AROUND_MOUTH("입 주변"),
    LEFT_CHIN("왼쪽 턱"),
    RIGHT_CHIN("오른쪽 턱"),
    LEFT_JAWLINE("왼쪽 턱선"),
    RIGHT_JAWLINE("오른쪽 턱선"),
    NECK("목"),
    OTHER("기타");

    private final String label;
}
