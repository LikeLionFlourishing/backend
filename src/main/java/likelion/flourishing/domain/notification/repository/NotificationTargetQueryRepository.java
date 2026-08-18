package likelion.flourishing.domain.notification.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * 17:30 발송 대상을 고르는 조회 전용 저장소.
 *
 * <p>skin_reports와 daily_check_ins는 다른 태그 담당자의 영역이라 엔티티를 만들지 않았다.
 * 같은 테이블에 엔티티가 두 벌 생기면 그쪽 설계와 충돌한다. 필요한 컬럼만 네이티브 SQL로 읽고
 * 쓰기는 하지 않는다.
 *
 * <p>ID는 BINARY(16)이고 애플리케이션이 바이트 순서를 바꾸지 않고 저장하므로 반드시 인자 없는
 * {@code BIN_TO_UUID(id)}, {@code UUID_TO_BIN(?)}을 써야 한다. 두 번째 인자에 1을 넣으면
 * 바이트가 뒤바뀌어 엉뚱한 값이 된다.
 */
@Repository
public class NotificationTargetQueryRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 그날 평가할 사용자 목록.
     *
     * <p>알림을 켰고, 발송 시각이 지금이고, 살아 있는 구독이 하나라도 있고, 그날 발송 이력이
     * 아직 없는 사용자만 고른다. 이력이 있는 사용자를 SQL에서 미리 걸러 두면 재실행 때 헛돌지
     * 않는다. 다만 이 조회만으로 중복을 막지는 않는다. 실제 차단은 PENDING 행 삽입과 유니크
     * 제약이 한다.
     *
     * <p>명세 v2_1에서 발송 시각이 사용자마다 달라져 시각 조건이 들어왔다. 정확히 일치하는 분에만
     * 고르므로 그 분의 실행이 통째로 빠지면 그날은 건너뛴다. 이전에도 17:30 실행 하나가 실패하면
     * 같은 결과였으므로 동작이 나빠지지는 않지만, 놓친 분을 따라잡는 처리는 별도 과제다.
     */
    public List<UUID> findUserIdsToEvaluate(LocalDate notificationDate, String notificationTime) {
        Query query = entityManager.createNativeQuery("""
                SELECT BIN_TO_UUID(s.user_id)
                FROM notification_settings s
                WHERE s.enabled = TRUE
                  AND s.notification_time = :notificationTime
                  AND EXISTS (
                      SELECT 1 FROM push_subscriptions p
                      WHERE p.user_id = s.user_id AND p.active = TRUE
                  )
                  AND NOT EXISTS (
                      SELECT 1 FROM notification_deliveries d
                      WHERE d.user_id = s.user_id AND d.notification_date = :notificationDate
                  )
                ORDER BY s.user_id
                """);
        query.setParameter("notificationDate", notificationDate);
        query.setParameter("notificationTime", notificationTime);

        @SuppressWarnings("unchecked")
        List<String> rows = query.getResultList();
        return rows.stream().map(UUID::fromString).toList();
    }

    /**
     * 아직 경과를 입력하지 않은 보고 중 가장 오래된 한 건.
     *
     * <p>입력 기한이 지난 건은 제외한다. 기한이 지나면 보고 상태가 EXPIRED로 정리되지만
     * 정리 시점과 조회 시점이 어긋날 수 있어 만료 시각으로 한 번 더 거른다.
     */
    public Optional<UUID> findOldestPendingFollowUpReportId(UUID userId, LocalDateTime now) {
        Query query = entityManager.createNativeQuery("""
                SELECT BIN_TO_UUID(r.id)
                FROM skin_reports r
                WHERE r.user_id = UUID_TO_BIN(:userId)
                  AND r.status = 'FOLLOW_UP_PENDING'
                  AND r.follow_up_expires_at > :now
                ORDER BY r.report_date ASC
                LIMIT 1
                """);
        query.setParameter("userId", userId.toString());
        query.setParameter("now", now);

        @SuppressWarnings("unchecked")
        List<String> rows = query.getResultList();
        return rows.isEmpty() ? Optional.empty() : Optional.of(UUID.fromString(rows.get(0)));
    }

}
