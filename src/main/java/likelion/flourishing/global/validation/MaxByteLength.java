package likelion.flourishing.global.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 문자열의 UTF-8 바이트 길이 상한. {@code @Size}는 문자 수만 세기 때문에, 바이트 단위 한도가 있는
 * 값에는 이것을 함께 붙인다.
 *
 * <p>한글 한 글자는 UTF-8에서 3바이트라 문자 수와 바이트 수가 크게 벌어진다. 예를 들어 24글자
 * 한글 비밀번호는 72바이트로, 문자 수 제한만으로는 바이트 한도를 넘는 것을 걸러내지 못한다.
 */
@Documented
@Constraint(validatedBy = MaxByteLengthValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxByteLength {

    String message() default "UTF-8 기준 {value}바이트를 넘을 수 없습니다.";

    /** 허용하는 최대 바이트 수. */
    int value();

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
