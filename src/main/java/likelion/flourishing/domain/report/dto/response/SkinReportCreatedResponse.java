package likelion.flourishing.domain.report.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import likelion.flourishing.domain.report.entity.PreCareCheck;
import likelion.flourishing.domain.report.entity.ReportStatus;
import likelion.flourishing.domain.report.entity.ResultType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 저장된 보고와 함께 만들어진 관리 결과.
 *
 * <p>보고와 결과를 한 응답에 담는다. 저장하자마자 사용자가 보는 화면이 결과 화면이라 응답을
 * 두 번 받게 만들 이유가 없다.
 *
 * @param followUpAvailableAt 경과를 입력할 수 있게 되는 시각.
 * @param followUpExpiresAt   경과 입력 기한.
 */
@Getter
@JsonInclude(JsonInclude.Include.ALWAYS)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SkinReportCreatedResponse {

    private final UUID id;
    private final LocalDate reportDate;
    private final ResultType resultType;
    private final ReportStatus status;
    private final String rawText;
    private final StructuredSelectionsResponse confirmed;
    private final List<PreCareCheck> preCareChecks;
    private final CareResultResponse careResult;
    private final OffsetDateTime followUpAvailableAt;
    private final OffsetDateTime followUpExpiresAt;
    private final OffsetDateTime createdAt;

    public static SkinReportCreatedResponse of(
            UUID id,
            LocalDate reportDate,
            ResultType resultType,
            ReportStatus status,
            String rawText,
            StructuredSelectionsResponse confirmed,
            List<PreCareCheck> preCareChecks,
            CareResultResponse careResult,
            OffsetDateTime followUpAvailableAt,
            OffsetDateTime followUpExpiresAt,
            OffsetDateTime createdAt
    ) {
        return new SkinReportCreatedResponse(
                id,
                reportDate,
                resultType,
                status,
                rawText,
                confirmed,
                preCareChecks,
                careResult,
                followUpAvailableAt,
                followUpExpiresAt,
                createdAt
        );
    }
}
