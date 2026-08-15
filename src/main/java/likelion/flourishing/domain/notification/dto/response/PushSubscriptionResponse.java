package likelion.flourishing.domain.notification.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import likelion.flourishing.domain.notification.crypto.EndpointFingerprint;
import likelion.flourishing.domain.notification.entity.PushSubscription;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Push 구독 응답.
 *
 * <p>endpoint 원문과 p256dh, auth는 어떤 경우에도 내보내지 않는다. 클라이언트가 자기 구독을
 * 식별할 수 있어야 하므로 되짚을 수 없는 지문만 준다.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.ALWAYS)
public class PushSubscriptionResponse {

    private final UUID subscriptionId;

    private final String endpointFingerprint;

    private final boolean active;

    private final OffsetDateTime expiresAt;

    private final OffsetDateTime createdAt;

    private final OffsetDateTime updatedAt;

    public static PushSubscriptionResponse of(
            UUID subscriptionId,
            String endpointFingerprint,
            boolean active,
            OffsetDateTime expiresAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        return new PushSubscriptionResponse(
                subscriptionId, endpointFingerprint, active, expiresAt, createdAt, updatedAt
        );
    }

    public static PushSubscriptionResponse from(PushSubscription subscription) {
        return new PushSubscriptionResponse(
                subscription.getId(),
                EndpointFingerprint.toHex(subscription.getEndpointFingerprint()),
                subscription.isActive(),
                atUtc(subscription.getExpiresAt()),
                atUtc(subscription.getCreatedAt()),
                atUtc(subscription.getUpdatedAt())
        );
    }

    private static OffsetDateTime atUtc(java.time.LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
