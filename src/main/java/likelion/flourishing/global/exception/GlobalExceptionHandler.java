package likelion.flourishing.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import likelion.flourishing.global.response.ErrorDetail;
import likelion.flourishing.global.response.ErrorResponse;
import likelion.flourishing.support.RateLimitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 컨트롤러에서 빠져나온 예외를 모두 명세 Problem 형식(application/problem+json)으로 바꾼다.
 *
 * <p>상태 코드를 나누는 기준은 명세 정의를 따른다.
 * <ul>
 *   <li>400 — 본문을 읽지 못한 경우. JSON 문법 오류, 정의되지 않은 필드, enum에 없는 값,
 *       필수 헤더 누락처럼 객체를 만들기 전에 실패한 것들이다.
 *   <li>422 — 본문은 읽혔지만 값이 규칙에 맞지 않는 경우. @Valid 검증 실패가 여기 해당하고
 *       어느 필드가 왜 틀렸는지 errors 배열에 담는다.
 *   <li>429 — 요청 제한 초과. 재시도 시점을 알려주는 Retry-After와 X-RateLimit-* 헤더를 함께 붙인다.
 * </ul>
 *
 * <p>맨 아래 Exception 처리기는 예상 못 한 오류를 500으로 바꾸면서 requestId와 예외 타입만 로그로
 * 남긴다. 스택 트레이스나 내부 메시지를 응답에 넣으면 서버 구조가 밖으로 새기 때문이다.
 *
 * <p>필터에서 나는 예외는 아직 컨트롤러에 닿기 전이라 여기로 오지 않는다. 그쪽은
 * {@link ProblemResponseWriter}가 같은 형식으로 직접 쓴다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ProblemFactory problemFactory;

    public GlobalExceptionHandler(ProblemFactory problemFactory) {
        this.problemFactory = problemFactory;
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyRequestsException(
            TooManyRequestsException exception,
            HttpServletRequest request
    ) {
        RateLimitResult result = exception.getRateLimitResult();
        return ResponseEntity.status(exception.getErrorCode().getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(result.retryAfterSeconds()))
                .header("X-RateLimit-Limit", String.valueOf(result.limit()))
                .header("X-RateLimit-Remaining", String.valueOf(result.remaining()))
                .header("X-RateLimit-Reset", String.valueOf(result.resetEpochSecond()))
                .body(problemFactory.create(exception.getErrorCode(), request));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException exception,
            HttpServletRequest request
    ) {
        return problem(exception.getErrorCode(), request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<ErrorDetail> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toErrorDetail)
                .toList();
        return problem(ErrorCode.VALIDATION_ERROR, request, errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        List<ErrorDetail> errors = exception.getConstraintViolations().stream()
                .map(violation -> ErrorDetail.of(
                        violation.getPropertyPath().toString(),
                        ErrorCode.VALIDATION_ERROR.getCode(),
                        violation.getMessage()
                ))
                .toList();
        return problem(ErrorCode.VALIDATION_ERROR, request, errors);
    }

    /**
     * 본문·헤더·경로 값을 읽지 못한 요청. 객체를 만들기 전에 실패한 것이라 422가 아니라 400이다.
     *
     * <p>MethodArgumentTypeMismatchException은 경로나 쿼리 값이 타입에 맞지 않을 때 난다.
     * 예를 들어 /v1/daily-check-ins/2026-13-99처럼 날짜가 될 수 없는 문자열이 오는 경우다.
     * 여기서 받지 않으면 아래 Exception 처리기로 흘러가 500이 된다.
     */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ErrorResponse> handleMalformedRequest(
            Exception exception,
            HttpServletRequest request
    ) {
        return problem(ErrorCode.BAD_REQUEST, request, null);
    }

    /**
     * Spring MVC가 4xx로 정의한 예외들. 이 클래스가 {@code ResponseEntityExceptionHandler}를
     * 상속하지 않고 아래 Exception 캐치올을 두고 있어, 따로 받지 않으면 전부 500으로 나간다.
     *
     * <ul>
     *   <li>404 — 어떤 핸들러나 정적 리소스에도 걸리지 않은 경로.
     *   <li>405 — 매핑되지 않은 메서드. {@code PUT /v1/me}처럼 경로만 열려 있는 자리에서 난다.
     *   <li>415 — Content-Type이 없거나 지원하지 않는 요청.
     *   <li>403 — 컨트롤러 안에서 난 접근 거부. {@link ProblemAccessDeniedHandler}는 필터 단계에서
     *       걸린 거부만 처리하므로 메서드 보안을 쓰기 시작하면 이쪽이 필요하다.
     * </ul>
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request
    ) {
        return problem(ErrorCode.METHOD_NOT_ALLOWED, request, null);
    }

    /**
     * 어떤 핸들러나 정적 리소스에도 걸리지 않은 경로. 404로 답한다.
     *
     * <p>이 핸들러가 없으면 500이 나간다. 인가 설정이 열어 둔 경로 중 실제 자원이 없는 자리에서
     * 생기는데, 운영 프로파일이 Swagger를 닫으면 {@code /swagger-ui.html}과
     * {@code /v3/api-docs}가 바로 그런 자리가 된다. 없는 문서를 찾은 것이 서버 오류로 보고되면
     * 로그에 예외가 쌓이고 클라이언트도 재시도할지 판단할 수 없다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException exception,
            HttpServletRequest request
    ) {
        return problem(ErrorCode.NOT_FOUND, request, null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException exception,
            HttpServletRequest request
    ) {
        return problem(ErrorCode.UNSUPPORTED_MEDIA_TYPE, request, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException exception,
            HttpServletRequest request
    ) {
        return problem(ErrorCode.ACCESS_DENIED, request, null);
    }

    /**
     * 예상 못 한 오류. 응답에는 내부 정보를 담지 않지만 로그에는 스택 트레이스를 남긴다.
     * 응답에서 감추는 것과 로그에서 감추는 것은 별개이고, 스택이 없으면 운영에서 어느 코드가
     * 터졌는지 알 방법이 없다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        String requestId = problemFactory.resolveRequestId(request);
        log.error(
                "Unhandled exception requestId={} type={}",
                requestId,
                exception.getClass().getName(),
                exception
        );
        return problem(ErrorCode.INTERNAL_SERVER_ERROR, request, null);
    }

    private ResponseEntity<ErrorResponse> problem(
            ErrorCode errorCode,
            HttpServletRequest request,
            List<ErrorDetail> errors
    ) {
        return ResponseEntity.status(errorCode.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemFactory.create(errorCode, request, errors));
    }

    private ErrorDetail toErrorDetail(FieldError fieldError) {
        String message = fieldError.getDefaultMessage() == null
                ? ErrorCode.VALIDATION_ERROR.getDetail()
                : fieldError.getDefaultMessage();
        String code = fieldError.getCode() == null
                ? ErrorCode.VALIDATION_ERROR.getCode()
                : fieldError.getCode();
        return ErrorDetail.of(fieldError.getField(), code, message);
    }
}
