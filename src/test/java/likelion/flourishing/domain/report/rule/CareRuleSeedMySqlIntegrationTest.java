package likelion.flourishing.domain.report.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import likelion.flourishing.domain.report.entity.Appearance;
import likelion.flourishing.domain.report.entity.BodyArea;
import likelion.flourishing.domain.report.entity.CareAvailability;
import likelion.flourishing.domain.report.entity.ExpectedEnvironment;
import likelion.flourishing.domain.report.entity.PreCareCheck;
import likelion.flourishing.domain.report.entity.RuleActionType;
import likelion.flourishing.domain.report.entity.Sensation;
import likelion.flourishing.domain.report.entity.Situation;
import likelion.flourishing.domain.report.service.PlannedIngredient;
import likelion.flourishing.domain.report.service.RecommendedIngredientPlanner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/**
 * 운영 규칙 시드가 실제 MySQL에 들어가고 규칙 엔진이 그것을 읽어 쓰는지 확인한다.
 *
 * <p>다른 규칙 테스트는 가짜 fixture를 쓴다. 이 테스트만 {@code db/seed/20260818_care_rules_v0_3.sql}을
 * 컨테이너 초기화 스크립트로 그대로 먹인다. 시드가 CHECK 제약을 통과하는지, 조건이 실제 선택값과
 * 맞물리는지는 운영 데이터로만 확인할 수 있다. 문구를 고쳤다가 제약에 걸리는 실수도 여기서 잡힌다.
 *
 * <p>AI는 부르지 않는다. 카탈로그 조회 → 규칙 매칭 → 성분 선정까지가 확인 범위다. 결과 저장까지는
 * 보고와 사용자 데이터가 필요해서 별도 통합 테스트가 맡는다.
 */
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CareRuleSeedMySqlIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("flourishing")
            .withUsername("test")
            .withPassword("test")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(Path.of("db/schema.sql").toAbsolutePath()),
                    "/docker-entrypoint-initdb.d/001-schema.sql"
            )
            .withCopyFileToContainer(
                    MountableFile.forHostPath(
                            Path.of("db/seed/20260818_care_rules_v0_3.sql").toAbsolutePath()
                    ),
                    "/docker-entrypoint-initdb.d/002-care-rules.sql"
            );

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("app.records.crypto.master-key",
                () -> "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        registry.add("app.notifications.crypto.master-key",
                () -> "OTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTBmZWRjYmE=");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CareRuleCatalogPort careRuleCatalogPort;

    @Autowired
    private CareRuleEngine careRuleEngine;

    @Autowired
    private RecommendedIngredientPlanner ingredientPlanner;

    @Test
    void seedInsertsTwentySixRulesAcrossNineCategories() {
        assertThat(count("SELECT COUNT(*) FROM care_rules")).isEqualTo(26);
        assertThat(count("SELECT COUNT(DISTINCT category) FROM care_rules")).isEqualTo(9);
        assertThat(count("SELECT COUNT(*) FROM care_rule_versions WHERE review_status = 'APPROVED'"))
                .isEqualTo(26);
        assertThat(count("SELECT COUNT(*) FROM care_ingredients WHERE active = TRUE")).isEqualTo(8);
        assertThat(count("SELECT COUNT(*) FROM rule_version_ingredients")).isEqualTo(8);
    }

    /** 활성 세트는 하나여야 한다. 둘이 되면 어느 규칙으로 안내했는지 되짚을 수 없다. */
    @Test
    void exactlyOneRuleSetIsActive() {
        assertThat(count("SELECT COUNT(*) FROM rule_sets WHERE status = 'ACTIVE'")).isEqualTo(1);
        assertThat(careRuleCatalogPort.loadActiveCatalog()).isPresent();
        // 결과의 ruleVersion 자리에 그대로 나가는 값이다. 규칙 세트 버전이지 개별 규칙 버전이 아니다.
        assertThat(careRuleCatalogPort.loadActiveCatalog().orElseThrow().versionCode())
                .isEqualTo("v0.3-mvp");
    }

    /** 검토가 끝나지 않았다는 사실이 데이터에 남아 있어야 한다. */
    @Test
    void evidenceIsRecordedButNotYetReviewed() {
        assertThat(count("SELECT COUNT(*) FROM rule_evidence_sources")).isGreaterThanOrEqualTo(26);
        assertThat(count("SELECT COUNT(*) FROM rule_evidence_sources WHERE reviewed_at IS NOT NULL"))
                .isZero();
    }

    @Test
    void shavingWithRednessMatchesSituationAppearanceStateCommonAndIngredientRules() {
        List<CareRuleSnapshot> matched = match(facts(
                Set.of(Appearance.APP_REDNESS),
                Set.of(Situation.SHAVING),
                CareAvailability.ALREADY_WASHED,
                Set.of(PreCareCheck.NONE),
                Set.of()
        ));

        assertThat(matched).extracting(CareRuleSnapshot::ruleCode)
                .containsExactly("ST-002", "SIT-001", "APP-REDNESS", "CR-001", "ING-001", "FALLBACK-001");
    }

    /** 겉모습 규칙은 상태값이라 행동 문구를 갖지 않는다. */
    @Test
    void appearanceRulesCarryNoActions() {
        List<CareRuleSnapshot> appearanceRules = catalogRules().stream()
                .filter(rule -> rule.ruleCode().startsWith("APP-"))
                .toList();

        assertThat(appearanceRules).hasSize(6);
        assertThat(appearanceRules).allSatisfy(rule -> assertThat(rule.actions()).isEmpty());
    }

    /** 성분 규칙도 행동 문구를 갖지 않는다. 성분은 별도 흐름으로 나간다. */
    @Test
    void ingredientRulesCarryIngredientsButNoActions() {
        List<CareRuleSnapshot> ingredientRules = catalogRules().stream()
                .filter(rule -> rule.ruleCode().startsWith("ING-"))
                .toList();

        assertThat(ingredientRules).hasSize(4);
        assertThat(ingredientRules).allSatisfy(rule -> {
            assertThat(rule.actions()).isEmpty();
            assertThat(rule.ingredients()).hasSize(2);
        });
    }

    @Test
    void shavingWithRednessYieldsPanthenolAndAllantoin() {
        List<PlannedIngredient> planned = ingredientPlanner.plan(match(facts(
                Set.of(Appearance.APP_REDNESS),
                Set.of(Situation.SHAVING),
                CareAvailability.ALREADY_WASHED,
                Set.of(PreCareCheck.NONE),
                Set.of()
        )));

        assertThat(planned).extracting(PlannedIngredient::code)
                .containsExactly("ING_PANTHENOL", "ING_ALLANTOIN");
        assertThat(planned).extracting(PlannedIngredient::sourceRuleCodes)
                .allSatisfy(sourceRuleCodes -> assertThat(sourceRuleCodes).containsExactly("ING-001"));
    }

    /**
     * 새 제품을 쓴 뒤에는 성분을 내보내지 않는다.
     *
     * <p>문서의 ING 공통 호출 조건이다. 새 제품을 멈추라고 안내하면서 성분을 함께 권하면 서로
     * 어긋난다. 규칙 조건의 NOT_CONTAINS_ANY로 표현했다.
     */
    @Test
    void newProductSituationYieldsNoIngredients() {
        List<CareRuleSnapshot> matched = match(facts(
                Set.of(Appearance.APP_DRYNESS),
                Set.of(Situation.NEW_PRODUCT, Situation.SHAVING),
                CareAvailability.ALREADY_WASHED,
                Set.of(PreCareCheck.NONE),
                Set.of()
        ));

        assertThat(matched).extracting(CareRuleSnapshot::ruleCode).contains("SIT-004");
        assertThat(ingredientPlanner.plan(matched)).isEmpty();
    }

    /** 겉모습이 기타 하나뿐이면 어느 성분 규칙도 걸리지 않는다. */
    @Test
    void appearanceOtherAloneYieldsNoIngredients() {
        List<CareRuleSnapshot> matched = match(facts(
                Set.of(Appearance.APP_OTHER),
                Set.of(Situation.SHAVING),
                CareAvailability.ALREADY_WASHED,
                Set.of(PreCareCheck.NONE),
                Set.of()
        ));

        assertThat(matched).extracting(CareRuleSnapshot::ruleCode).contains("APP-OTHER");
        assertThat(ingredientPlanner.plan(matched)).isEmpty();
    }

    /** 성분은 두 개까지만 나간다. 여러 성분 규칙이 함께 걸려도 마찬가지다. */
    @Test
    void ingredientsAreCappedAtTwoEvenWhenSeveralIngredientRulesMatch() {
        List<CareRuleSnapshot> matched = match(facts(
                Set.of(Appearance.APP_REDNESS, Appearance.APP_DRYNESS, Appearance.APP_OILINESS),
                Set.of(Situation.SHAVING, Situation.PROTECTIVE_GEAR_OR_MASK),
                CareAvailability.ALREADY_WASHED,
                Set.of(PreCareCheck.NONE),
                Set.of()
        ));

        assertThat(matched).extracting(CareRuleSnapshot::ruleCode)
                .contains("ING-001", "ING-003", "ING-004");
        assertThat(ingredientPlanner.plan(matched)).hasSize(2);
    }

    /** 위험 신호를 고르면 안전 규칙이 가장 앞에 오고 승인된 의료진 안내 문구를 갖는다. */
    @Test
    void safetySignalPutsSafetyRuleFirstWithClinicianMessage() {
        List<CareRuleSnapshot> matched = match(facts(
                Set.of(Appearance.APP_PUS_BUMP),
                Set.of(Situation.SQUEEZED_ACNE),
                CareAvailability.ALREADY_WASHED,
                Set.of(PreCareCheck.PUS_OOZING_BLISTER),
                Set.of()
        ));

        assertThat(matched.getFirst().ruleCode()).isEqualTo("SAF-001");
        assertThat(matched.getFirst().actions())
                .anySatisfy(action -> assertThat(action.type()).isEqualTo(RuleActionType.CLINICIAN_MESSAGE));
    }

    /** 위험 신호가 없다고 답하면 안전 규칙은 걸리지 않는다. */
    @Test
    void noSafetySignalSkipsTheSafetyRule() {
        assertThat(match(facts(
                Set.of(Appearance.APP_REDNESS),
                Set.of(Situation.SHAVING),
                CareAvailability.ALREADY_WASHED,
                Set.of(PreCareCheck.NONE),
                Set.of()
        ))).extracting(CareRuleSnapshot::ruleCode).doesNotContain("SAF-001");
    }

    /** 예상 환경이 비어 있으면 환경 규칙은 걸리지 않는다. 문서가 선택값으로 정한 동작이다. */
    @Test
    void emptyEnvironmentSkipsEnvironmentRules() {
        assertThat(match(facts(
                Set.of(Appearance.APP_REDNESS),
                Set.of(Situation.SHAVING),
                CareAvailability.ALREADY_WASHED,
                Set.of(PreCareCheck.NONE),
                Set.of()
        ))).extracting(CareRuleSnapshot::ruleCode).noneMatch(code -> code.startsWith("ENV-"));
    }

    /** 예상 환경이 들어오면 해당 환경 규칙만 걸린다. 조건 필드가 실제로 동작하는지 본다. */
    @Test
    void outdoorTrainingEnvironmentMatchesOnlyThatEnvironmentRule() {
        assertThat(match(facts(
                Set.of(Appearance.APP_OILINESS),
                Set.of(Situation.SWEAT_OR_SEBUM),
                CareAvailability.ADDITIONAL_CARE_DIFFICULT,
                Set.of(PreCareCheck.NONE),
                Set.of(ExpectedEnvironment.OUTDOOR_TRAINING)
        ))).extracting(CareRuleSnapshot::ruleCode)
                .contains("ENV-001")
                .doesNotContain("ENV-002", "ENV-003");
    }

    /** 세안 상태 네 값이 빠짐없이 현재 상태 규칙 하나에 걸려야 한다. */
    @Test
    void everyCareAvailabilityValueMatchesExactlyOneStateRule() {
        for (CareAvailability careAvailability : CareAvailability.values()) {
            List<String> stateRules = match(facts(
                    Set.of(Appearance.APP_REDNESS),
                    Set.of(Situation.SHAVING),
                    careAvailability,
                    Set.of(PreCareCheck.NONE),
                    Set.of()
            )).stream().map(CareRuleSnapshot::ruleCode).filter(code -> code.startsWith("ST-")).toList();

            assertThat(stateRules).as("careAvailability=%s", careAvailability).hasSize(1);
        }
    }

    /** 상황 여섯 값이 빠짐없이 상황 규칙 하나에 걸려야 한다. 폴백으로 새는 입력이 없어야 한다. */
    @Test
    void everySituationValueMatchesExactlyOneSituationRule() {
        for (Situation situation : Situation.values()) {
            List<String> situationRules = match(facts(
                    Set.of(Appearance.APP_REDNESS),
                    Set.of(situation),
                    CareAvailability.ALREADY_WASHED,
                    Set.of(PreCareCheck.NONE),
                    Set.of()
            )).stream().map(CareRuleSnapshot::ruleCode).filter(code -> code.startsWith("SIT-")).toList();

            assertThat(situationRules).as("situation=%s", situation).hasSize(1);
        }
    }

    /** 겉모습 여섯 값이 빠짐없이 겉모습 규칙 하나에 걸려야 한다. */
    @Test
    void everyAppearanceValueMatchesExactlyOneAppearanceRule() {
        for (Appearance appearance : Appearance.values()) {
            List<String> appearanceRules = match(facts(
                    Set.of(appearance),
                    Set.of(Situation.SHAVING),
                    CareAvailability.ALREADY_WASHED,
                    Set.of(PreCareCheck.NONE),
                    Set.of()
            )).stream().map(CareRuleSnapshot::ruleCode).filter(code -> code.startsWith("APP-")).toList();

            assertThat(appearanceRules).as("appearance=%s", appearance).hasSize(1);
        }
    }

    /**
     * 규칙 문구가 결과 컬럼에 들어갈 수 있는 길이여야 한다.
     *
     * <p>규칙 쪽 원본은 TEXT지만 결과 스냅샷은 VARCHAR다. 명세는 행동 문구를 300자로 제한하고
     * 요약은 500자다. 문구를 길게 고쳐 두면 저장 단계에서 503이 나므로 시드에서 미리 막는다.
     */
    @Test
    void seededTextsFitTheResultColumns() {
        assertThat(count("SELECT COUNT(*) FROM rule_actions"
                + " WHERE action_type <> 'CLINICIAN_MESSAGE' AND CHAR_LENGTH(content) > 300")).isZero();
        assertThat(count("SELECT COUNT(*) FROM rule_actions"
                + " WHERE action_type = 'CLINICIAN_MESSAGE' AND CHAR_LENGTH(content) > 1000")).isZero();
        assertThat(count("SELECT COUNT(*) FROM care_rule_versions"
                + " WHERE fallback_text IS NOT NULL AND CHAR_LENGTH(fallback_text) > 500")).isZero();
    }

    /**
     * 금지 표현 항목에 쉼표가 없어야 한다.
     *
     * <p>애플리케이션이 forbidden_expressions를 개행과 쉼표로 쪼갠다. 항목 안에 쉼표를 쓰면 한
     * 항목이 둘로 갈라져 뜻이 끊긴 금지 표현이 AI 프롬프트에 들어간다.
     */
    @Test
    void forbiddenExpressionsDoNotContainCommas() {
        assertThat(count("SELECT COUNT(*) FROM care_rule_versions"
                + " WHERE forbidden_expressions LIKE '%,%'")).isZero();
    }

    /** 유형별 행동 문구가 두 개를 넘지 않아야 한다. 결과 항목의 display_order가 1과 2만 허용한다. */
    @Test
    void noRuleHasMoreThanTwoActionsPerType() {
        assertThat(count("SELECT COUNT(*) FROM ("
                + " SELECT rule_version_id FROM rule_actions"
                + " GROUP BY rule_version_id, action_type HAVING COUNT(*) > 2) AS over_limit"))
                .isZero();
    }

    private List<CareRuleSnapshot> catalogRules() {
        return careRuleCatalogPort.loadActiveCatalog().orElseThrow().rules();
    }

    private List<CareRuleSnapshot> match(RuleEvaluationFacts facts) {
        return careRuleEngine.match(careRuleCatalogPort.loadActiveCatalog().orElseThrow(), facts);
    }

    private RuleEvaluationFacts facts(
            Set<Appearance> appearances,
            Set<Situation> situations,
            CareAvailability careAvailability,
            Set<PreCareCheck> preCareChecks,
            Set<ExpectedEnvironment> environments
    ) {
        return new RuleEvaluationFacts(
                BodyArea.RIGHT_CHIN,
                appearances,
                Set.of(Sensation.REDNESS),
                situations,
                careAvailability,
                preCareChecks,
                Set.of(),
                environments
        );
    }

    private int count(String sql) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count == null ? 0 : count;
    }
}
