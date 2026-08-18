package likelion.flourishing.domain.referencedata.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import likelion.flourishing.domain.referencedata.dto.response.SkinReportOptionsResponse;
import likelion.flourishing.domain.referencedata.service.ReferenceDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "ReferenceData", description = "피부 보고 선택값")
@RestController
@RequestMapping("/v1/reference-data")
@RequiredArgsConstructor
public class ReferenceDataController {

    private final ReferenceDataService referenceDataService;

    @Operation(summary = "피부 보고 선택값 조회")
    @GetMapping("/skin-report-options")
    public SkinReportOptionsResponse getSkinReportOptions() {
        return referenceDataService.getSkinReportOptions();
    }
}
