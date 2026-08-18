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
    private static final SkinReportOptionsResponse SKIN_REPORT_OPTIONS = createSkinReportOptions();

    public SkinReportOptionsResponse getSkinReportOptions() {
        return SKIN_REPORT_OPTIONS;
    }

    private static SkinReportOptionsResponse createSkinReportOptions() {
        return SkinReportOptionsResponse.of(
                OPTIONS_VERSION,
                bodyAreaOptions(),
                appearanceOptions(),
                sensationOptions(),
                situationOptions(),
                careAvailabilityOptions(),
                preCareCheckOptions()
        );
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
