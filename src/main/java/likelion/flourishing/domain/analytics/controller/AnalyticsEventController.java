package likelion.flourishing.domain.analytics.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import likelion.flourishing.domain.analytics.dto.request.AnalyticsEventBatchRequest;
import likelion.flourishing.domain.analytics.dto.response.AnalyticsEventBatchResponse;
import likelion.flourishing.domain.analytics.service.AnalyticsEventService;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Analytics", description = "민감한 피부 정보를 제외한 사용자 행동 측정")
@RestController
@RequestMapping("/v1/analytics-events")
public class AnalyticsEventController {

    private final AnalyticsEventService analyticsEventService;

    public AnalyticsEventController(AnalyticsEventService analyticsEventService) {
        this.analyticsEventService = analyticsEventService;
    }

    @Operation(summary = "사용자 측정 이벤트 일괄 접수")
    @PostMapping
    public ResponseEntity<AnalyticsEventBatchResponse> collect(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody AnalyticsEventBatchRequest request
    ) {
        int acceptedCount = analyticsEventService.collect(principal, request);
        return ResponseEntity.accepted().body(AnalyticsEventBatchResponse.of(acceptedCount));
    }
}
