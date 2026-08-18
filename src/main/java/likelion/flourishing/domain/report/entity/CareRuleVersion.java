package likelion.flourishing.domain.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 한 번 승인하면 고치지 않는 개별 규칙 버전.
 *
 * <p>문구를 바꿔야 하면 같은 rule_id에 새 버전을 만든다. 과거 결과가 참조하는 버전이 그대로
 * 남아 있어야 당시 사용자에게 무엇을 안내했는지 다시 설명할 수 있다.
 *
 * <p>forbiddenExpressions와 fallbackText는 AI 설명 생성에 쓴다. 앞은 쓰면 안 되는 표현,
 * 뒤는 생성이 실패했을 때 대신 저장할 승인 문구다.
 */
@Entity
@Getter
@Table(name = "care_rule_versions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CareRuleVersion {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "rule_id", nullable = false, updatable = false)
    private UUID ruleId;

    @Column(name = "rule_set_id", nullable = false, updatable = false)
    private UUID ruleSetId;

    @Column(name = "version_code", nullable = false, length = 20)
    private String versionCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 30)
    private RuleReviewStatus reviewStatus;

    @Column(name = "application_summary", nullable = false)
    private String applicationSummary;

    @Column(name = "exclusion_summary")
    private String exclusionSummary;

    @Column(name = "forbidden_expressions")
    private String forbiddenExpressions;

    @Column(name = "fallback_text")
    private String fallbackText;

    @Column(name = "priority", nullable = false)
    private int priority;
}
