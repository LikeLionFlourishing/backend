package likelion.flourishing.domain.report.ai;

/**
 * 구조화 결과. 실패해도 응답을 만들 수 있어야 해서 성공과 실패를 한 타입으로 표현한다.
 *
 * @param extracted   성공했을 때 AI가 읽어 낸 후보값. 실패면 빈 값이다.
 * @param failureCode 실패 사유. 성공이면 null이다.
 */
public record StructuringOutcome(ExtractedSelections extracted, AiFailureCode failureCode) {

    public static StructuringOutcome succeeded(ExtractedSelections extracted) {
        return new StructuringOutcome(extracted, null);
    }

    public static StructuringOutcome failed(AiFailureCode failureCode) {
        return new StructuringOutcome(ExtractedSelections.empty(), failureCode);
    }

    public boolean isSucceeded() {
        return failureCode == null;
    }
}
