package likelion.flourishing.domain.analytics.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 피부 정보가 아닌 지표 계산용 속성만 구조적으로 허용한다. */
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public class AnalyticsEventPropertiesRequest {

    private static final Set<String> RESULT_TYPES = Set.of("SELF_CARE_GUIDE", "CLINICIAN_CHECK");

    @PositiveOrZero
    private Long durationMs;

    private Boolean inputAssistUsed;

    private String resultType;

    private Boolean aiSucceeded;

    @JsonIgnore
    private final Set<String> unknownFields = new HashSet<>();

    public AnalyticsEventPropertiesRequest() {
    }

    public AnalyticsEventPropertiesRequest(
            Long durationMs,
            Boolean inputAssistUsed,
            String resultType,
            Boolean aiSucceeded
    ) {
        this.durationMs = durationMs;
        this.inputAssistUsed = inputAssistUsed;
        this.resultType = resultType;
        this.aiSucceeded = aiSucceeded;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public Boolean getInputAssistUsed() {
        return inputAssistUsed;
    }

    public String getResultType() {
        return resultType;
    }

    public Boolean getAiSucceeded() {
        return aiSucceeded;
    }

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object ignoredValue) {
        unknownFields.add(fieldName);
    }

    @JsonIgnore
    @AssertTrue(message = "허용되지 않은 측정 속성이 포함되어 있습니다.")
    public boolean isAllowedFieldsOnly() {
        return unknownFields.isEmpty();
    }

    @JsonIgnore
    @AssertTrue(message = "허용되지 않은 결과 유형입니다.")
    public boolean isResultTypeAllowed() {
        return resultType == null || RESULT_TYPES.contains(resultType);
    }

    public Map<String, Object> toAllowedProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        putIfPresent(properties, "durationMs", durationMs);
        putIfPresent(properties, "inputAssistUsed", inputAssistUsed);
        putIfPresent(properties, "resultType", resultType);
        putIfPresent(properties, "aiSucceeded", aiSucceeded);
        return properties;
    }

    private void putIfPresent(Map<String, Object> properties, String key, Object value) {
        if (value != null) {
            properties.put(key, value);
        }
    }
}
