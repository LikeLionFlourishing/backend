package likelion.flourishing.domain.report.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import likelion.flourishing.domain.record.repository.SkinReportQueryRepository;
import likelion.flourishing.domain.report.entity.ReportStatus;
import likelion.flourishing.domain.report.entity.SkinReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkinReportRepository extends JpaRepository<SkinReport, UUID>, SkinReportQueryRepository {

    Optional<SkinReport> findByIdAndUserId(UUID id, UUID userId);

    /** 하루 한 건 제한. 유니크 제약과 같은 조건이라 저장 전에 미리 걸러 409로 답한다. */
    boolean existsByUserIdAndReportDate(UUID userId, LocalDate reportDate);

    /**
     * 유사 경험 후보. 같은 사용자의 과거 날짜 완료 기록만 본다.
     *
     * <p>전체 기록을 다 비교할 필요는 없어 최근 것부터 제한된 개수만 가져온다. 오래된 기록은
     * 지금 상태와 견줄 근거가 약하고 비교 비용만 늘린다.
     */
    List<SkinReport> findTop30ByUserIdAndStatusAndReportDateLessThanOrderByReportDateDesc(
            UUID userId,
            ReportStatus status,
            LocalDate reportDate
    );
}
