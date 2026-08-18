package likelion.flourishing.domain.report.dto.response;

/**
 * 구조화 처리 상태.
 *
 * <p>FAILED도 200으로 나간다. 사용자가 직접 고르면 계속 진행할 수 있는 상태이고, 클라이언트는
 * 오류 화면이 아니라 선택 화면을 그대로 보여 주면 된다. 오류로 내려보내면 프런트가 실패 처리를
 * 하게 되어 정상 흐름이 끊긴다.
 */
public enum ProcessingStatus {
    SUCCEEDED,
    FAILED
}
