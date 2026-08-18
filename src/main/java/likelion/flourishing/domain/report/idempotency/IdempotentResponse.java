package likelion.flourishing.domain.report.idempotency;

import java.util.UUID;

/**
 * 컨트롤러가 그대로 내보낼 응답.
 *
 * <p>본문을 DTO가 아니라 직렬화된 JSON 문자열로 다닌다. 재전송에 저장된 응답을 되돌려 줄 때
 * 처음 보낸 것과 완전히 같은 본문이어야 하고, 다시 객체로 되돌렸다가 직렬화하면 필드 순서나
 * null 표현이 달라질 수 있기 때문이다.
 *
 * @param resourceId 만들어진 리소스 식별자. Location 헤더에 쓴다. 없는 작업이면 null이다.
 * @param replayed   저장된 응답을 되돌려 준 것인지. 로깅과 테스트에서 새 처리와 구분하는 데 쓴다.
 */
public record IdempotentResponse(int status, String jsonBody, UUID resourceId, boolean replayed) {

    public static IdempotentResponse created(String jsonBody, UUID resourceId) {
        return new IdempotentResponse(201, jsonBody, resourceId, false);
    }

    public static IdempotentResponse ok(String jsonBody, UUID resourceId) {
        return new IdempotentResponse(200, jsonBody, resourceId, false);
    }

    public static IdempotentResponse replay(int status, String jsonBody, UUID resourceId) {
        return new IdempotentResponse(status, jsonBody, resourceId, true);
    }

    public boolean isCreated() {
        return status == 201;
    }
}
