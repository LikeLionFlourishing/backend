package likelion.flourishing.domain.report.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import likelion.flourishing.domain.report.entity.RuleAction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuleActionRepository extends JpaRepository<RuleAction, UUID> {

    /** 아직 쓰는 문구만. 끈 문구는 과거 결과 스냅샷에만 남고 새 결과에는 들어가지 않는다. */
    List<RuleAction> findAllByRuleVersionIdInAndActiveTrue(Collection<UUID> ruleVersionIds);
}
