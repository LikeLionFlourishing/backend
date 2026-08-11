package likelion.flourishing.global.exception;

import lombok.Getter;

/**
 * 업무 규칙 위반을 알리는 예외. 던지면 {@link GlobalExceptionHandler}가 받아
 * {@link ErrorCode}에 정의된 상태 코드와 명세 Problem 형식으로 응답한다.
 *
 * <p>서비스 계층이 HTTP 상태 코드를 직접 다루지 않게 하려고 둔 것이다. 예를 들어
 * 중복 이메일이면 ErrorCode.EMAIL_ALREADY_REGISTERED만 던지고, 그것이 409가 된다는 사실은
 * ErrorCode가 안다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDetail());
        this.errorCode = errorCode;
    }
}
