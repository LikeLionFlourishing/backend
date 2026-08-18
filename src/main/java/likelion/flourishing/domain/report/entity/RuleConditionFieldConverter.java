package likelion.flourishing.domain.report.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * {@link RuleConditionField}를 DDL이 허용하는 lowerCamelCase 문자열로 바꾼다.
 *
 * <p>{@code @Enumerated(STRING)}은 enum 이름을 그대로 저장해 CHECK 제약을 위반한다.
 */
@Converter
public class RuleConditionFieldConverter implements AttributeConverter<RuleConditionField, String> {

    @Override
    public String convertToDatabaseColumn(RuleConditionField attribute) {
        return attribute == null ? null : attribute.code();
    }

    @Override
    public RuleConditionField convertToEntityAttribute(String dbData) {
        return dbData == null ? null : RuleConditionField.fromCode(dbData);
    }
}
