package likelion.flourishing.domain.report.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import likelion.flourishing.domain.report.entity.RuleVersionIngredient;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuleVersionIngredientRepository
        extends JpaRepository<RuleVersionIngredient, RuleVersionIngredient.Id> {

    List<RuleVersionIngredient> findAllByIdRuleVersionIdIn(Collection<UUID> ruleVersionIds);
}
