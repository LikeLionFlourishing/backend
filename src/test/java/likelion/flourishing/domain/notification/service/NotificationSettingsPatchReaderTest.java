package likelion.flourishing.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolationException;
import likelion.flourishing.domain.notification.dto.request.UpdateNotificationSettingsRequest;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

/**
 * PATCH 본문 해석 테스트.
 *
 * <p>여기서 확인하는 것은 "보내지 않음"과 "명시적 null"이 다르게 다뤄지는지다. 둘을 섞으면
 * 값을 지우겠다는 요청과 그냥 두겠다는 요청이 같은 뜻이 된다.
 */
class NotificationSettingsPatchReaderTest {

    private final NotificationSettingsPatchReader reader =
            new NotificationSettingsPatchReader(new ObjectMapper());

    private UpdateNotificationSettingsRequest read(String json) throws Exception {
        return reader.read(new ObjectMapper().readTree(json));
    }

    @Test
    void omittedFieldsStayNull() throws Exception {
        UpdateNotificationSettingsRequest request = read("{\"enabled\": false}");

        assertThat(request.enabled()).isFalse();
        assertThat(request.requestsTimeChange()).isFalse();
        assertThat(request.requestsConsentChange()).isFalse();
    }

    @Test
    void timeOnlyRequestIsAccepted() throws Exception {
        UpdateNotificationSettingsRequest request = read("{\"time\": \"21:00\"}");

        assertThat(request.time()).isEqualTo("21:00");
        assertThat(request.requestsEnabledChange()).isFalse();
    }

    @Test
    void consentIsReadWithBothFields() throws Exception {
        UpdateNotificationSettingsRequest request =
                read("{\"enabled\": true, \"consent\": {\"agreed\": true, \"version\": \"2026-08-16\"}}");

        assertThat(request.consent().agreed()).isTrue();
        assertThat(request.consent().version()).isEqualTo("2026-08-16");
    }

    /** 명세의 minProperties: 1. 아무것도 바꾸지 않는 요청은 성립하지 않는다. */
    @Test
    void emptyObjectIsRejected() {
        assertThatThrownBy(() -> read("{}"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    /** 세 필드 모두 nullable 이 아니다. 지우겠다는 뜻으로 보낸 null 은 조용히 무시하면 안 된다. */
    @Test
    void explicitNullIsRejectedAndNamesTheField() {
        assertThatThrownBy(() -> read("{\"enabled\": null}"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrors().get(0).getField())
                .isEqualTo("enabled");
    }

    @Test
    void malformedTimeIsRejected() {
        assertThatThrownBy(() -> read("{\"time\": \"25:00\"}"))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void consentWithoutVersionIsRejected() {
        assertThatThrownBy(() -> read("{\"consent\": {\"agreed\": true}}"))
                .isInstanceOf(ConstraintViolationException.class);
    }
}
