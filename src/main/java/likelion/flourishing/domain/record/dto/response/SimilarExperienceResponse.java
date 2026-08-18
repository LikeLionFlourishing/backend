package likelion.flourishing.domain.record.dto.response;

import java.time.LocalDate;
import java.util.UUID;
import likelion.flourishing.domain.followup.entity.SkinChange;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SimilarExperienceResponse {

    private final UUID reportId;
    private final LocalDate reportDate;
    private final int similarityScore;
    private final String displayText;
    private final SkinChange skinChange;

    public static SimilarExperienceResponse of(
            UUID reportId,
            LocalDate reportDate,
            int similarityScore,
            String displayText,
            SkinChange skinChange
    ) {
        return new SimilarExperienceResponse(reportId, reportDate, similarityScore, displayText, skinChange);
    }
}
