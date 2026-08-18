package likelion.flourishing.domain.report.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.UUID;
import likelion.flourishing.domain.followup.entity.SkinChange;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 새 결과에 붙인 유사 경험.
 *
 * <p>기록 조회의 similarExperience와 필드가 같다. 그쪽은 저장된 결과를 읽어 만들고 이쪽은 방금
 * 고른 것을 담는데, 클라이언트가 보는 모양은 같아야 한다.
 *
 * @param skinChange 그때 경과에서 사용자가 답한 변화. 완료된 기록만 고르므로 항상 값이 있다.
 */
@Getter
@JsonInclude(JsonInclude.Include.ALWAYS)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SimilarExperienceSummaryResponse {

    private final UUID reportId;
    private final LocalDate reportDate;
    private final int score;
    private final String summary;
    private final SkinChange skinChange;

    public static SimilarExperienceSummaryResponse of(
            UUID reportId,
            LocalDate reportDate,
            int score,
            String summary,
            SkinChange skinChange
    ) {
        return new SimilarExperienceSummaryResponse(reportId, reportDate, score, summary, skinChange);
    }
}
