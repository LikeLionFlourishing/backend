package likelion.flourishing.domain.record.dto.response;

import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SkinReportListResponse {

    private final List<SkinReportSummaryResponse> data;
    private final CursorPageResponse pagination;

    public static SkinReportListResponse of(
            List<SkinReportSummaryResponse> data,
            CursorPageResponse pagination
    ) {
        return new SkinReportListResponse(data, pagination);
    }
}
