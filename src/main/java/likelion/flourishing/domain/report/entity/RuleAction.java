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
 * 규칙이 허용하는 문구. 결과에 담는 행동은 반드시 이 목록에서만 나온다.
 *
 * <p>active가 false인 행동은 더 쓰지 않기로 한 문구다. 과거 결과의 스냅샷은 그대로 남기고
 * 새 결과에만 반영하지 않기 위해 행을 지우지 않고 끈다.
 */
@Entity
@Getter
@Table(name = "rule_actions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RuleAction {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "rule_version_id", nullable = false, updatable = false)
    private UUID ruleVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 30)
    private RuleActionType actionType;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "priority", nullable = false)
    private int priority;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "active", nullable = false)
    private boolean active;
}
