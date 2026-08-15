package likelion.flourishing.domain.notification.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.notification.dto.request.RegisterPushSubscriptionRequest;
import likelion.flourishing.domain.notification.dto.response.PushSubscriptionResponse;
import likelion.flourishing.domain.notification.service.PushSubscriptionService;
import likelion.flourishing.domain.notification.service.SavedPushSubscription;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PWA Push 구독 엔드포인트.
 *
 * <p>등록은 새로 만들었으면 201, 같은 endpoint가 이미 있으면 200으로 나눈다. 브라우저는 같은
 * 구독을 여러 번 보낼 수 있어 두 경우를 모두 정상으로 다룬다.
 *
 * <p>응답에는 endpoint 지문만 담는다. 원문 endpoint와 키는 어떤 경로로도 돌려주지 않는다.
 */
@Tag(name = "Notifications", description = "PWA Push 구독")
@RestController
@RequestMapping("/v1/push-subscriptions")
public class PushSubscriptionController {

    private final PushSubscriptionService pushSubscriptionService;

    public PushSubscriptionController(PushSubscriptionService pushSubscriptionService) {
        this.pushSubscriptionService = pushSubscriptionService;
    }

    @Operation(summary = "Push 구독 등록")
    @PostMapping
    public ResponseEntity<PushSubscriptionResponse> register(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody RegisterPushSubscriptionRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent
    ) {
        SavedPushSubscription saved = pushSubscriptionService.register(principal, request, userAgent);
        return ResponseEntity.status(saved.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .cacheControl(CacheControl.noStore())
                .body(saved.response());
    }

    @Operation(summary = "Push 구독 해제")
    @DeleteMapping("/{subscriptionId}")
    public ResponseEntity<Void> unregister(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID subscriptionId
    ) {
        pushSubscriptionService.unregister(principal, subscriptionId);
        return ResponseEntity.noContent().build();
    }
}
