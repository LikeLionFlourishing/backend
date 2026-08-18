package likelion.flourishing.domain.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import likelion.flourishing.support.UuidV7;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 결과 생성 이후 성분 사전이 바뀌어도 당시 사용자에게 보인 내용을 유지하는 스냅샷.
 *
 * <p>{@link CareResultItem}이 문구를 붙잡는 것과 같은 이유다. 성분 설명은 안전 안내를 포함할 수
 * 있어 나중 판이 과거 결과에 소급되면 안 된다.
 */
@Entity
@Getter
@Table(name = "care_result_ingredients")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CareResultIngredient {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "care_result_id", nullable = false, updatable = false)
    private UUID careResultId;

    @Column(name = "source_ingredient_id")
    private UUID sourceIngredientId;

    @Column(name = "ingredient_code", nullable = false, updatable = false, length = 100)
    private String ingredientCode;

    @Column(name = "name_snapshot", nullable = false, updatable = false, length = 100)
    private String nameSnapshot;

    @Column(name = "description_snapshot", nullable = false, updatable = false, length = 300)
    private String descriptionSnapshot;

    @Column(name = "caution_note_snapshot", updatable = false, length = 300)
    private String cautionNoteSnapshot;

    @Column(name = "display_order", nullable = false, updatable = false)
    private int displayOrder;

    private CareResultIngredient(
            UUID careResultId,
            UUID sourceIngredientId,
            String ingredientCode,
            String nameSnapshot,
            String descriptionSnapshot,
            String cautionNoteSnapshot,
            int displayOrder
    ) {
        this.id = UuidV7.generate();
        this.careResultId = careResultId;
        this.sourceIngredientId = sourceIngredientId;
        this.ingredientCode = ingredientCode;
        this.nameSnapshot = nameSnapshot;
        this.descriptionSnapshot = descriptionSnapshot;
        this.cautionNoteSnapshot = cautionNoteSnapshot;
        this.displayOrder = displayOrder;
    }

    public static CareResultIngredient snapshot(
            UUID careResultId,
            UUID sourceIngredientId,
            String ingredientCode,
            String name,
            String description,
            String cautionNote,
            int displayOrder
    ) {
        return new CareResultIngredient(
                careResultId, sourceIngredientId, ingredientCode, name, description, cautionNote, displayOrder
        );
    }
}
