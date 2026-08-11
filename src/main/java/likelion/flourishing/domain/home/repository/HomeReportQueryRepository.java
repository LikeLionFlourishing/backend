package likelion.flourishing.domain.home.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * 홈 화면이 필요로 하는 피부 보고 정보만 읽는 조회 전용 저장소.
 *
 * <p>skin_reports, follow_ups, report_* 테이블은 Reports 태그 담당자의 영역이라 엔티티를
 * 만들지 않았다. 같은 테이블에 엔티티가 두 벌 생기면 나중에 그쪽 설계와 충돌하기 때문이다.
 * 대신 네이티브 SQL로 필요한 컬럼만 읽고 결과를 record로 옮긴다. 쓰기는 하지 않는다.
 *
 * <p>Reports 기능이 올라오면 이 클래스를 그쪽이 제공하는 조회 메서드로 갈아끼우는 것이 맞다.
 *
 * <p>ID는 BINARY(16)이고 애플리케이션이 바이트 순서를 바꾸지 않고 저장하므로
 * 반드시 인자 없는 {@code BIN_TO_UUID(id)}, {@code UUID_TO_BIN(?)}을 써야 한다.
 * 두 번째 인자에 1을 넣으면 바이트가 뒤바뀌어 엉뚱한 값이 된다.
 */
@Repository
public class HomeReportQueryRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 아직 경과를 입력하지 않은 보고 중 가장 오래된 한 건.
     *
     * <p>입력 기한이 지난 건은 제외한다. 기한이 지나면 보고 상태가 EXPIRED로 정리되지만,
     * 정리 시점과 조회 시점이 어긋날 수 있어 만료 시각으로 한 번 더 거른다.
     */
    public Optional<PendingFollowUpRow> findOldestPendingFollowUp(UUID userId, LocalDateTime now) {
        Query query = entityManager.createNativeQuery("""
                SELECT BIN_TO_UUID(r.id), r.report_date, r.follow_up_available_at,
                       r.follow_up_expires_at, r.result_type
                FROM skin_reports r
                WHERE r.user_id = UUID_TO_BIN(:userId)
                  AND r.status = 'FOLLOW_UP_PENDING'
                  AND r.follow_up_expires_at > :now
                ORDER BY r.report_date ASC
                LIMIT 1
                """);
        query.setParameter("userId", userId.toString());
        query.setParameter("now", now);

        return firstRow(query).map(row -> new PendingFollowUpRow(
                UUID.fromString((String) row[0]),
                toLocalDate(row[1]),
                toLocalDateTime(row[2]),
                toLocalDateTime(row[3]),
                (String) row[4]
        ));
    }

    /** 가장 최근 보고 한 건. 경과가 저장돼 있으면 피부 변화도 함께 읽는다. */
    public Optional<RecentReportRow> findMostRecentReport(UUID userId) {
        Query query = entityManager.createNativeQuery("""
                SELECT BIN_TO_UUID(r.id), r.report_date, r.primary_area,
                       r.result_type, r.status, f.skin_change
                FROM skin_reports r
                LEFT JOIN follow_ups f ON f.report_id = r.id
                WHERE r.user_id = UUID_TO_BIN(:userId)
                ORDER BY r.report_date DESC
                LIMIT 1
                """);
        query.setParameter("userId", userId.toString());

        return firstRow(query).map(row -> new RecentReportRow(
                UUID.fromString((String) row[0]),
                toLocalDate(row[1]),
                (String) row[2],
                (String) row[3],
                (String) row[4],
                (String) row[5]
        ));
    }

    public List<String> findAppearanceCodes(UUID reportId) {
        return findCodes("report_appearances", "appearance_code", reportId);
    }

    public List<String> findSensationCodes(UUID reportId) {
        return findCodes("report_sensations", "sensation_code", reportId);
    }

    public List<String> findSituationCodes(UUID reportId) {
        return findCodes("report_situations", "situation_code", reportId);
    }

    /**
     * 선택값 코드 목록. 테이블명과 컬럼명은 이 클래스 안에서만 넘어오는 고정 문자열이라
     * 사용자 입력이 SQL에 섞이지 않는다. 값(report_id)은 바인딩 파라미터로만 전달한다.
     */
    private List<String> findCodes(String tableName, String columnName, UUID reportId) {
        Query query = entityManager.createNativeQuery("""
                SELECT %s
                FROM %s
                WHERE report_id = UUID_TO_BIN(:reportId)
                ORDER BY created_at, %s
                """.formatted(columnName, tableName, columnName));
        query.setParameter("reportId", reportId.toString());

        @SuppressWarnings("unchecked")
        List<String> codes = query.getResultList();
        return codes;
    }

    private Optional<Object[]> firstRow(Query query) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private LocalDate toLocalDate(Object value) {
        return value instanceof java.sql.Date date ? date.toLocalDate() : (LocalDate) value;
    }

    private LocalDateTime toLocalDateTime(Object value) {
        return value instanceof Timestamp timestamp ? timestamp.toLocalDateTime() : (LocalDateTime) value;
    }

    /** 명세 PendingFollowUp에 필요한 값. resultType은 DDL의 CHECK가 값을 보장한다. */
    public record PendingFollowUpRow(
            UUID reportId,
            LocalDate reportDate,
            LocalDateTime availableFrom,
            LocalDateTime expiresAt,
            String resultType
    ) {
    }

    /** 명세 SkinReportSummary 중 선택값 목록을 뺀 나머지. skinChange는 경과가 없으면 null이다. */
    public record RecentReportRow(
            UUID id,
            LocalDate reportDate,
            String primaryArea,
            String resultType,
            String status,
            String skinChange
    ) {
    }
}
