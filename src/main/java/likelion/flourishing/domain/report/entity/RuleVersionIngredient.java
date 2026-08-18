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

/** 규칙 버전이 권하는 성분. 한 규칙이 여러 성분을, 한 성분이 여러 규칙에 걸릴 수 있다. */
@Entity
@Getter
@Table(name = "rule_version_ingredients")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RuleVersionIngredient {

    @EmbeddedId
    private Id id;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public UUID ruleVersionId() {
        return id.getRuleVersionId();
    }

    public UUID ingredientId() {
        return id.getIngredientId();
    }

    @Embeddable
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Id implements Serializable {

        @Column(name = "rule_version_id", nullable = false, updatable = false)
        private UUID ruleVersionId;

        @Column(name = "ingredient_id", nullable = false, updatable = false)
        private UUID ingredientId;

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Id that)) {
                return false;
            }
            return Objects.equals(ruleVersionId, that.ruleVersionId)
                    && Objects.equals(ingredientId, that.ingredientId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ruleVersionId, ingredientId);
        }
    }
}
