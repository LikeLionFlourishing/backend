package likelion.flourishing.domain.record.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import likelion.flourishing.domain.report.entity.Appearance;
import likelion.flourishing.domain.report.entity.BodyArea;
import likelion.flourishing.domain.report.entity.CareAvailability;
import likelion.flourishing.domain.report.entity.Sensation;
import likelion.flourishing.domain.report.entity.Situation;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@JsonInclude(JsonInclude.Include.ALWAYS)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ConfirmedStructuredReportResponse {

    private final BodyArea primaryArea;
    private final String otherAreasNote;
    private final List<Appearance> appearances;
    private final List<Sensation> sensations;
    private final List<Situation> situations;
    private final CareAvailability careAvailability;

    public static ConfirmedStructuredReportResponse of(
            BodyArea primaryArea,
            String otherAreasNote,
            List<Appearance> appearances,
            List<Sensation> sensations,
            List<Situation> situations,
            CareAvailability careAvailability
    ) {
        return new ConfirmedStructuredReportResponse(
                primaryArea, otherAreasNote, appearances, sensations, situations, careAvailability
        );
    }
}
