package likelion.flourishing.domain.report.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 관리 규칙표에서 고른 추천 성분 하나. 명세 RecommendedIngredient 와 필드가 같다.
 *
 * <p>AI가 성분을 만들어 내지 않는다. 여기 담기는 값은 전부 걸린 규칙이 가리키는 성분 행에서
 * 온다. 특정 제품·브랜드·의약품은 담지 않는다.
 *
 * <p>{@code cautionNote}는 명세가 nullable로 둔 필드라 값이 없으면 null로 내보낸다.
 * NON_NULL을 붙이지 않는 이유다.
 */
@Getter
@JsonInclude(JsonInclude.Include.ALWAYS)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RecommendedIngredientResponse {

    private final String id;

    private final String name;

    private final String description;

    private final String cautionNote;

    /** 이 성분을 내놓은 규칙 코드. 같은 결과의 matchedRuleIds 의 부분집합이다. */
    private final List<String> sourceRuleIds;

    public static RecommendedIngredientResponse of(
            String id,
            String name,
            String description,
            String cautionNote,
            List<String> sourceRuleIds
    ) {
        return new RecommendedIngredientResponse(id, name, description, cautionNote, List.copyOf(sourceRuleIds));
    }
}
