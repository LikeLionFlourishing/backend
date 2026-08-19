package likelion.flourishing.domain.report.dto.response;

import likelion.flourishing.domain.report.ai.AiFailureCode;

/**
 * 응답으로 나가는 실패 사유. 명세가 정한 세 값뿐이다.
 *
 * <p>내부 {@link AiFailureCode} 8종은 그대로 두고 여기서 세 값으로 좁힌다. 세분 코드는 우리가
 * 원인을 좁힐 때 쓰는 것이지 클라이언트가 분기할 근거가 아니고, 밖으로 내보내면 어느 외부 모델을
 * 어떤 방식으로 부르는지가 함께 드러난다. 원래 코드는 서버 로그에 requestId 와 함께 남긴다.
 */
public enum InterpretationFailureCode {
    AI_TIMEOUT,
    AI_INVALID_OUTPUT,
    AI_UNAVAILABLE;

    public static InterpretationFailureCode from(AiFailureCode internal) {
        if (internal == null) {
            return null;
        }
        return switch (internal) {
            case AI_TIMEOUT -> AI_TIMEOUT;
            case AI_NOT_CONFIGURED, AI_UNREACHABLE, AI_HTTP_ERROR, AI_REFUSED -> AI_UNAVAILABLE;
            case AI_INCOMPLETE, AI_MALFORMED_OUTPUT, AI_SCHEMA_VIOLATION -> AI_INVALID_OUTPUT;
        };
    }
}
