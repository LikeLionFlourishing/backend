package likelion.flourishing.domain.home.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import likelion.flourishing.domain.home.repository.HomeReportQueryRepository.RecentReportRow;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 명세 SkinReportSummary 스키마. 홈의 "최근 기록 한 건"에 쓴다.
 *
 * <p>부위·겉모습·불편·상황 값은 Reports 태그 담당자의 enum이라 여기서 새로 정의하지 않고
 * DB에 저장된 코드 문자열을 그대로 내보낸다. DDL의 CHECK가 값을 보장하고 있어 JSON 결과는
 * 명세의 enum과 같다. Reports 기능이 올라오면 그쪽 타입으로 바꾸는 것이 맞다.
 *
 * <p>skinChange는 경과를 아직 입력하지 않았으면 null이라 명세대로 필드를 남긴 채 내보낸다.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.ALWAYS)
public class SkinReportSummaryResponse {

    private final UUID id;

    private final LocalDate reportDate;

    private final String primaryArea;

    private final List<String> appearances;

    private final List<String> sensations;

    private final List<String> situations;

    private final String resultType;

    private final String status;

    private final String skinChange;

    public static SkinReportSummaryResponse of(
            UUID id,
            LocalDate reportDate,
            String primaryArea,
            List<String> appearances,
            List<String> sensations,
            List<String> situations,
            String resultType,
            String status,
            String skinChange
    ) {
        return new SkinReportSummaryResponse(
                id, reportDate, primaryArea, appearances, sensations, situations, resultType, status, skinChange
        );
    }

    public static SkinReportSummaryResponse from(
            RecentReportRow row,
            List<String> appearances,
            List<String> sensations,
            List<String> situations
    ) {
        return new SkinReportSummaryResponse(
                row.id(),
                row.reportDate(),
                row.primaryArea(),
                appearances,
                sensations,
                situations,
                row.resultType(),
                row.status(),
                row.skinChange()
        );
    }
}
