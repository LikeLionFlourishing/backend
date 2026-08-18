package likelion.flourishing.domain.record.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.record.dto.response.SkinReportDetailResponse;
import likelion.flourishing.domain.record.dto.response.SkinReportListResponse;
import likelion.flourishing.domain.record.service.SkinRecordService;
import likelion.flourishing.domain.report.entity.ReportStatus;
import likelion.flourishing.domain.report.entity.ResultType;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 인증된 사용자의 피부 기록 목록과 상세만 반환한다. */
@Tag(name = "Records", description = "피부 기록 조회")
@RestController
@RequestMapping("/v1/skin-reports")
public class SkinRecordController {

    private final SkinRecordService skinRecordService;

    public SkinRecordController(SkinRecordService skinRecordService) {
        this.skinRecordService = skinRecordService;
    }

    @Operation(summary = "피부 기록 목록 조회")
    @GetMapping
    public ResponseEntity<SkinReportListResponse> getRecords(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false, defaultValue = "20") Integer limit,
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) ResultType resultType
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(skinRecordService.getRecords(principal, cursor, limit, status, resultType));
    }

    @Operation(summary = "피부 기록 상세 조회")
    @GetMapping("/{reportId}")
    public ResponseEntity<SkinReportDetailResponse> getRecord(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID reportId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(skinRecordService.getRecord(principal, reportId));
    }
}
