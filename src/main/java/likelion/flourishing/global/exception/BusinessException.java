package likelion.flourishing.global.exception;

import java.util.List;
import likelion.flourishing.global.response.ErrorDetail;
import lombok.Getter;

/**
 * 업무 규칙 위반. 상태 코드와 코드 문자열은 {@link ErrorCode}가 정한다.
 *
 * <p>어느 필드가 문제였는지 알려 줘야 하는 규칙이 있어 errors 를 함께 실을 수 있게 두었다.
 * 빈 목록이면 응답의 errors 는 생략된다. Bean Validation 이 잡는 형식 오류와 달리, 값의
 * 형식은 맞지만 규칙에 어긋나는 경우를 같은 모양으로 돌려주기 위한 것이다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<ErrorDetail> errors;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, List.of());
    }

    public BusinessException(ErrorCode errorCode, List<ErrorDetail> errors) {
        super(errorCode.getDetail());
        this.errorCode = errorCode;
        this.errors = List.copyOf(errors);
    }

    /** 필드 오류가 하나뿐인 흔한 경우를 짧게 쓰기 위한 생성자. */
    public static BusinessException ofField(ErrorCode errorCode, String field) {
        return new BusinessException(
                errorCode,
                List.of(ErrorDetail.of(field, errorCode.getCode(), errorCode.getDetail()))
        );
    }
}
