package likelion.flourishing.domain.report.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import likelion.flourishing.domain.report.entity.Appearance;
import likelion.flourishing.domain.report.entity.BodyArea;
import likelion.flourishing.domain.report.entity.CareAvailability;
import likelion.flourishing.domain.report.entity.Sensation;
import likelion.flourishing.domain.report.entity.Situation;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 구조화된 선택값 묶음. 구조화 응답의 structured와 보고 응답의 confirmed에 같은 모양으로 쓰인다.
 *
 * <p>기록 조회의 confirmed와도 필드가 같아 클라이언트가 한 타입으로 다룰 수 있다.
 */
@Getter
@JsonInclude(JsonInclude.Include.ALWAYS)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class StructuredSelectionsResponse {

    private final BodyArea primaryArea;
    private final String otherAreasNote;
    private final List<Appearance> appearances;
    private final List<Sensation> sensations;
    private final List<Situation> situations;
    private final CareAvailability careAvailability;

    public static StructuredSelectionsResponse of(
            BodyArea primaryArea,
            String otherAreasNote,
            List<Appearance> appearances,
            List<Sensation> sensations,
            List<Situation> situations,
            CareAvailability careAvailability
    ) {
        return new StructuredSelectionsResponse(
                primaryArea,
                otherAreasNote,
                appearances,
                sensations,
                situations,
                careAvailability
        );
    }
}
