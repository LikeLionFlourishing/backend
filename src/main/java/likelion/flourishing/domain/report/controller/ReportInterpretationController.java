package likelion.flourishing.domain.report.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.report.dto.request.ReportInterpretationRequest;
import likelion.flourishing.domain.report.dto.response.ReportInterpretationResponse;
import likelion.flourishing.domain.report.service.ReportInterpretationService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 한 문장 구조화 엔드포인트.
 *
 * <p>아무것도 저장하지 않지만 POST다. 서버 상태를 바꾸지 않아도 외부 모델을 호출하는 작업이라
 * 캐시되거나 미리 불려서는 안 되고, 원문을 URL에 실을 수도 없다.
 *
 * <p>AI가 실패해도 200이다. 응답의 processingStatus로 구분하고, 사용자는 직접 골라 그대로 진행한다.
 */
@Tag(name = "Reports", description = "피부 보고 구조화와 생성")
@RestController
@RequestMapping("/v1/report-interpretations")
public class ReportInterpretationController {

    private final ReportInterpretationService reportInterpretationService;

    public ReportInterpretationController(ReportInterpretationService reportInterpretationService) {
        this.reportInterpretationService = reportInterpretationService;
    }

    @Operation(summary = "한 문장 구조화")
    @PostMapping
    public ResponseEntity<ReportInterpretationResponse> interpret(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ReportInterpretationRequest request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(reportInterpretationService.interpret(principal, request));
    }
}
