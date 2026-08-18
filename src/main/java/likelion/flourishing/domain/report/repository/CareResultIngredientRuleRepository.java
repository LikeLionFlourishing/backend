package likelion.flourishing.domain.report.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import likelion.flourishing.domain.report.entity.CareResultIngredientRule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareResultIngredientRuleRepository
        extends JpaRepository<CareResultIngredientRule, CareResultIngredientRule.Id> {

    List<CareResultIngredientRule> findAllByIdCareResultIngredientIdIn(Collection<UUID> ingredientIds);
}
