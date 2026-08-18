package likelion.flourishing.domain.report.repository;

import java.util.Optional;
import java.util.UUID;
import likelion.flourishing.domain.report.entity.RuleSet;
import likelion.flourishing.domain.report.entity.RuleSetStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuleSetRepository extends JpaRepository<RuleSet, UUID> {

    /**
     * 활성 규칙 세트. ACTIVE는 DDL 제약상 전역에 하나뿐이라 Optional로 받는다.
     *
     * <p>비어 있으면 새 관리 결과를 만들 기준이 없다는 뜻이므로 호출한 쪽이 503으로 돌린다.
     */
    Optional<RuleSet> findFirstByStatus(RuleSetStatus status);
}
