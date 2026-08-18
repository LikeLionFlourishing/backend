package likelion.flourishing.domain.report.rule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import likelion.flourishing.domain.report.entity.CareRule;
import likelion.flourishing.domain.report.entity.CareRuleVersion;
import likelion.flourishing.domain.report.entity.RuleAction;
import likelion.flourishing.domain.report.entity.RuleCondition;
import likelion.flourishing.domain.report.entity.RuleReviewStatus;
import likelion.flourishing.domain.report.entity.RuleSet;
import likelion.flourishing.domain.report.entity.RuleSetStatus;
import likelion.flourishing.domain.report.repository.CareRuleRepository;
import likelion.flourishing.domain.report.repository.CareRuleVersionRepository;
import likelion.flourishing.domain.report.entity.CareIngredient;
import likelion.flourishing.domain.report.entity.RuleVersionIngredient;
import likelion.flourishing.domain.report.repository.CareIngredientRepository;
import likelion.flourishing.domain.report.repository.RuleVersionIngredientRepository;
import likelion.flourishing.domain.report.repository.RuleActionRepository;
import likelion.flourishing.domain.report.repository.RuleConditionRepository;
import likelion.flourishing.domain.report.repository.RuleSetRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리 규칙 테이블을 읽어 카탈로그로 옮기는 구현.
 *
 * <p>규칙 하나당 조회를 반복하지 않도록 세트 단위로 한 번에 읽고 메모리에서 묶는다. 규칙 수는
 * 수십 개 규모이고 요청마다 전부 필요해서 이 방식이 단순하고 예측 가능하다.
 *
 * <p>승인되지 않은 규칙 버전은 아예 읽지 않는다. 검토 전 규칙이 실수로 결과에 섞이는 경로를
 * 코드 수준에서 없애기 위해서다.
 */
@Component
public class CareRuleCatalogAdapter implements CareRuleCatalogPort {

    private static final String FORBIDDEN_EXPRESSION_DELIMITER = "[\\r\\n,]+";

    private final RuleSetRepository ruleSetRepository;
    private final CareRuleVersionRepository careRuleVersionRepository;
    private final CareRuleRepository careRuleRepository;
    private final RuleConditionRepository ruleConditionRepository;
    private final RuleActionRepository ruleActionRepository;
    private final RuleVersionIngredientRepository ruleVersionIngredientRepository;
    private final CareIngredientRepository careIngredientRepository;

