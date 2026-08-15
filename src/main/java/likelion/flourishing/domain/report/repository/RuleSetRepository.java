package likelion.flourishing.domain.report.repository;

import java.util.UUID;
import likelion.flourishing.domain.report.entity.RuleSet;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuleSetRepository extends JpaRepository<RuleSet, UUID> {
}
