package likelion.flourishing.domain.notification.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import likelion.flourishing.domain.notification.crypto.EndpointFingerprint;
import likelion.flourishing.domain.notification.entity.PushSubscription;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 명세 PushSubscription 스키마.
 *
 * <p>endpoint 원문과 p256dh, auth는 어떤 경우에도 내보내지 않는다. endpoint는 그 자체로 알림을
 * 보낼 수 있는 capability라서 응답과 로그에 전체 값이 남으면 안 된다. 클라이언트가 자기 구독을
 * 식별할 수 있어야 하므로 되짚을 수 없는 지문만 준다.
 *
 * <p>명세가 additionalProperties: false라 정의되지 않은 필드는 내보내지 않는다. 브라우저가 알려 준
 * 만료 시각은 저장만 하고 응답에는 담지 않는다.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.ALWAYS)
public class PushSubscriptionResponse {

    private final UUID id;

    private final String endpointFingerprint;

    private final boolean active;

    private final OffsetDateTime createdAt;

    private final OffsetDateTime updatedAt;

    public static PushSubscriptionResponse of(
            UUID id,
            String endpointFingerprint,
            boolean active,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        return new PushSubscriptionResponse(id, endpointFingerprint, active, createdAt, updatedAt);
    }

    public static PushSubscriptionResponse from(PushSubscription subscription) {
        return new PushSubscriptionResponse(
                subscription.getId(),
                EndpointFingerprint.toHex(subscription.getEndpointFingerprint()),
                subscription.isActive(),
                atUtc(subscription.getCreatedAt()),
                atUtc(subscription.getUpdatedAt())
        );
    }

    private static OffsetDateTime atUtc(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
