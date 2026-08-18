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
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        String requestId = problemFactory.resolveRequestId(request);
        log.error("Unhandled exception requestId={} type={}", requestId, exception.getClass().getName());
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
