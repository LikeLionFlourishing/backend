package likelion.flourishing.domain.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import likelion.flourishing.global.entity.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 관리 규칙표가 관리하는 성분 하나.
 *
 * <p>AI는 이 표에 없는 성분을 만들어 낼 수 없다. 결과에 담기는 성분은 전부 걸린 규칙이 가리키는
 * 행에서 나온다. 특정 제품·브랜드·의약품은 담지 않는다.
 *
 * <p>{@code ingredientCode}가 명세 RecommendedIngredient.id 다. UUID 기본키를 밖으로 내보내지
 * 않는 이유는, 클라이언트와 규칙 데이터가 주고받는 식별자가 사람이 읽을 수 있는 코드여야
 * 규칙표를 다루기 쉽기 때문이다.
 */
@Entity
@Getter
@Table(name = "care_ingredients")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CareIngredient extends BaseTimeEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "ingredient_code", nullable = false, updatable = false, length = 100)
    private String ingredientCode;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", nullable = false, length = 300)
    private String description;

    @Column(name = "caution_note", length = 300)
    private String cautionNote;

    @Column(name = "active", nullable = false)
    private boolean active;
}
