package likelion.flourishing.domain.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 추천 성분 하나를 내놓은 규칙 코드. 명세 RecommendedIngredient.sourceRuleIds 다.
 *
 * <p>명세는 이 값이 같은 결과의 matchedRuleIds 의 부분집합이어야 한다고 정한다. DB로는 강제할 수
 * 없어 서비스 계층에서 걸러 낸다.
 */
@Entity
@Getter
@Table(name = "care_result_ingredient_rules")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CareResultIngredientRule {

    @EmbeddedId
    private Id id;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    private CareResultIngredientRule(Id id, int displayOrder) {
        this.id = id;
        this.displayOrder = displayOrder;
    }

    public static CareResultIngredientRule of(UUID careResultIngredientId, String ruleCode, int displayOrder) {
        return new CareResultIngredientRule(new Id(careResultIngredientId, ruleCode), displayOrder);
    }

    public UUID careResultIngredientId() {
        return id.getCareResultIngredientId();
    }

    public String ruleCode() {
        return id.getRuleCode();
    }

    @Embeddable
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Id implements Serializable {

        @Column(name = "care_result_ingredient_id", nullable = false, updatable = false)
        private UUID careResultIngredientId;

        @Column(name = "rule_code", nullable = false, updatable = false, length = 100)
        private String ruleCode;

        private Id(UUID careResultIngredientId, String ruleCode) {
            this.careResultIngredientId = careResultIngredientId;
            this.ruleCode = ruleCode;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Id that)) {
                return false;
            }
            return Objects.equals(careResultIngredientId, that.careResultIngredientId)
                    && Objects.equals(ruleCode, that.ruleCode);
        }

        @Override
        public int hashCode() {
            return Objects.hash(careResultIngredientId, ruleCode);
        }
    }
}
