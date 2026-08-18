package likelion.flourishing.domain.report.entity;

/**
 * 결과 카드 가이드 섹션의 종류. 명세 GuideSection.key 와 값이 같다.
 *
 * <p>각 섹션은 결과의 어느 본문 필드를 가리키는지가 명세 표로 고정돼 있다. 본문이 비면 섹션은
 * 빈 상태로 표시되며 그 판단이 {@code empty} 값이다. 노출 순서는 여기 선언 순서를 따른다.
 *
 * <p>제목과 설명은 이 enum이 아니라 guide_sections 테이블에 있다. 문구를 배포 없이 다듬을 수
 * 있어야 하고, 명세도 관리 규칙표에서 관리한다고 적고 있다.
 */
public enum GuideSectionKey {

    /** 대응 본문: summary */
    CURRENT_SUMMARY,

    /** 대응 본문: doToday */
    DO_TODAY,

    /** 대응 본문: avoidToday */
    AVOID_TODAY,

    /** 대응 본문: similarExperience */
    SIMILAR_EXPERIENCE,

    /** 대응 본문: checkNext */
    CHECK_NEXT,

    /** 대응 본문: recommendedIngredients */
    RECOMMENDED_INGREDIENTS;

    /** 명세 CareResult.guideSections.maxItems 이자 이 enum의 값 개수. */
    public static final int SECTION_COUNT = 6;
}
