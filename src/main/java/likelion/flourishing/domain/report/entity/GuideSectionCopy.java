package likelion.flourishing.domain.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import likelion.flourishing.global.entity.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 가이드 섹션 하나의 제목과 설명. 관리 규칙표가 관리하는 문구다.
 *
 * <p>키가 그대로 기본키다. 섹션은 여섯 개로 고정이고 사용자별로 달라지지 않는다.
 *
 * <p>결과에 스냅샷하지 않는다. 항목 문구와 달리 섹션 제목은 편집상의 표현이라, 나중에 다듬은
 * 문구가 과거 결과에도 보이는 편이 자연스럽다. 사용자에게 안내한 내용 자체는 care_result_items 와
 * care_result_ingredients 가 붙잡는다.
 */
@Entity
@Getter
@Table(name = "guide_sections")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuideSectionCopy extends BaseTimeEntity {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "section_key", nullable = false, length = 30, updatable = false)
    private GuideSectionKey sectionKey;

    @Column(name = "title", nullable = false, length = 50)
    private String title;

    @Column(name = "description", nullable = false, length = 300)
    private String description;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    private GuideSectionCopy(GuideSectionKey sectionKey, String title, String description, int displayOrder) {
        this.sectionKey = sectionKey;
        this.title = title;
        this.description = description;
        this.displayOrder = displayOrder;
    }

    public static GuideSectionCopy of(
            GuideSectionKey sectionKey,
            String title,
            String description,
            int displayOrder
    ) {
        return new GuideSectionCopy(sectionKey, title, description, displayOrder);
    }
}
