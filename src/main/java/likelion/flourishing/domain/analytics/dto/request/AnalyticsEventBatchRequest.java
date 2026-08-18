package likelion.flourishing.domain.analytics.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 최대 20개 측정 이벤트를 한 번에 접수하는 요청. */
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public class AnalyticsEventBatchRequest {

    @NotNull
    @Size(min = 1, max = 20)
    private List<@NotNull @Valid AnalyticsEventRequest> events;

    @JsonIgnore
    private final Set<String> unknownFields = new HashSet<>();

    public AnalyticsEventBatchRequest() {
    }

    public AnalyticsEventBatchRequest(List<AnalyticsEventRequest> events) {
        this.events = events;
    }

    public List<AnalyticsEventRequest> getEvents() {
        return events;
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
