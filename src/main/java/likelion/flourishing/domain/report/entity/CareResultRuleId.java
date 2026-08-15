package likelion.flourishing.domain.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CareResultRuleId implements Serializable {

    @Column(name = "care_result_id", nullable = false)
    private UUID careResultId;

    @Column(name = "rule_version_id", nullable = false)
    private UUID ruleVersionId;

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof CareResultRuleId that)) {
            return false;
        }
        return Objects.equals(careResultId, that.careResultId)
                && Objects.equals(ruleVersionId, that.ruleVersionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(careResultId, ruleVersionId);
    }
}
