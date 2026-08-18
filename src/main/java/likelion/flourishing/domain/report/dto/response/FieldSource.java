package likelion.flourishing.domain.report.dto.response;

/**
 * 구조화 결과의 각 값이 어디서 왔는지.
 *
 * <p>사용자가 확인 화면에서 무엇을 검토해야 하는지 알려 주기 위한 값이다. MANUAL은 사용자가 이미
 * 고른 값이라 그대로 두면 되고, AI는 확인이 필요한 값이다.
 */
public enum FieldSource {

    /** 사용자가 직접 고른 값. AI가 읽어 낸 값보다 앞선다. */
    MANUAL,

    /** AI가 원문에서 읽어 낸 값. */
    AI,

    /** 양쪽 모두 값이 없어 비워 둔 자리. */
    NONE
}
