package likelion.flourishing.report.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CareAvailability {

    BEFORE_WASH_CAN_WASH_LATER("아직 세안·샤워 전이며 이후 가능"),
    ALREADY_WASHED("이미 세안·샤워함"),
    CAN_CARE_BEFORE_SLEEP("취침 전에 관리 가능"),
    ADDITIONAL_CARE_DIFFICULT("오늘은 추가 관리가 어려움");

    private final String label;
}
