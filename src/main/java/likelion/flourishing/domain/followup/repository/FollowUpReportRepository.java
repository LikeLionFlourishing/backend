package likelion.flourishing.domain.followup.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * 경과 저장에 필요한 만큼만 skin_reports를 다루는 저장소.
 *
 * <p>skin_reports는 Reports 태그 담당자의 영역이라 엔티티를 만들지 않고 네이티브 SQL로
 * 접근한다. 홈에도 같은 방식의 조회 저장소가 있는데, 필요한 컬럼과 조건이 서로 달라
 * 합치지 않고 기능별로 따로 두었다. Reports 기능이 올라오면 둘 다 그쪽 메서드로 갈아끼운다.
 *
 * <p>여기서는 예외적으로 쓰기도 한다. 명세가 경과 저장과 보고의 COMPLETED 전환을 한 번의
 * 요청으로 묶었기 때문이다. 상태 한 컬럼만 바꾸고 다른 컬럼은 건드리지 않는다.
 *
 * <p>ID는 바이트 순서를 바꾸지 않고 저장하므로 인자 없는 {@code UUID_TO_BIN}을 써야 한다.
 */
@Repository
public class FollowUpReportRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /** 보고의 경과 관련 정보. 사용자가 다르면 빈 값이라 호출한 쪽이 404로 처리한다. */
    public Optional<ReportRow> findOwnedReport(UUID reportId, UUID userId) {
        Query query = entityManager.createNativeQuery("""
                SELECT r.result_type, r.status, r.follow_up_available_at, r.follow_up_expires_at
                FROM skin_reports r
                WHERE r.id = UUID_TO_BIN(:reportId)
                  AND r.user_id = UUID_TO_BIN(:userId)
                """);
        query.setParameter("reportId", reportId.toString());
        query.setParameter("userId", userId.toString());

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }

        Object[] row = rows.get(0);
        return Optional.of(new ReportRow(
                (String) row[0],
                (String) row[1],
                toLocalDateTime(row[2]),
                toLocalDateTime(row[3])
        ));
    }

    /**
     * 경과가 저장된 보고를 COMPLETED로 바꾼다.
     *
     * <p>이미 COMPLETED인 보고는 건드리지 않도록 status 조건을 함께 건다. 같은 보고에
     * 두 요청이 동시에 들어와도 상태를 두 번 바꾸지 않는다.
     */
    public void markCompleted(UUID reportId, UUID userId) {
        Query query = entityManager.createNativeQuery("""
                UPDATE skin_reports
                SET status = 'COMPLETED'
                WHERE id = UUID_TO_BIN(:reportId)
                  AND user_id = UUID_TO_BIN(:userId)
                  AND status <> 'COMPLETED'
                """);
        query.setParameter("reportId", reportId.toString());
        query.setParameter("userId", userId.toString());
        query.executeUpdate();
    }

    private LocalDateTime toLocalDateTime(Object value) {
        return value instanceof Timestamp timestamp ? timestamp.toLocalDateTime() : (LocalDateTime) value;
    }

    /**
     * @param resultType 보고의 결과 유형. 어떤 종류의 경과를 받아야 하는지 정한다.
     * @param status     보고 상태. FOLLOW_UP_PENDING, COMPLETED, EXPIRED 중 하나다.
     */
    public record ReportRow(
            String resultType,
            String status,
            LocalDateTime followUpAvailableAt,
            LocalDateTime followUpExpiresAt
    ) {
    }
}
