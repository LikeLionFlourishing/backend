package likelion.flourishing.domain.report.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.Map;
import likelion.flourishing.domain.report.ai.AiFailureCode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 구조화 응답.
 *
 * <p>실패해도 structured에는 사용자가 직접 고른 값이 담겨 나간다. 화면이 비어 버리면 사용자가
 * 처음부터 다시 고르게 되기 때문이다.
 *
 * <p>failureCode는 실패했을 때만 값이 있다. 원문이나 모델 응답은 어디에도 담기지 않는다.
 */
@Getter
@JsonInclude(JsonInclude.Include.ALWAYS)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ReportInterpretationResponse {

    private final ProcessingStatus processingStatus;
    private final AiFailureCode failureCode;
    private final StructuredSelectionsResponse structured;
    private final Map<String, FieldSource> fieldSources;
    private final OffsetDateTime interpretedAt;

    public static ReportInterpretationResponse succeeded(
            StructuredSelectionsResponse structured,
            Map<String, FieldSource> fieldSources,
            OffsetDateTime interpretedAt
    ) {
        return new ReportInterpretationResponse(
                ProcessingStatus.SUCCEEDED,
                null,
                structured,
                fieldSources,
                interpretedAt
        );
    }

    public static ReportInterpretationResponse failed(
            AiFailureCode failureCode,
            StructuredSelectionsResponse structured,
            Map<String, FieldSource> fieldSources,
            OffsetDateTime interpretedAt
    ) {
        return new ReportInterpretationResponse(
                ProcessingStatus.FAILED,
                failureCode,
                structured,
                fieldSources,
                interpretedAt
        );
    }
}
