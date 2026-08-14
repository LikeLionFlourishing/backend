package likelion.flourishing.analytics.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import likelion.flourishing.analytics.domain.AnalyticsEventName;

/** 측정 이벤트 한 건. eventId는 클라이언트 재전송을 멱등 처리하는 식별자다. */
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public class AnalyticsEventRequest {

    @NotNull
    private UUID eventId;

    @NotBlank
    @Pattern(regexp = AnalyticsEventName.ALLOWED_PATTERN, message = "허용되지 않은 이벤트 이름입니다.")
    private String eventName;

    @Valid
    private AnalyticsEventPropertiesRequest properties;

    @NotNull
    private OffsetDateTime occurredAt;

    @JsonIgnore
    private final Set<String> unknownFields = new HashSet<>();

    public AnalyticsEventRequest() {
    }

    public AnalyticsEventRequest(
            UUID eventId,
            String eventName,
            AnalyticsEventPropertiesRequest properties,
            OffsetDateTime occurredAt
    ) {
        this.eventId = eventId;
        this.eventName = eventName;
        this.properties = properties;
        this.occurredAt = occurredAt;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEventName() {
        return eventName;
    }

    public AnalyticsEventPropertiesRequest getProperties() {
        return properties;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object ignoredValue) {
        unknownFields.add(fieldName);
    }

    @JsonIgnore
    @AssertTrue(message = "정의되지 않은 필드는 사용할 수 없습니다.")
    public boolean isAllowedFieldsOnly() {
        return unknownFields.isEmpty();
    }
}
