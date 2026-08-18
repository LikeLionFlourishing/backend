package likelion.flourishing.global.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** API 명세 Problem.errors 항목. */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ErrorDetail {

    private final String field;
    private final String code;
    private final String message;

    public static ErrorDetail of(String field, String code, String message) {
        return new ErrorDetail(field, code, message);
    }
}
