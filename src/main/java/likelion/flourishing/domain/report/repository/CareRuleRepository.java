package likelion.flourishing.domain.report.repository;

import java.util.UUID;
import likelion.flourishing.domain.report.entity.CareRule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareRuleRepository extends JpaRepository<CareRule, UUID> {
}
