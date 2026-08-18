package likelion.flourishing.domain.report.repository;

import java.util.Optional;
import java.util.UUID;
import likelion.flourishing.domain.record.repository.SkinReportQueryRepository;
import likelion.flourishing.domain.report.entity.SkinReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkinReportRepository extends JpaRepository<SkinReport, UUID>, SkinReportQueryRepository {

    Optional<SkinReport> findByIdAndUserId(UUID id, UUID userId);
}
