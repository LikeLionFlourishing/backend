package likelion.flourishing.domain.referencedata.service;

import java.util.Arrays;
import java.util.List;
import likelion.flourishing.domain.referencedata.dto.response.OptionResponse;
import likelion.flourishing.domain.referencedata.dto.response.SkinReportOptionsResponse;
import likelion.flourishing.domain.report.entity.Appearance;
import likelion.flourishing.domain.report.entity.BodyArea;
import likelion.flourishing.domain.report.entity.CareAvailability;
import likelion.flourishing.domain.report.entity.PreCareCheck;
import likelion.flourishing.domain.report.entity.Sensation;
import likelion.flourishing.domain.report.entity.Situation;
import likelion.flourishing.domain.report.service.GuideSectionAssembler;
import org.springframework.stereotype.Service;

@Service
public class ReferenceDataService {

    /**
     * 명세 SkinReportOptions.version. 선택값 구성이 바뀔 때마다 올린다.
     *
     * <p>v2_1에서 느껴지는 불편, 직전 상황, 겉모습 세 그룹이 바뀌어 2026-08-16이 됐다.
     * 클라이언트는 이 값이 달라지면 캐시한 선택값을 버려야 한다.
     */
    private static final String OPTIONS_VERSION = "2026-08-16";

    /**
     * enum에서 나오는 선택값 여섯 그룹. 배포 중에는 바뀌지 않아 한 번만 만든다.
     *
     * <p>가이드 섹션 문구는 여기 담지 않는다. 그쪽은 규칙표에서 읽는 값이라 배포 없이 바뀔 수
     * 있고, 정적 필드에 넣으면 고친 문구가 재시작 전까지 반영되지 않는다.
     */
    private static final SelectionOptions SELECTION_OPTIONS = createSelectionOptions();

    private final GuideSectionAssembler guideSectionAssembler;

    public ReferenceDataService(GuideSectionAssembler guideSectionAssembler) {
        this.guideSectionAssembler = guideSectionAssembler;
    }

    public SkinReportOptionsResponse getSkinReportOptions() {
        return SkinReportOptionsResponse.of(
                OPTIONS_VERSION,
                SELECTION_OPTIONS.areas(),
                SELECTION_OPTIONS.appearances(),
                SELECTION_OPTIONS.sensations(),
                SELECTION_OPTIONS.situations(),
                SELECTION_OPTIONS.careAvailability(),
                SELECTION_OPTIONS.preCareChecks(),
                guideSectionAssembler.assembleDefaults()
        );
    }

    private static SelectionOptions createSelectionOptions() {
        return new SelectionOptions(
                bodyAreaOptions(),
                appearanceOptions(),
                sensationOptions(),
                situationOptions(),
                careAvailabilityOptions(),
                preCareCheckOptions()
        );
    }

    /** enum에서 나오는 부분만 모아 둔 값. 요청마다 다시 만들 이유가 없다. */
    private record SelectionOptions(
            List<OptionResponse> areas,
            List<OptionResponse> appearances,
            List<OptionResponse> sensations,
            List<OptionResponse> situations,
            List<OptionResponse> careAvailability,
            List<OptionResponse> preCareChecks
    ) {
    }

    private static List<OptionResponse> bodyAreaOptions() {
        return Arrays.stream(BodyArea.values())
                .map(value -> OptionResponse.of(value.name(), value.getLabel()))
                .toList();
    }

    private static List<OptionResponse> appearanceOptions() {
        return Arrays.stream(Appearance.values())
                .map(value -> OptionResponse.of(value.name(), value.getLabel()))
                .toList();
    }

    private static List<OptionResponse> sensationOptions() {
        return Arrays.stream(Sensation.values())
                .map(value -> OptionResponse.of(value.name(), value.getLabel()))
                .toList();
    }

    private static List<OptionResponse> situationOptions() {
        return Arrays.stream(Situation.values())
                .map(value -> OptionResponse.of(value.name(), value.getLabel()))
                .toList();
    }

    private static List<OptionResponse> careAvailabilityOptions() {
        return Arrays.stream(CareAvailability.values())
                .map(value -> OptionResponse.of(value.name(), value.getLabel()))
                .toList();
    }

    private static List<OptionResponse> preCareCheckOptions() {
        return Arrays.stream(PreCareCheck.values())
                .map(value -> OptionResponse.of(value.name(), value.getLabel()))
                .toList();
    }
}
