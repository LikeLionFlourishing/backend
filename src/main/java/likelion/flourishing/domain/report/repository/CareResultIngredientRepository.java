package likelion.flourishing.domain.report.repository;

import java.util.List;
import java.util.UUID;
import likelion.flourishing.domain.report.entity.CareResultIngredient;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareResultIngredientRepository extends JpaRepository<CareResultIngredient, UUID> {

    List<CareResultIngredient> findAllByCareResultIdOrderByDisplayOrderAsc(UUID careResultId);
}
