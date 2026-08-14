package likelion.flourishing.analytics.repository;

import java.time.LocalDateTime;
import java.util.UUID;
import likelion.flourishing.analytics.domain.AnalyticsEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, UUID> {

    /** event_id 충돌만 무시해 동시에 재전송된 요청도 중복 행을 만들지 않는다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO analytics_events (
                event_id, user_id, event_name, allowed_properties, occurred_at, received_at
            ) VALUES (
                :eventId, :userId, :eventName, CAST(:allowedProperties AS JSON), :occurredAt, :receivedAt
            )
            ON DUPLICATE KEY UPDATE event_id = event_id
            """, nativeQuery = true)
    int insertIdempotently(
            @Param("eventId") UUID eventId,
            @Param("userId") UUID userId,
            @Param("eventName") String eventName,
            @Param("allowedProperties") String allowedProperties,
            @Param("occurredAt") LocalDateTime occurredAt,
            @Param("receivedAt") LocalDateTime receivedAt
    );
}
