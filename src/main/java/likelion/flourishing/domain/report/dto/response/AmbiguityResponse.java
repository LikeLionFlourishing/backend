package likelion.flourishing.domain.report.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * AI가 원문에서 어느 항목인지 확정하지 못한 표현.
 *
 * <p>field 는 모호했던 항목명, text 는 원문에서 발췌한 표현이다. 길이 상한은 명세를 따른다.
 */
@Getter
@JsonInclude(JsonInclude.Include.ALWAYS)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AmbiguityResponse {

    /** 명세 Ambiguity.text 의 maxLength. */
    public static final int MAX_TEXT_LENGTH = 120;

    /** 명세 ReportInterpretation.ambiguities 의 maxItems. */
    public static final int MAX_ITEMS = 10;

    private final String field;
    private final String text;

    public static AmbiguityResponse of(String field, String text) {
        return new AmbiguityResponse(field, truncate(text));
    }

    private static String truncate(String text) {
        if (text == null || text.length() <= MAX_TEXT_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_TEXT_LENGTH);
    }
}
