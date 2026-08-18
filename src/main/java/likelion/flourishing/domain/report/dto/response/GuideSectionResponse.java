package likelion.flourishing.domain.report.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import likelion.flourishing.domain.report.entity.GuideSectionKey;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 결과 카드의 가이드 섹션 하나. 명세 GuideSection 과 필드가 같다.
 *
 * <p>프론트가 제목·설명을 하드코딩하지 않고 이 값을 그대로 그린다. 노출 순서는 배열 순서다.
 *
 * <p>{@code empty}는 대응하는 본문 필드가 비었다는 뜻이다. 섹션 자체는 그대로 두고 빈 상태로
 * 표시한다. 섹션이 사라지면 화면 구성이 결과마다 달라져 사용자가 어디를 봐야 할지 매번 다시
 * 찾아야 한다.
 */
@Getter
@JsonInclude(JsonInclude.Include.ALWAYS)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class GuideSectionResponse {

    private final GuideSectionKey key;

    private final String title;

    private final String description;

    private final boolean empty;

    public static GuideSectionResponse of(GuideSectionKey key, String title, String description, boolean empty) {
        return new GuideSectionResponse(key, title, description, empty);
    }
}
