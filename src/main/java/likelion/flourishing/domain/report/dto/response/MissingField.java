package likelion.flourishing.domain.report.dto.response;

/**
 * 사용자 직접 선택과 AI 추출을 합친 뒤에도 비어 있는 항목.
 *
 * <p>프런트가 "AI가 채우지 못한 항목을 되묻는" 화면을 그리는 근거다. 그래서 값이 비어 있다는
 * 사실만 알리고 이유는 담지 않는다.
 *
 * <p>otherAreasNote 는 명세의 missingFields enum 에 없으므로 대상이 아니다. 사용자가 직접 쓰는
 * 자유 문장이라 되물을 항목이 아니기 때문이다.
 */
public enum MissingField {
    PRIMARY_AREA,
    APPEARANCES,
    SENSATIONS,
    SITUATIONS,
    CARE_AVAILABILITY
}
