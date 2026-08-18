package likelion.flourishing.domain.report.repository;

import java.util.Optional;
import java.util.UUID;
import likelion.flourishing.domain.report.entity.CareResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareResultRepository extends JpaRepository<CareResult, UUID> {

    Optional<CareResult> findByReportIdAndUserId(UUID reportId, UUID userId);
}
