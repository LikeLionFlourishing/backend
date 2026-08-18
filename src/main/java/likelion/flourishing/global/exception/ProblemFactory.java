package likelion.flourishing.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.util.List;
import likelion.flourishing.global.config.ProblemProperties;
import likelion.flourishing.global.response.ErrorDetail;
import likelion.flourishing.global.response.ErrorResponse;
import org.springframework.stereotype.Component;

/**
 * Problem 응답을 만든다. requestId는 요청당 한 번만 만들고 요청 속성에 담아 로그와 응답이 같은 값을 쓰게 한다.
 */
@Component
public class ProblemFactory {

    public static final String REQUEST_ID_ATTRIBUTE = "flourishing.requestId";

    private static final String REQUEST_ID_PREFIX = "req_";
    private static final char[] REQUEST_ID_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int REQUEST_ID_LENGTH = 10;

    private final SecureRandom random = new SecureRandom();
    private final ProblemProperties problemProperties;

    public ProblemFactory(ProblemProperties problemProperties) {
        this.problemProperties = problemProperties;
    }

    public ErrorResponse create(ErrorCode errorCode, HttpServletRequest request) {
        return create(errorCode, errorCode.getDetail(), request, null);
    }

    public ErrorResponse create(ErrorCode errorCode, HttpServletRequest request, List<ErrorDetail> errors) {
        return create(errorCode, errorCode.getDetail(), request, errors);
    }

    public ErrorResponse create(
            ErrorCode errorCode,
            String detail,
            HttpServletRequest request,
            List<ErrorDetail> errors
    ) {
        return ErrorResponse.of(
                problemProperties.baseUri() + "/" + errorCode.typeSlug(),
                errorCode.getTitle(),
                errorCode.getStatus().value(),
                detail,
                request == null ? null : request.getRequestURI(),
                errorCode.getCode(),
                resolveRequestId(request),
                errors
        );
    }

    public String resolveRequestId(HttpServletRequest request) {
        if (request == null) {
            return generateRequestId();
        }
        Object cached = request.getAttribute(REQUEST_ID_ATTRIBUTE);
        if (cached instanceof String requestId) {
            return requestId;
        }
        String requestId = generateRequestId();
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        return requestId;
    }

    private String generateRequestId() {
        StringBuilder builder = new StringBuilder(REQUEST_ID_PREFIX);
        for (int index = 0; index < REQUEST_ID_LENGTH; index++) {
            builder.append(REQUEST_ID_ALPHABET[random.nextInt(REQUEST_ID_ALPHABET.length)]);
        }
        return builder.toString();
    }
}
