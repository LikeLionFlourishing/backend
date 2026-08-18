package likelion.flourishing.domain.report.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import likelion.flourishing.domain.report.entity.CareIngredient;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareIngredientRepository extends JpaRepository<CareIngredient, UUID> {

    /** 활성 성분만 읽는다. 규칙이 가리켜도 내려 둔 성분은 결과에 담지 않는다. */
    List<CareIngredient> findAllByIdInAndActiveTrue(Collection<UUID> ids);
}
