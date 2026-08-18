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
    RESOURCE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "RESOURCE_NOT_FOUND",
            "리소스 없음",
            "요청한 리소스를 찾을 수 없습니다."
    ),
    INVALID_CURSOR(
            HttpStatus.BAD_REQUEST,
            "INVALID_CURSOR",
            "잘못된 커서",
            "기록 목록 커서가 올바르지 않습니다."
    ),
    METHOD_NOT_ALLOWED(
            HttpStatus.METHOD_NOT_ALLOWED,
            "METHOD_NOT_ALLOWED",
            "허용되지 않은 메서드",
            "이 경로에서 지원하지 않는 요청 방식입니다."
    ),
    UNSUPPORTED_MEDIA_TYPE(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "UNSUPPORTED_MEDIA_TYPE",
            "지원하지 않는 형식",
            "요청 본문 형식이 올바르지 않습니다. Content-Type을 확인해 주세요."
    ),
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
    CHECK_IN_DATE_NOT_TODAY(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "CHECK_IN_DATE_NOT_TODAY",
            "저장할 수 없는 날짜",
            "오늘 날짜만 저장할 수 있습니다."
    ),
    CHECK_IN_STATE_NOT_ALLOWED(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "CHECK_IN_STATE_NOT_ALLOWED",
            "저장할 수 없는 상태",
            "이 요청으로는 불편 없음만 저장할 수 있습니다."
    ),
    CHECK_IN_ALREADY_REPORTED(
            HttpStatus.CONFLICT,
            "CHECK_IN_ALREADY_REPORTED",
            "이미 보고된 날",
            "피부 보고가 저장된 날은 불편 없음으로 바꿀 수 없습니다."
    ),
    FOLLOW_UP_KIND_MISMATCH(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "FOLLOW_UP_KIND_MISMATCH",
            "경과 종류 불일치",
            "이 보고에 맞지 않는 경과 종류입니다."
    ),
    FOLLOW_UP_NOT_AVAILABLE_YET(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "FOLLOW_UP_NOT_AVAILABLE_YET",
            "아직 입력할 수 없음",
            "경과는 다음 날부터 입력할 수 있습니다."
    ),
    FOLLOW_UP_EXPIRED(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "FOLLOW_UP_EXPIRED",
            "입력 기한 지남",
            "경과 입력 기한이 지났습니다."
    ),
    FOLLOW_UP_ALREADY_SUBMITTED(
            HttpStatus.CONFLICT,
            "FOLLOW_UP_ALREADY_SUBMITTED",
            "이미 저장된 경과",
            "이미 저장한 경과는 다른 내용으로 바꿀 수 없습니다."
    ),
    CONSENT_REQUIRED(
            HttpStatus.FORBIDDEN,
            "CONSENT_REQUIRED",
            "동의 필요",
            "민감정보 동의를 완료해야 이용할 수 있습니다."
    ),
    SELECTION_COMBINATION_INVALID(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "SELECTION_COMBINATION_INVALID",
            "선택값 조합 오류",
            "함께 고를 수 없는 선택값이 있습니다."
    ),
    REPORT_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "REPORT_ALREADY_EXISTS",
            "이미 저장된 보고",
            "같은 날짜의 피부 보고는 하루 한 번만 저장할 수 있습니다."
    ),
    IDEMPOTENCY_KEY_REUSED(
            HttpStatus.CONFLICT,
            "IDEMPOTENCY_KEY_REUSED",
            "멱등성 키 재사용",
            "같은 키를 다른 요청에 다시 쓸 수 없습니다."
    ),
    AI_RETRY_NOT_AVAILABLE(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "AI_RETRY_NOT_AVAILABLE",
            "재생성할 수 없는 결과",
            "이 결과는 관리 설명을 다시 만들 수 없습니다."
    ),
    AI_RETRY_ALREADY_USED(
            HttpStatus.CONFLICT,
            "AI_RETRY_ALREADY_USED",
            "재생성 횟수 초과",
            "관리 설명 재생성은 한 번만 할 수 있습니다."
    ),
    CONSENT_VERSION_NOT_ACCEPTED(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "CONSENT_VERSION_NOT_ACCEPTED",
            "동의서 버전 불일치",
            "현재 받고 있는 동의서 버전이 아닙니다. 최신 동의 화면에서 다시 진행해 주세요."
    ),
    FEATURE_NOT_AVAILABLE(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "FEATURE_NOT_AVAILABLE",
            "아직 제공하지 않는 기능",
            "이 항목은 아직 바꿀 수 없습니다."
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
    ),
    RATE_LIMIT_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "RATE_LIMIT_UNAVAILABLE",
            "일시적으로 처리할 수 없음",
            "요청을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요."
    ),
    RULE_ENGINE_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "RULE_ENGINE_UNAVAILABLE",
            "관리 규칙 준비 중",
            "관리 기준을 확인할 수 없어 결과를 만들지 못했습니다. 잠시 후 다시 시도해 주세요."
    ),
    SERVICE_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "SERVICE_UNAVAILABLE",
            "일시적으로 처리할 수 없음",
            "요청을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요."
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
