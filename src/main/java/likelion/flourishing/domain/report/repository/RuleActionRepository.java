package likelion.flourishing.domain.report.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import likelion.flourishing.domain.report.entity.RuleAction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuleActionRepository extends JpaRepository<RuleAction, UUID> {

    /** 아직 쓰는 문구만. 끈 문구는 과거 결과 스냅샷에만 남고 새 결과에는 들어가지 않는다. */
    List<RuleAction> findAllByRuleVersionIdInAndActiveTrue(Collection<UUID> ruleVersionIds);

    /**
     * 켜짐 여부를 가리지 않고 모두.
     *
     * <p>이미 만들어진 결과의 설명을 다시 만들 때 쓴다. 그 사이 문구를 껐다는 사실이 당시 적용된
     * 근거를 좁히지는 않는다. 켜진 것만 읽으면 재생성이 처음보다 적은 후보로 돌아간다.
     */
    List<RuleAction> findAllByRuleVersionIdIn(Collection<UUID> ruleVersionIds);
}
