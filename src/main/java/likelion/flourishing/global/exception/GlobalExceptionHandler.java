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

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingRequestHeaderException.class})
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
