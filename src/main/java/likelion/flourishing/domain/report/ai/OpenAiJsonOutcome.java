package likelion.flourishing.domain.report.ai;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 구조화 응답 호출 결과. 예외를 던지지 않고 성공과 실패를 같은 타입으로 돌린다.
 *
 * <p>AI 실패는 예외 상황이 아니라 명세가 정의한 정상 흐름이다. 호출한 쪽이 실패를 반드시
 * 다루도록 결과 객체로 강제한다.
 *
 * @param payload     성공했을 때 모델이 만든 JSON. 실패면 null이다.
 * @param failureCode 실패했을 때의 사유. 성공이면 null이다.
 */
public record OpenAiJsonOutcome(JsonNode payload, AiFailureCode failureCode) {

    public static OpenAiJsonOutcome succeeded(JsonNode payload) {
        return new OpenAiJsonOutcome(payload, null);
    }

    public static OpenAiJsonOutcome failed(AiFailureCode failureCode) {
        return new OpenAiJsonOutcome(null, failureCode);
    }

    public boolean isSucceeded() {
        return failureCode == null && payload != null;
    }
}
