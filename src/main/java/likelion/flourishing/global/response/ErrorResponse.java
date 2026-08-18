package likelion.flourishing.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * API 명세의 Problem 스키마. `application/problem+json`으로 직렬화한다.
 * 클래스 이름은 팀 컨벤션의 오류 응답 이름을 그대로 쓰고, 필드는 명세를 따른다.
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ErrorResponse {

    private final String type;
    private final String title;
    private final int status;
    private final String detail;
    private final String instance;
    private final String code;
    private final String requestId;
    private final List<ErrorDetail> errors;

    public static ErrorResponse of(
            String type,
            String title,
            int status,
            String detail,
            String instance,
            String code,
            String requestId,
            List<ErrorDetail> errors
    ) {
        return new ErrorResponse(
                type,
                title,
                status,
                detail,
                instance,
                code,
                requestId,
                errors == null || errors.isEmpty() ? null : List.copyOf(errors)
        );
    }
}
