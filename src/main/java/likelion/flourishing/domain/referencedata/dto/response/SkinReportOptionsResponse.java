package likelion.flourishing.domain.referencedata.dto.response;

import likelion.flourishing.domain.report.dto.response.GuideSectionResponse;
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

    /**
     * 결과 카드 가이드 섹션의 기본 제목·설명.
     *
     * <p>결과가 아직 없을 때도 화면을 그릴 수 있게 준다. 여기 담기는 값은 결과에 붙는
     * guideSections 와 같은 문구이며, 본문이 없으므로 전부 empty 다.
     */
    private final List<GuideSectionResponse> guideSections;

    public static SkinReportOptionsResponse of(
            String version,
            List<OptionResponse> areas,
            List<OptionResponse> appearances,
            List<OptionResponse> sensations,
            List<OptionResponse> situations,
            List<OptionResponse> careAvailability,
            List<OptionResponse> preCareChecks,
            List<GuideSectionResponse> guideSections
    ) {
        return new SkinReportOptionsResponse(
                version,
                List.copyOf(areas),
                List.copyOf(appearances),
                List.copyOf(sensations),
                List.copyOf(situations),
                List.copyOf(careAvailability),
                List.copyOf(preCareChecks),
                List.copyOf(guideSections)
        );
    }
}
