package likelion.flourishing.domain.report.rule;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 활성 관리 규칙 조회 담당.
 *
 * <p>규칙 최종본을 아직 받지 못했으므로 조회 계약만 먼저 고정한다. 규칙 데이터가 채워지면
 * 구현을 바꾸지 않고 행만 늘어난다.
 */
public interface CareRuleCatalogPort {

    /**
     * 활성 규칙 세트와 그 안의 승인된 규칙을 모두 읽는다.
     *
     * <p>비어 있으면 결과를 만들 기준이 없다는 뜻이다. 호출한 쪽은 관리 행동을 임의로 만들지 않고
     * 503으로 답해야 한다.
     */
    Optional<ActiveRuleCatalog> loadActiveCatalog();

    /**
     * 이미 만들어진 결과에 적용된 규칙 버전을 그대로 읽는다.
     *
     * <p>관리 설명을 다시 만들 때 쓴다. 다시 만드는 것은 문구뿐이고 어떤 규칙이 걸렸는지는 처음
     * 결정한 그대로여야 한다. 지금 활성 세트로 다시 판단하면 같은 보고의 근거가 바뀐다.
     *
     * <p>승인 상태는 보지 않는다. 그 뒤에 은퇴한 규칙이라도 당시 적용된 근거이기 때문이다. 다만
     * 결과에 남은 세트와 다른 세트의 버전이 섞여 있으면 스냅샷이 깨진 상태라 빈 값을 돌린다.
     *
     * @param ruleVersionIds care_result_rules에 남은 규칙 버전. 적용 순서대로 넘긴다.
     */
    Optional<AppliedRuleSet> loadAppliedRules(UUID ruleSetId, List<UUID> ruleVersionIds);
}
