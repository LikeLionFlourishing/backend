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

/** 결과 생성 이후 규칙이 바뀌어도 당시 사용자에게 보인 문구를 유지하는 항목 스냅샷. */
@Entity
@Getter
@Table(name = "care_result_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CareResultItem {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "care_result_id", nullable = false, updatable = false)
    private UUID careResultId;

    @Column(name = "source_rule_action_id")
    private UUID sourceRuleActionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 30)
    private CareResultItemType itemType;

    @Column(name = "content_snapshot", nullable = false, length = 500)
    private String contentSnapshot;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
