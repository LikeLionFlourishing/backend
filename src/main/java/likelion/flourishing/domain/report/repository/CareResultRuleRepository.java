package likelion.flourishing.domain.report.repository;

import java.util.List;
import java.util.UUID;
import likelion.flourishing.domain.report.entity.CareResultRule;
import likelion.flourishing.domain.report.entity.CareResultRuleId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareResultRuleRepository extends JpaRepository<CareResultRule, CareResultRuleId> {

    List<CareResultRule> findAllByIdCareResultIdOrderByApplicationOrder(UUID careResultId);
}
