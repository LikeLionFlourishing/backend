package likelion.flourishing.domain.followup.entity;

/**
 * 경과의 종류. 명세 FollowUp의 discriminator인 kind와 값이 같다.
 *
 * <p>원 보고의 결과 유형에 따라 물어보는 것이 다르기 때문에 나뉜다. 관리 안내를 받았으면
 * 안내를 얼마나 지켰는지를, 진료 확인 안내를 받았으면 진료를 봤는지를 묻는다.
 *
 * <p>보고의 result_type과 값 이름이 다르다는 점에 주의한다.
 * SELF_CARE_GUIDE 보고에는 SELF_CARE 경과가, CLINICIAN_CHECK 보고에는 같은 이름의 경과가 붙는다.
 */
public enum FollowUpKind {
    SELF_CARE,
    CLINICIAN_CHECK;

    /**
     * 보고의 result_type 문자열에 대응하는 경과 종류.
     *
     * <p>모르는 값은 조용히 한쪽으로 몰지 않고 예외로 드러낸다. 그러지 않으면 나중에 Reports가
     * result_type을 늘렸을 때 새 값이 전부 CLINICIAN_CHECK로 매핑되어, 종류가 맞지 않는 경과가
     * FOLLOW_UP_KIND_MISMATCH 검사를 그대로 통과한다. 여기서 터지면 스키마의 CHECK와 이 코드를
     * 함께 고치게 된다.
     */
    public static FollowUpKind forResultType(String resultType) {
        return switch (resultType == null ? "" : resultType) {
            case "SELF_CARE_GUIDE" -> SELF_CARE;
            case "CLINICIAN_CHECK" -> CLINICIAN_CHECK;
            default -> throw new IllegalStateException("알 수 없는 보고 결과 유형입니다: " + resultType);
        };
    }
}
