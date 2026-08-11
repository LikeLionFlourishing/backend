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

    /** 보고의 result_type 문자열에 대응하는 경과 종류. */
    public static FollowUpKind forResultType(String resultType) {
        return "SELF_CARE_GUIDE".equals(resultType) ? SELF_CARE : CLINICIAN_CHECK;
    }
}
