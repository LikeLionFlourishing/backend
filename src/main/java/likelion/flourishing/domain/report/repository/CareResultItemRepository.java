package likelion.flourishing.domain.report.repository;

import java.util.List;
import java.util.UUID;
import likelion.flourishing.domain.report.entity.CareResultItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareResultItemRepository extends JpaRepository<CareResultItem, UUID> {

    List<CareResultItem> findAllByCareResultIdOrderByItemTypeAscDisplayOrderAsc(UUID careResultId);

    /**
     * 관리 설명을 다시 만들 때 기존 항목을 지운다.
     *
     * <p>(care_result_id, item_type, display_order) 유니크 제약이 있어 새 항목을 넣기 전에
     * 비워야 한다. 지우고 넣는 두 단계가 같은 트랜잭션 안에서 끝나야 한다.
     */
    void deleteAllByCareResultId(UUID careResultId);
}
