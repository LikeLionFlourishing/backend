package likelion.flourishing.domain.report.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 한 문장 구조화 결과. 명세 ReportInterpretation 과 필드가 1:1로 대응한다.
 *
 * <p>명세의 응답 스키마는 additionalProperties: false 라서 여기에 없는 값을 얹으면 계약 위반이다.
 * 그래서 fieldSources(값의 출처)와 interpretedAt(처리 시각)은 담지 않는다.
 *
 * <p>실패해도 proposed 는 항상 채워 보낸다. 사용자가 직접 고른 값은 AI 실패와 무관하게 살아 있어야
 * 확인 화면이 빈 채로 열리지 않는다.
 */
@Getter
@JsonInclude(JsonInclude.Include.ALWAYS)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ReportInterpretationResponse {

    private final ProcessingStatus processingStatus;
    private final StructuredSelectionsResponse proposed;
    private final List<MissingField> missingFields;
    private final List<AmbiguityResponse> ambiguities;
    private final InterpretationFailureCode failureCode;

    public static ReportInterpretationResponse succeeded(
            StructuredSelectionsResponse proposed,
            List<MissingField> missingFields,
            List<AmbiguityResponse> ambiguities
    ) {
        return new ReportInterpretationResponse(
                ProcessingStatus.SUCCESS,
                proposed,
                List.copyOf(missingFields),
                List.copyOf(ambiguities),
                null
        );
    }

    public static ReportInterpretationResponse failed(
            InterpretationFailureCode failureCode,
            StructuredSelectionsResponse proposed,
            List<MissingField> missingFields,
            List<AmbiguityResponse> ambiguities
    ) {
        return new ReportInterpretationResponse(
                ProcessingStatus.FAILED,
                proposed,
                List.copyOf(missingFields),
                List.copyOf(ambiguities),
                failureCode
        );
    }
}
