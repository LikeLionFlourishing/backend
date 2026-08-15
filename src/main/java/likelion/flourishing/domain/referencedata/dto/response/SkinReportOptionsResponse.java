package likelion.flourishing.domain.referencedata.dto.response;

import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SkinReportOptionsResponse {

    private final String version;
    private final List<OptionResponse> areas;
    private final List<OptionResponse> appearances;
    private final List<OptionResponse> sensations;
    private final List<OptionResponse> situations;
    private final List<OptionResponse> careAvailability;
    private final List<OptionResponse> preCareChecks;

    public static SkinReportOptionsResponse of(
            String version,
            List<OptionResponse> areas,
            List<OptionResponse> appearances,
            List<OptionResponse> sensations,
            List<OptionResponse> situations,
            List<OptionResponse> careAvailability,
            List<OptionResponse> preCareChecks
    ) {
        return new SkinReportOptionsResponse(
                version,
                List.copyOf(areas),
                List.copyOf(appearances),
                List.copyOf(sensations),
                List.copyOf(situations),
                List.copyOf(careAvailability),
                List.copyOf(preCareChecks)
        );
    }
}
