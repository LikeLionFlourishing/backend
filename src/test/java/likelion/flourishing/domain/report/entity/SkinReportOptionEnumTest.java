package likelion.flourishing.domain.report.entity;

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

    /**
     * 명세 v2_1의 AppearanceSelection은 enum이 비어 있고, 이 여섯 개는 팀이 따로 확정한 값이다.
     * 명세 TODO와 어긋나는 지점 셋은 이슈 #28에서 확인 중이라 값이 바뀔 수 있다.
     */
    @Test
    void appearancesMatchApiContract() {
        assertThat(Arrays.stream(Appearance.values()).map(Enum::name))
                .containsExactly(
                        "APP_REDNESS", "APP_BUMP", "APP_PUS_BUMP",
                        "APP_DRYNESS", "APP_OILINESS", "APP_OTHER"
                );
        assertThat(Arrays.stream(Appearance.values()).map(Appearance::getLabel))
                .containsExactly(
                        "붉어짐", "돌기·울퉁불퉁함", "고름이 찬 돌기",
                        "건조·각질", "번들거림·유분", "기타"
                );
    }

    /** v1 감각 7개는 v2_1 불편 유형 3개로 전면 대체됐다. 값 사이에 대응 관계가 없다. */
    @Test
    void sensationsMatchApiContract() {
        assertThat(Arrays.stream(Sensation.values()).map(Enum::name))
                .containsExactly("REDNESS", "EXCESS_SEBUM", "BREAKOUT");
        assertThat(Arrays.stream(Sensation.values()).map(Sensation::getLabel))
                .containsExactly("붉어짐", "피지 과다 분비", "트러블");
    }

    @Test
    void situationsMatchApiContract() {
        assertThat(Arrays.stream(Situation.values()).map(Enum::name))
                .containsExactly(
                        "PROTECTIVE_GEAR_OR_MASK", "SHAVING", "SQUEEZED_ACNE",
                        "NEW_PRODUCT", "SWEAT_OR_SEBUM", "NONE_RECALLED"
                );
        assertThat(Arrays.stream(Situation.values()).map(Situation::getLabel))
                .containsExactly(
                        "보호장비·마스크 착용", "면도", "여드름을 짬",
                        "새 제품 사용", "땀·과피지", "해당 상황 없음"
                );
    }

    /** v1 값이 되살아나면 계약이 깨진다. 마이그레이션 대상이라 이름으로 고정해 둔다. */
    @Test
    void valuesRemovedInSpecV2AreGone() {
        assertThat(Arrays.stream(Sensation.values()).map(Enum::name))
                .doesNotContain("ITCHING", "STINGING_BURNING", "PAIN_WHEN_PRESSED",
                        "PAIN_AT_REST", "HEAT", "TIGHTNESS", "NONE");
        assertThat(Arrays.stream(Situation.values()).map(Enum::name))
                .doesNotContain("DELAYED_WASHING", "SLEEP_DEPRIVATION", "OTHER",
                        "SWEAT_OR_DUST_AFTER_TRAINING", "TOUCHED_OR_SQUEEZED");
    }

    /** 선택 개수 상한은 명세가 정한 값이다. 검증 코드가 이 상수를 참조한다. */
    @Test
    void selectionLimitsMatchApiContract() {
        assertThat(Sensation.MAX_SELECTIONS).isEqualTo(3);
        assertThat(Situation.MAX_SELECTIONS).isEqualTo(5);
        assertThat(Appearance.MAX_SELECTIONS).isEqualTo(6);
    }

    /** NONE_RECALLED는 단독 선택이다. 다른 상황과 함께 고를 수 없다. */
    @Test
    void onlyNoneRecalledIsExclusiveAmongSituations() {
        assertThat(Arrays.stream(Situation.values()).filter(Situation::isExclusive))
                .containsExactly(Situation.NONE_RECALLED);
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
