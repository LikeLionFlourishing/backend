package likelion.flourishing.domain.report.dto.response;

/**
 * 구조화 처리 결과. 명세 ReportInterpretation.processingStatus 와 값이 같아야 한다.
 *
 * <p>AI가 실패해도 응답 자체는 200이다. 사용자가 직접 고르면 그대로 진행할 수 있기 때문이다.
 */
public enum ProcessingStatus {
    SUCCESS,
    FAILED
}
