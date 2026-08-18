package likelion.flourishing.domain.record.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import likelion.flourishing.domain.followup.entity.SkinChange;
import likelion.flourishing.domain.report.entity.Appearance;
import likelion.flourishing.domain.report.entity.BodyArea;
import likelion.flourishing.domain.report.entity.ReportStatus;
import likelion.flourishing.domain.report.entity.ResultType;
import likelion.flourishing.domain.report.entity.Sensation;
import likelion.flourishing.domain.report.entity.Situation;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@JsonInclude(JsonInclude.Include.ALWAYS)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SkinReportSummaryResponse {

    private final UUID id;
    private final LocalDate reportDate;
    private final BodyArea primaryArea;
    private final List<Appearance> appearances;
    private final List<Sensation> sensations;
    private final List<Situation> situations;
    private final ResultType resultType;
    private final ReportStatus status;
    private final SkinChange skinChange;

    public static SkinReportSummaryResponse of(
            UUID id,
            LocalDate reportDate,
            BodyArea primaryArea,
            List<Appearance> appearances,
            List<Sensation> sensations,
            List<Situation> situations,
            ResultType resultType,
            ReportStatus status,
            SkinChange skinChange
    ) {
        return new SkinReportSummaryResponse(
                id, reportDate, primaryArea, appearances, sensations, situations, resultType, status, skinChange
        );
    }
}
