package likelion.flourishing.global.exception;

import likelion.flourishing.support.RateLimitResult;
import lombok.Getter;

/** 요청 제한을 넘겼을 때 던진다. 명세의 429 응답 헤더를 만들기 위해 제한 정보를 함께 담는다. */
@Getter
public class TooManyRequestsException extends BusinessException {

    private final RateLimitResult rateLimitResult;

    public TooManyRequestsException(RateLimitResult rateLimitResult) {
        super(ErrorCode.TOO_MANY_REQUESTS);
        this.rateLimitResult = rateLimitResult;
    }
}
