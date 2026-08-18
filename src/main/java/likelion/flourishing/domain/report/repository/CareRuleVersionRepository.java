package likelion.flourishing.domain.report.repository;

import java.util.List;
import java.util.UUID;
import likelion.flourishing.domain.report.entity.CareRuleVersion;
import likelion.flourishing.domain.report.entity.RuleReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareRuleVersionRepository extends JpaRepository<CareRuleVersion, UUID> {

    /** 한 세트에서 승인된 규칙 버전만. 검토 전 규칙으로는 관리 행동을 만들지 않는다. */
    List<CareRuleVersion> findAllByRuleSetIdAndReviewStatus(UUID ruleSetId, RuleReviewStatus reviewStatus);
}
