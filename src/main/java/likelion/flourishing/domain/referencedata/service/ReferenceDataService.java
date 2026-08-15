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
