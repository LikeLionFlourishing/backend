package likelion.flourishing.domain.report.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.report.dto.request.CreateSkinReportRequest;
import likelion.flourishing.domain.record.dto.response.CareResultResponse;
import likelion.flourishing.domain.record.dto.response.SkinReportDetailResponse;
import likelion.flourishing.domain.report.idempotency.IdempotentResponse;
import likelion.flourishing.domain.report.service.CareGuideRegenerationService;
import likelion.flourishing.domain.report.service.SkinReportSubmissionService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 보고 생성과 관리 설명 재생성 엔드포인트.
 *
 * <p>본문을 DTO가 아니라 직렬화된 JSON 문자열로 내보낸다. 같은 Idempotency-Key로 다시 온 요청에는
 * 처음 보낸 것과 완전히 같은 본문을 돌려줘야 하고, 저장한 JSON을 객체로 되돌렸다가 다시 만들면
 * 필드 순서나 null 표현이 달라질 수 있다. 응답 스키마는 어노테이션으로 따로 알린다.
 *
 * <p>Idempotency-Key는 보고 생성에서 필수다. 헤더가 없으면 Spring이 400으로 막는다. 결과를 만드는
 * 데 외부 모델 호출이 끼어 있어 재실행되면 사용자에게 다른 결과가 두 개 생긴다.
 */
@Tag(name = "Reports", description = "피부 보고 구조화와 생성")
@RestController
@RequestMapping("/v1/skin-reports")
public class SkinReportController {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final SkinReportSubmissionService skinReportSubmissionService;
    private final CareGuideRegenerationService careGuideRegenerationService;

    public SkinReportController(
            SkinReportSubmissionService skinReportSubmissionService,
            CareGuideRegenerationService careGuideRegenerationService
    ) {
        this.skinReportSubmissionService = skinReportSubmissionService;
        this.careGuideRegenerationService = careGuideRegenerationService;
    }

    @Operation(summary = "피부 보고 생성")
    @ApiResponse(
            responseCode = "201",
            content = @Content(schema = @Schema(implementation = SkinReportDetailResponse.class))
    )
    @PostMapping
    public ResponseEntity<String> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestHeader(IDEMPOTENCY_KEY_HEADER) UUID idempotencyKey,
            @Valid @RequestBody CreateSkinReportRequest request
    ) {
        IdempotentResponse response = skinReportSubmissionService.submit(principal, idempotencyKey, request);
        return body(response);
    }

    @Operation(summary = "관리 설명 재생성")
    @ApiResponse(
            responseCode = "200",
            content = @Content(schema = @Schema(implementation = CareResultResponse.class))
    )
    @PostMapping("/{reportId}/care-guide-generations")
    public ResponseEntity<String> regenerateCareGuide(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID reportId,
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) UUID idempotencyKey
    ) {
        IdempotentResponse response = careGuideRegenerationService
                .regenerate(principal, reportId, idempotencyKey);
        return body(response);
    }

    /**
     * 저장된 응답과 새 응답을 같은 방식으로 내보낸다.
     *
     * <p>201에는 Location을 붙인다. 재전송으로 되돌려 준 응답에도 같은 헤더가 나가야 클라이언트가
     * 처음 응답을 놓쳤을 때 리소스 위치를 알 수 있다.
     */
    private ResponseEntity<String> body(IdempotentResponse response) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(response.status())
                .contentType(MediaType.APPLICATION_JSON)
                .cacheControl(CacheControl.noStore());
        if (response.isCreated() && response.resourceId() != null) {
            builder.location(URI.create("/v1/skin-reports/" + response.resourceId()));
        }
        return builder.body(response.jsonBody());
    }
}
