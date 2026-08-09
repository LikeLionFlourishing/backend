package likelion.flourishing.global.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ErrorDetail {

    private final String field;
    private final String reason;

    public static ErrorDetail of(String field, String reason) {
        return new ErrorDetail(field, reason);
    }
}
