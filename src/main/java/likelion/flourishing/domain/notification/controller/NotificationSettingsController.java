package likelion.flourishing.domain.notification.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.notification.dto.request.UpdateNotificationSettingsRequest;
import likelion.flourishing.domain.notification.dto.response.NotificationSettingsResponse;
import likelion.flourishing.domain.notification.service.NotificationSettingsPatchReader;
import likelion.flourishing.domain.notification.service.NotificationSettingsService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 알림 설정 엔드포인트.
 *
 * <p>발송 시각과 시간대는 P0 고정이라 조회에서만 보여 주고 변경은 사용 여부와 권한 상태만 받는다.
 *
 * <p>사용자별 설정이라 중간 캐시에 남지 않도록 no-store로 응답한다.
 */
@Tag(name = "Notifications", description = "알림 설정")
@RestController
@RequestMapping("/v1/me/notification-settings")
public class NotificationSettingsController {

    private final NotificationSettingsService notificationSettingsService;
    private final NotificationSettingsPatchReader patchReader;

    public NotificationSettingsController(
            NotificationSettingsService notificationSettingsService,
            NotificationSettingsPatchReader patchReader
    ) {
        this.notificationSettingsService = notificationSettingsService;
        this.patchReader = patchReader;
    }

    @Operation(summary = "알림 설정 조회")
    @GetMapping
    public ResponseEntity<NotificationSettingsResponse> getSettings(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(notificationSettingsService.getSettings(principal));
    }

    /**
     * 본문을 JsonNode 로 받는 이유는 "보내지 않음"과 "명시적 null"을 구분해야 하기 때문이다.
     * 문서에는 실제 스키마가 드러나야 하므로 @RequestBody 로 타입을 따로 알려 준다.
     */
    @Operation(summary = "알림 설정 변경")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(schema = @Schema(implementation = UpdateNotificationSettingsRequest.class))
    )
    @PatchMapping
    public ResponseEntity<NotificationSettingsResponse> updateSettings(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody JsonNode body
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(notificationSettingsService.updateSettings(principal, patchReader.read(body)));
    }
}
