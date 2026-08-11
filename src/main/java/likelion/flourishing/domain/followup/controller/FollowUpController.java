package likelion.flourishing.domain.followup.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.followup.dto.request.SaveFollowUpRequest;
import likelion.flourishing.domain.followup.dto.response.FollowUpResponse;
import likelion.flourishing.domain.followup.service.FollowUpService;
import likelion.flourishing.domain.followup.service.SavedFollowUp;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 경과 엔드포인트. 명세 FollowUps 태그의 두 개를 담당한다.
 *
 * <p>보고 하나에 경과 하나라서 목록이 아니라 단수 경로(/follow-up)를 쓴다.
 * 저장은 새로 만들었으면 201, 같은 내용이 이미 있으면 200으로 나눈다.
 *
 * <p>다른 사용자의 보고 번호는 존재 여부를 알리지 않고 404로 답한다.
 */
@Tag(name = "FollowUps", description = "다음 날 경과")
@RestController
@RequestMapping("/v1/skin-reports/{reportId}/follow-up")
public class FollowUpController {

    private final FollowUpService followUpService;

    public FollowUpController(FollowUpService followUpService) {
        this.followUpService = followUpService;
    }

    @Operation(summary = "다음 날 경과 조회")
    @GetMapping
    public ResponseEntity<FollowUpResponse> getFollowUp(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID reportId
    ) {
        return ResponseEntity.ok(followUpService.getFollowUp(principal, reportId));
    }

    @Operation(summary = "다음 날 경과 저장")
    @PutMapping
    public ResponseEntity<FollowUpResponse> saveFollowUp(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID reportId,
            @Valid @RequestBody SaveFollowUpRequest request
    ) {
        SavedFollowUp saved = followUpService.saveFollowUp(principal, reportId, request);
        return ResponseEntity.status(saved.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(saved.response());
    }
}
