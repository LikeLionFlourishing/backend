package likelion.flourishing.global.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

/**
 * {@link MaxByteLength} 검증기.
 *
 * <p>null은 통과시킨다. 값이 있어야 하는지는 {@code @NotBlank}가 따로 판단하며, 여기서 함께
 * 막으면 어느 규칙을 어겼는지 응답에서 구분되지 않는다.
 */
public class MaxByteLengthValidator implements ConstraintValidator<MaxByteLength, CharSequence> {

    private int max;

    @Override
    public void initialize(MaxByteLength constraint) {
        this.max = constraint.value();
    }

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return value.toString().getBytes(StandardCharsets.UTF_8).length <= max;
    }
}
