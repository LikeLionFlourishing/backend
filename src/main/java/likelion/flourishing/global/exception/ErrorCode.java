package likelion.flourishing.global.exception;

import java.util.Locale;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * API 명세의 Problem 응답 값을 정의한다.
 * code는 명세의 `^[A-Z][A-Z0-9_]*$` 형식을 따르고, type은 code를 kebab-case로 바꿔 만든다.
 */
@Getter
public enum ErrorCode {
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "잘못된 요청", "요청 형식이 올바르지 않습니다."),
    VALIDATION_ERROR(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "입력값 오류", "입력값을 확인해 주세요."),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "인증 필요", "다시 로그인해 주세요."),
    INVALID_CREDENTIALS(
            HttpStatus.UNAUTHORIZED,
            "INVALID_CREDENTIALS",
            "인증 실패",
            "이메일 또는 비밀번호를 확인해 주세요."
    ),
    CSRF_TOKEN_INVALID(
            HttpStatus.FORBIDDEN,
            "CSRF_TOKEN_INVALID",
            "요청 검증 실패",
            "요청을 검증하지 못했습니다. 새로고침 후 다시 시도해 주세요."
    ),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "접근 거부", "이 요청을 수행할 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND", "리소스 없음", "요청한 리소스를 찾을 수 없습니다."),
    EMAIL_ALREADY_REGISTERED(
            HttpStatus.CONFLICT,
            "EMAIL_ALREADY_REGISTERED",
            "중복된 이메일",
            "이미 가입된 이메일입니다."
    ),
    DELETE_CONFIRMATION_REQUIRED(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "DELETE_CONFIRMATION_REQUIRED",
            "삭제 확인 필요",
            "계정 삭제를 확인하는 헤더가 올바르지 않습니다."
    ),
    TOO_MANY_REQUESTS(
            HttpStatus.TOO_MANY_REQUESTS,
            "TOO_MANY_REQUESTS",
            "요청 제한 초과",
            "요청이 많습니다. 잠시 후 다시 시도해 주세요."
    ),
    INTERNAL_SERVER_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "INTERNAL_SERVER_ERROR",
            "서버 오류",
            "요청을 처리하는 중 오류가 발생했습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String title;
    private final String detail;

    ErrorCode(HttpStatus status, String code, String title, String detail) {
        this.status = status;
        this.code = code;
        this.title = title;
        this.detail = detail;
    }

    /** Problem type URI의 마지막 경로 조각. 예: INVALID_CREDENTIALS → invalid-credentials */
    public String typeSlug() {
        return code.toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
