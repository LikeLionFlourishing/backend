package likelion.flourishing.report.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class SkinReportOptionEnumTest {

    @Test
    void bodyAreasMatchApiContract() {
        assertThat(Arrays.stream(BodyArea.values()).map(Enum::name))
                .containsExactly(
                        "LEFT_FOREHEAD", "CENTER_FOREHEAD", "RIGHT_FOREHEAD",
                        "NOSE", "LEFT_CHEEK", "RIGHT_CHEEK", "AROUND_MOUTH",
                        "LEFT_CHIN", "RIGHT_CHIN", "LEFT_JAWLINE",
                        "RIGHT_JAWLINE", "NECK", "OTHER"
                );
        assertThat(Arrays.stream(BodyArea.values()).map(BodyArea::getLabel))
                .containsExactly(
                        "왼쪽 이마", "가운데 이마", "오른쪽 이마",
                        "코", "왼쪽 볼", "오른쪽 볼", "입 주변",
                        "왼쪽 턱", "오른쪽 턱", "왼쪽 턱선",
                        "오른쪽 턱선", "목", "기타"
                );
    }

    @Test
    void appearancesMatchApiContract() {
        assertThat(Arrays.stream(Appearance.values()).map(Enum::name))
                .containsExactly(
                        "REDNESS", "SMALL_BUMPS", "WHITE_TIPPED_BUMPS",
                        "RED_BUMPS_AROUND_HAIR", "ROUGHNESS_FLAKING",
                        "OOZING", "CRUST", "UNSURE"
                );
        assertThat(Arrays.stream(Appearance.values()).map(Appearance::getLabel))
                .containsExactly(
                        "붉어짐", "작은 돌기", "하얀 끝이 보이는 돌기",
                        "털 주변의 붉은 돌기", "거칠어짐·각질",
                        "진물", "딱지", "잘 모르겠음"
                );
    }

    @Test
    void sensationsMatchApiContract() {
        assertThat(Arrays.stream(Sensation.values()).map(Enum::name))
                .containsExactly(
                        "ITCHING", "STINGING_BURNING", "PAIN_WHEN_PRESSED",
                        "PAIN_AT_REST", "HEAT", "TIGHTNESS", "NONE"
                );
        assertThat(Arrays.stream(Sensation.values()).map(Sensation::getLabel))
                .containsExactly(
                        "가려움", "따가움·화끈거림", "누르면 아픔",
                        "가만히 있어도 아픔", "열감", "당김", "특별한 느낌 없음"
                );
    }

    @Test
    void situationsMatchApiContract() {
        assertThat(Arrays.stream(Situation.values()).map(Enum::name))
                .containsExactly(
                        "SHAVING", "SWEAT_OR_DUST_AFTER_TRAINING",
                        "PROTECTIVE_GEAR_OR_MASK", "DELAYED_WASHING",
                        "NEW_PRODUCT", "TOUCHED_OR_SQUEEZED",
                        "SLEEP_DEPRIVATION", "OTHER", "NONE_RECALLED"
                );
        assertThat(Arrays.stream(Situation.values()).map(Situation::getLabel))
                .containsExactly(
                        "면도", "훈련·운동 후 땀 또는 먼지",
                        "보호장비·마스크 착용", "세안·샤워 지연",
                        "새로운 제품 사용", "피부를 만지거나 짬",
                        "수면 부족", "기타", "특별히 떠오르는 상황 없음"
                );
    }

    @Test
    void careAvailabilityValuesMatchApiContract() {
        assertThat(Arrays.stream(CareAvailability.values()).map(Enum::name))
                .containsExactly(
                        "BEFORE_WASH_CAN_WASH_LATER", "ALREADY_WASHED",
                        "CAN_CARE_BEFORE_SLEEP", "ADDITIONAL_CARE_DIFFICULT"
                );
        assertThat(Arrays.stream(CareAvailability.values()).map(CareAvailability::getLabel))
                .containsExactly(
                        "아직 세안·샤워 전이며 이후 가능", "이미 세안·샤워함",
                        "취침 전에 관리 가능", "오늘은 추가 관리가 어려움"
                );
    }

    @Test
    void preCareChecksMatchApiContract() {
        assertThat(Arrays.stream(PreCareCheck.values()).map(Enum::name))
                .containsExactly(
                        "SPREADING_RAPIDLY", "SEVERE_PAIN_HEAT_SWELLING",
                        "PUS_OOZING_BLISTER", "NONE"
                );
        assertThat(Arrays.stream(PreCareCheck.values()).map(PreCareCheck::getLabel))
                .containsExactly(
                        "짧은 시간에 빠르게 넓어지고 있어요.",
                        "평소보다 통증·열감·붓기가 심해요.",
                        "고름·진물·물집이 보여요.",
                        "해당하는 변화가 없어요."
                );
    }
}
