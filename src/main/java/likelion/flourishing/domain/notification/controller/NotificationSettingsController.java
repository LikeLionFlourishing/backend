package likelion.flourishing.domain.notification.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.notification.dto.request.UpdateNotificationSettingsRequest;
import likelion.flourishing.domain.notification.dto.response.NotificationSettingsResponse;
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

    public NotificationSettingsController(NotificationSettingsService notificationSettingsService) {
        this.notificationSettingsService = notificationSettingsService;
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

    @Operation(summary = "알림 설정 변경")
    @PatchMapping
    public ResponseEntity<NotificationSettingsResponse> updateSettings(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UpdateNotificationSettingsRequest request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(notificationSettingsService.updateSettings(principal, request));
    }
}
