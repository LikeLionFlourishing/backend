package likelion.flourishing.global.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 바이트 길이 제한이 문자 수가 아니라 UTF-8 바이트로 세는지 확인한다.
 *
 * <p>한글은 한 글자가 3바이트라 24글자면 벌써 72바이트다. 문자 수만 세는 {@code @Size}로는 이
 * 경계를 잡을 수 없다는 것이 이 제약을 따로 만든 이유다.
 */
class MaxByteLengthValidatorTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void openValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @Test
    void acceptsValueAtTheByteLimit() {
        assertThat(validator.validate(new Holder("a".repeat(72)))).isEmpty();
    }

    @Test
    void rejectsValueOverTheByteLimitByOneByte() {
        assertThat(validator.validate(new Holder("a".repeat(73)))).hasSize(1);
    }

    @Test
    void countsMultiByteCharactersAsBytes() {
        // 한글 24글자 = 72바이트라 통과하고, 한 글자만 더 붙으면 75바이트라 걸린다.
        assertThat(validator.validate(new Holder("가".repeat(24)))).isEmpty();
        assertThat(validator.validate(new Holder("가".repeat(25)))).hasSize(1);
    }

    @Test
    void leavesNullToOtherConstraints() {
        assertThat(validator.validate(new Holder(null))).isEmpty();
    }

    @Test
    void messageSaysTheByteLimit() {
        assertThat(validator.validate(new Holder("a".repeat(73))).iterator().next().getMessage())
                .isEqualTo("UTF-8 기준 72바이트를 넘을 수 없습니다.");
    }

    private record Holder(@MaxByteLength(72) String value) {
    }
}
