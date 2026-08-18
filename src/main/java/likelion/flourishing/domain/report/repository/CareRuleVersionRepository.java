package likelion.flourishing.domain.report.repository;

import java.util.UUID;
import likelion.flourishing.domain.report.entity.CareRuleVersion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareRuleVersionRepository extends JpaRepository<CareRuleVersion, UUID> {
}
