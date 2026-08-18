package likelion.flourishing.domain.report.repository;

import java.util.List;
import java.util.UUID;
import likelion.flourishing.domain.report.entity.CareResultItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareResultItemRepository extends JpaRepository<CareResultItem, UUID> {

    List<CareResultItem> findAllByCareResultIdOrderByItemTypeAscDisplayOrderAsc(UUID careResultId);
}