    public CareRuleCatalogAdapter(
            RuleSetRepository ruleSetRepository,
            CareRuleVersionRepository careRuleVersionRepository,
            CareRuleRepository careRuleRepository,
            RuleConditionRepository ruleConditionRepository,
            RuleActionRepository ruleActionRepository,
            RuleVersionIngredientRepository ruleVersionIngredientRepository,
            CareIngredientRepository careIngredientRepository
    ) {
        this.ruleSetRepository = ruleSetRepository;
        this.careRuleVersionRepository = careRuleVersionRepository;
        this.careRuleRepository = careRuleRepository;
        this.ruleConditionRepository = ruleConditionRepository;
        this.ruleActionRepository = ruleActionRepository;
        this.ruleVersionIngredientRepository = ruleVersionIngredientRepository;
        this.careIngredientRepository = careIngredientRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ActiveRuleCatalog> loadActiveCatalog() {
        Optional<RuleSet> activeSet = ruleSetRepository.findFirstByStatus(RuleSetStatus.ACTIVE);
        if (activeSet.isEmpty()) {
            return Optional.empty();
        }

        RuleSet ruleSet = activeSet.get();
        List<CareRuleVersion> versions = careRuleVersionRepository
                .findAllByRuleSetIdAndReviewStatus(ruleSet.getId(), RuleReviewStatus.APPROVED);
        if (versions.isEmpty()) {
            return Optional.empty();
        }

        List<UUID> versionIds = versions.stream().map(CareRuleVersion::getId).toList();
        Map<UUID, CareRule> rules = careRuleRepository
                .findAllById(versions.stream().map(CareRuleVersion::getRuleId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(CareRule::getId, Function.identity()));
        Map<UUID, List<RuleCondition>> conditions = ruleConditionRepository
                .findAllByRuleVersionIdIn(versionIds).stream()
                .collect(Collectors.groupingBy(RuleCondition::getRuleVersionId));
        Map<UUID, List<RuleAction>> actions = ruleActionRepository
                .findAllByRuleVersionIdInAndActiveTrue(versionIds).stream()
                .collect(Collectors.groupingBy(RuleAction::getRuleVersionId));

        Map<UUID, List<IngredientSnapshot>> ingredients = loadIngredients(versionIds);

        List<CareRuleSnapshot> snapshots = toSnapshots(versions, rules, conditions, actions, ingredients);
        return Optional.of(new ActiveRuleCatalog(ruleSet.getId(), ruleSet.getVersionCode(), snapshots));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AppliedRuleSet> loadAppliedRules(UUID ruleSetId, List<UUID> ruleVersionIds) {
        Optional<RuleSet> ruleSet = ruleSetRepository.findById(ruleSetId);
        if (ruleSet.isEmpty() || ruleVersionIds.isEmpty()) {
            return Optional.empty();
        }

        List<CareRuleVersion> versions = careRuleVersionRepository.findAllById(ruleVersionIds);
        boolean snapshotBroken = versions.size() != ruleVersionIds.size()
                || versions.stream().anyMatch(version -> !version.getRuleSetId().equals(ruleSetId));
        if (snapshotBroken) {
            return Optional.empty();
        }

        Map<UUID, CareRule> rules = careRuleRepository
                .findAllById(versions.stream().map(CareRuleVersion::getRuleId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(CareRule::getId, Function.identity()));
        Map<UUID, List<RuleAction>> actions = ruleActionRepository
                .findAllByRuleVersionIdIn(ruleVersionIds).stream()
                .collect(Collectors.groupingBy(RuleAction::getRuleVersionId));

        // 넘어온 순서가 곧 적용 순서다. 조회 결과 순서에 기대지 않고 인자 순서대로 다시 세운다.
        Map<UUID, CareRuleVersion> versionsById = versions.stream()
                .collect(Collectors.toMap(CareRuleVersion::getId, Function.identity()));
        List<CareRuleVersion> ordered = ruleVersionIds.stream().map(versionsById::get).toList();
        return Optional.of(new AppliedRuleSet(
                ruleSet.get().getVersionCode(),
                toSnapshots(ordered, rules, Map.of(), actions, loadIngredients(ruleVersionIds))
        ));
    }

    /**
     * 규칙 버전별 추천 성분.
     *
     * <p>내려 둔 성분(active = false)은 읽지 않는다. 규칙이 가리키고 있어도 결과에 담기지
     * 않아야 하고, 여기서 거르면 아래 단계가 활성 여부를 다시 볼 필요가 없다.
     */
    private Map<UUID, List<IngredientSnapshot>> loadIngredients(List<UUID> versionIds) {
        List<RuleVersionIngredient> links = ruleVersionIngredientRepository
                .findAllByIdRuleVersionIdIn(versionIds);
        if (links.isEmpty()) {
            return Map.of();
        }

        Map<UUID, CareIngredient> ingredientsById = careIngredientRepository
                .findAllByIdInAndActiveTrue(links.stream().map(RuleVersionIngredient::ingredientId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(CareIngredient::getId, Function.identity()));

        Map<UUID, List<IngredientSnapshot>> byVersion = new LinkedHashMap<>();
        links.stream()
                .sorted(Comparator.comparingInt(RuleVersionIngredient::getDisplayOrder))
                .forEach(link -> {
                    CareIngredient ingredient = ingredientsById.get(link.ingredientId());
                    if (ingredient == null) {
                        return;
                    }
                    byVersion.computeIfAbsent(link.ruleVersionId(), key -> new ArrayList<>())
                            .add(new IngredientSnapshot(
                                    ingredient.getId(),
                                    ingredient.getIngredientCode(),
                                    ingredient.getName(),
                                    ingredient.getDescription(),
                                    ingredient.getCautionNote(),
                                    link.getDisplayOrder()
                            ));
                });
        return byVersion;
    }

    private List<CareRuleSnapshot> toSnapshots(
            List<CareRuleVersion> versions,
            Map<UUID, CareRule> rules,
            Map<UUID, List<RuleCondition>> conditions,
            Map<UUID, List<RuleAction>> actions,
            Map<UUID, List<IngredientSnapshot>> ingredients
    ) {
        List<CareRuleSnapshot> snapshots = new ArrayList<>();
        for (CareRuleVersion version : versions) {
            CareRule rule = rules.get(version.getRuleId());
            if (rule == null) {
                // 규칙 정의 없는 버전은 참조 무결성이 깨진 상태다. 그 규칙만 빼면 사용자에게
                // 근거 없는 결과를 주게 되므로 결과 생성을 아예 막는다.
                throw new IllegalStateException("관리 규칙 버전에 대응하는 규칙 정의가 없습니다.");
            }
            snapshots.add(new CareRuleSnapshot(
                    version.getId(),
                    rule.getId(),
                    rule.getRuleCode(),
                    rule.getCategory(),
                    version.getPriority(),
                    version.getApplicationSummary(),
                    version.getFallbackText(),
                    splitForbiddenExpressions(version.getForbiddenExpressions()),
                    toConditionSpecs(conditions.getOrDefault(version.getId(), List.of())),
                    toActionSnapshots(actions.getOrDefault(version.getId(), List.of())),
                    ingredients.getOrDefault(version.getId(), List.of())
            ));
        }
        return snapshots;
    }

    private List<RuleConditionSpec> toConditionSpecs(List<RuleCondition> conditions) {
        return conditions.stream()
                .sorted(Comparator.comparingInt(RuleCondition::getConditionGroup)
                        .thenComparingInt(RuleCondition::getDisplayOrder))
                .map(condition -> new RuleConditionSpec(
                        condition.getConditionGroup(),
                        condition.getFieldCode(),
                        condition.getOperatorCode(),
                        condition.getValueCode(),
                        condition.isNegated()
                ))
                .toList();
    }

    private List<RuleActionSnapshot> toActionSnapshots(List<RuleAction> actions) {
        return actions.stream()
                .sorted(Comparator.comparingInt(RuleAction::getPriority)
                        .thenComparingInt(RuleAction::getDisplayOrder))
                .map(action -> new RuleActionSnapshot(
                        action.getId(),
                        action.getActionType(),
                        action.getContent(),
                        action.getPriority(),
                        action.getDisplayOrder()
                ))
                .toList();
    }

    /** 금지 표현은 한 컬럼에 줄바꿈이나 쉼표로 구분해 적는다. 빈 조각은 버린다. */
    private List<String> splitForbiddenExpressions(String forbiddenExpressions) {
        if (forbiddenExpressions == null || forbiddenExpressions.isBlank()) {
            return List.of();
        }
        return Arrays.stream(forbiddenExpressions.split(FORBIDDEN_EXPRESSION_DELIMITER))
                .map(String::trim)
                .filter(expression -> !expression.isEmpty())
                .toList();
    }
}
