package likelion.flourishing.domain.report.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import likelion.flourishing.domain.report.entity.RuleCondition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuleConditionRepository extends JpaRepository<RuleCondition, UUID> {

    List<RuleCondition> findAllByRuleVersionIdIn(Collection<UUID> ruleVersionIds);
}
