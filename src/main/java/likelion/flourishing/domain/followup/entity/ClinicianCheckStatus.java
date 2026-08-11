package likelion.flourishing.domain.followup.entity;

/**
 * 진료 확인을 했는지. CLINICIAN_CHECK 경과에서만 쓴다.
 *
 * <p>PREFER_NOT_TO_RECORD가 있는 이유는 진료 여부를 남기고 싶지 않을 수 있어서다.
 * 이 서비스는 진단·처방 내용을 받지 않으므로 확인 여부만 묻고 그마저도 거절할 수 있게 한다.
 */
public enum ClinicianCheckStatus {
    CHECKED,
    NOT_YET,
    PREFER_NOT_TO_RECORD
}
