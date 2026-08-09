package likelion.flourishing.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import likelion.flourishing.global.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ErrorResponse {

    private final OffsetDateTime timestamp;
    private final int status;
    private final String code;
    private final String message;
    private final String path;
    private final List<ErrorDetail> details;

    public static ErrorResponse from(ErrorCode errorCode, String path) {
        return of(errorCode, path, null);
    }

    public static ErrorResponse of(ErrorCode errorCode, String path, List<ErrorDetail> details) {
        return new ErrorResponse(
                OffsetDateTime.now(ZoneOffset.UTC),
                errorCode.getStatus().value(),
                errorCode.getCode(),
                errorCode.getMessage(),
                path,
                details
        );
    }
}
