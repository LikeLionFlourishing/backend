package likelion.flourishing.referencedata.service;

import java.util.Arrays;
import java.util.List;
import likelion.flourishing.referencedata.dto.response.OptionResponse;
import likelion.flourishing.referencedata.dto.response.SkinReportOptionsResponse;
import likelion.flourishing.report.domain.Appearance;
import likelion.flourishing.report.domain.BodyArea;
import likelion.flourishing.report.domain.CareAvailability;
import likelion.flourishing.report.domain.PreCareCheck;
import likelion.flourishing.report.domain.Sensation;
import likelion.flourishing.report.domain.Situation;
import org.springframework.stereotype.Service;

@Service
public class ReferenceDataService {

    private static final String OPTIONS_VERSION = "2026-08-09";
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
