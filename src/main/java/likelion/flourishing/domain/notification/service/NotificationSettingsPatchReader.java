package likelion.flourishing.domain.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Set;
import likelion.flourishing.domain.notification.dto.request.UpdateNotificationSettingsRequest;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import likelion.flourishing.global.response.ErrorDetail;
import org.springframework.stereotype.Component;

/**
 * 알림 설정 PATCH 본문을 읽는다.
 *
 * <p>본문을 바로 DTO로 바인딩하지 않고 JsonNode를 한 번 거치는 이유는 하나다. 명세의 세 필드가
 * nullable이 아니라서 "보내지 않음"과 "명시적 null"이 다른 뜻인데, Jackson은 둘 다 null 필드로
 * 만들어 구분을 지운다. 키가 실제로 있었는지는 트리에서만 알 수 있다.
 *
 * <p>여기서 명시적 null을 걸러 내고 나면 그 뒤로는 null 필드가 곧 "보내지 않음"이다.
 */
@Component
public class NotificationSettingsPatchReader {

    private static final List<String> PATCH_FIELDS = List.of("enabled", "time", "consent");

    private final ObjectMapper objectMapper;
    private final Validator validator;

    public NotificationSettingsPatchReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        // 이 경로는 @Valid 를 거치지 않으므로 검증기를 직접 들고 있는다. 웹 계층 설정에 기대지 않아
        // 슬라이스 테스트에서도 같은 규칙이 그대로 돈다.
        this.validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    public UpdateNotificationSettingsRequest read(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        // 명세의 minProperties: 1. 빈 객체는 아무것도 바꾸지 않겠다는 뜻이라 요청 자체가 성립하지 않는다.
        if (body.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    List.of(ErrorDetail.of(
                            "body",
                            ErrorCode.VALIDATION_ERROR.getCode(),
                            "바꿀 항목을 하나 이상 보내 주세요."
                    ))
            );
        }
        rejectExplicitNulls(body);

        UpdateNotificationSettingsRequest request = toRequest(body);
        validate(request);
        return request;
    }

    /**
     * 값을 지우겠다는 뜻으로 보낸 null을 거부한다.
     *
     * <p>세 필드 모두 지울 수 있는 값이 아니다. 조용히 무시하면 클라이언트는 바뀐 줄 알고,
     * 기본값으로 덮으면 사용자가 고른 적 없는 설정이 저장된다.
     */
    private void rejectExplicitNulls(JsonNode body) {
        List<ErrorDetail> errors = PATCH_FIELDS.stream()
                .filter(field -> body.has(field) && body.get(field).isNull())
                .map(field -> ErrorDetail.of(
                        field,
                        ErrorCode.VALIDATION_ERROR.getCode(),
                        "null 은 보낼 수 없습니다. 바꾸지 않으려면 항목을 빼 주세요."
                ))
                .toList();
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, errors);
        }
    }

    private UpdateNotificationSettingsRequest toRequest(JsonNode body) {
        try {
            return objectMapper.treeToValue(body, UpdateNotificationSettingsRequest.class);
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
    }

    /** @Valid 를 거치지 않는 경로라 제약을 직접 돌린다. 위반은 기존 핸들러가 422로 옮긴다. */
    private void validate(UpdateNotificationSettingsRequest request) {
        Set<ConstraintViolation<UpdateNotificationSettingsRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}
