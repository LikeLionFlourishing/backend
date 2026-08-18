package likelion.flourishing.domain.analytics.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 피부 정보가 아닌 지표 계산용 속성만 구조적으로 허용한다.
 *
 * <p>명세 v2_1에서 둘이 늘었다. 둘 다 값 자체는 민감하지 않게 설계돼 있다.
 * ingredientCount는 성분 개수만 담고 성분명은 담지 않으며, usedDefaultTime은 기본값을 그대로
 * 썼는지 여부만 담고 사용자가 고른 시각은 담지 않는다.
 */
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public class AnalyticsEventPropertiesRequest {

    private static final Set<String> RESULT_TYPES = Set.of("SELF_CARE_GUIDE", "CLINICIAN_CHECK");

    /** 명세 maximum 3600000. 상한이 없으면 시계가 어긋난 클라이언트 값이 지표를 통째로 왜곡한다. */
    @PositiveOrZero
    @Max(3_600_000)
    private Long durationMs;

    private Boolean inputAssistUsed;

    private String resultType;

    private Boolean aiSucceeded;

    /** 명세 maximum 3. CareResult.recommendedIngredients의 maxItems와 같은 값이다. */
    @PositiveOrZero
    @Max(3)
    private Integer ingredientCount;

    private Boolean usedDefaultTime;

    @JsonIgnore
    private final Set<String> unknownFields = new HashSet<>();

    public AnalyticsEventPropertiesRequest() {
    }

    public AnalyticsEventPropertiesRequest(
            Long durationMs,
            Boolean inputAssistUsed,
            String resultType,
            Boolean aiSucceeded,
            Integer ingredientCount,
            Boolean usedDefaultTime
    ) {
        this.durationMs = durationMs;
        this.inputAssistUsed = inputAssistUsed;
        this.resultType = resultType;
        this.aiSucceeded = aiSucceeded;
        this.ingredientCount = ingredientCount;
        this.usedDefaultTime = usedDefaultTime;
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

    public Integer getIngredientCount() {
        return ingredientCount;
    }

    public Boolean getUsedDefaultTime() {
        return usedDefaultTime;
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

    /** 명세가 정한 상·하한. 서비스 계층에서도 같은 값으로 한 번 더 본다. */
    @JsonIgnore
    public boolean isWithinDeclaredRanges() {
        if (durationMs != null && (durationMs < 0 || durationMs > 3_600_000L)) {
            return false;
        }
        return ingredientCount == null || ingredientCount >= 0 && ingredientCount <= 3;
    }

    public Map<String, Object> toAllowedProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        putIfPresent(properties, "durationMs", durationMs);
        putIfPresent(properties, "inputAssistUsed", inputAssistUsed);
        putIfPresent(properties, "resultType", resultType);
        putIfPresent(properties, "aiSucceeded", aiSucceeded);
        putIfPresent(properties, "ingredientCount", ingredientCount);
        putIfPresent(properties, "usedDefaultTime", usedDefaultTime);
        return properties;
    }

    private void putIfPresent(Map<String, Object> properties, String key, Object value) {
        if (value != null) {
            properties.put(key, value);
        }
    }
}
