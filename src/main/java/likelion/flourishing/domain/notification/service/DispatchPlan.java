package likelion.flourishing.domain.notification.service;

import java.util.List;
import java.util.UUID;
import likelion.flourishing.domain.notification.entity.NotificationType;

/**
 * 사용자 한 명에게 무엇을 어디로 보낼지 정한 결과.
 *
 * <p>이력 행(PENDING)을 이미 잡아 둔 뒤에 만들어진다. 이 값이 있으면 그날 그 사용자 자리는
 * 확보된 상태이고, 남은 일은 실제 발송과 결과 기록뿐이다.
 */
public record DispatchPlan(
        UUID deliveryId,
        NotificationType notificationType,
        UUID targetReportId,
        List<PushTarget> targets
) {
}
