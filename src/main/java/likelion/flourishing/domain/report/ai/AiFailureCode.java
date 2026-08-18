package likelion.flourishing.domain.report.ai;

/**
 * AI 호출이 왜 실패했는지. 응답의 failureCode와 로그에 남기는 유일한 실패 정보다.
 *
 * <p>원문, 프롬프트, 모델 원본 응답은 어디에도 남기지 않기 때문에 사후 확인은 이 코드로만 한다.
 */
public enum AiFailureCode {

    /** API 키나 모델이 설정되지 않았다. */
    AI_NOT_CONFIGURED,

    /** 응답을 기다리다 제한 시간을 넘겼다. */
    AI_TIMEOUT,

    /** 네트워크나 TLS 단계에서 닿지 못했다. */
    AI_UNREACHABLE,

    /** 4xx·5xx 응답을 받았다. */
    AI_HTTP_ERROR,

    /** 모델이 안전상 이유로 응답을 거부했다. */
    AI_REFUSED,

    /** 토큰 한도나 콘텐츠 필터로 응답이 중간에 끊겼다. */
    AI_INCOMPLETE,

    /** 응답 본문이 기대한 형태가 아니어서 값을 꺼내지 못했다. */
    AI_MALFORMED_OUTPUT,

    /** 형태는 맞지만 서버 재검증에서 허용하지 않는 값이 들어 있었다. */
    AI_SCHEMA_VIOLATION
}
