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
     * <p>명세 v2_1에서 발송 시각이 사용자마다 달라져 시각 조건이 들어왔다. 조건을 등호가 아니라
     * "지금까지"로 두어, 어떤 분의 실행이 통째로 빠져도 다음 분에 저절로 따라잡히게 한다.
     *
     * <p>등호로 두면 그 분에 실행이 없었던 사용자는 그날을 통째로 건너뛴다. 스케줄러 스레드가
     * 하나뿐이라(pool 설정 없음) 앞 실행이 60초를 넘기거나 배포·재시작이 그 분에 걸리면 실제로
     * 일어난다. 대상이 많은 기본 시각(17:30)일수록 걸리기 쉽다.
     *
     * <p>여러 번 돌아도 한 번만 나간다. 그날 발송 이력이 있는 사용자를 여기서 이미 빼고, 실제
     * 중복 차단은 PENDING 행 삽입과 (user_id, notification_date) 유니크 제약이 한다.
     *
     * <p>알아 두어야 할 동작: 하루 중간에 배포하면 그 시각 이전으로 설정한 사용자들에게 그 순간
     * 한꺼번에 나간다. 아직 그날 알림을 받지 못한 사람들이라 늦게라도 받는 편이 낫다고 봤다.
     * "지난 시각은 그냥 넘긴다"로 바꾸려면 이 조건을 등호로 되돌리면 된다.
     *
     * <p>HH:mm은 제로 패딩이라 문자열 비교가 시각 순서와 같다.
     */
    public List<UUID> findUserIdsToEvaluate(LocalDate notificationDate, String notificationTime) {
        Query query = entityManager.createNativeQuery("""
                SELECT BIN_TO_UUID(s.user_id)
                FROM notification_settings s
                WHERE s.enabled = TRUE
                  AND s.notification_time <= :notificationTime
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
