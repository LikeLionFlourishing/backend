package likelion.flourishing.domain.notification.repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import likelion.flourishing.domain.notification.entity.NotificationDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * notification_deliveries 테이블 접근.
 *
 * <p>조회 조건이 (사용자, 날짜)인 이유는 테이블의 유니크 제약과 같은 조합이기 때문이다.
 * 하루에 한 행만 존재하므로 Optional로 받는다.
 */
public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, UUID> {

    Optional<NotificationDelivery> findByUserIdAndNotificationDate(UUID userId, LocalDate notificationDate);
}
