package likelion.flourishing.domain.report.dto.request;

import jakarta.validation.constraints.Size;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import likelion.flourishing.domain.report.entity.Appearance;
import likelion.flourishing.domain.report.entity.BodyArea;
import likelion.flourishing.domain.report.entity.CareAvailability;
import likelion.flourishing.domain.report.entity.Sensation;
import likelion.flourishing.domain.report.entity.Situation;

/**
 * 구조화를 요청할 때 사용자가 이미 직접 고른 값.
 *
 * <p>모든 필드가 선택이다. 사용자가 문장만 쓰고 아무것도 고르지 않았을 수도 있고, 몇 개만 골라
 * 두고 나머지를 AI에 맡길 수도 있다. 채워진 값은 AI가 읽어 낸 값보다 앞선다.
 */
public record ManualSelectionsRequest(
        BodyArea primaryArea,
        @Size(max = 200) String otherAreasNote,
        List<Appearance> appearances,
        List<Sensation> sensations,
        List<Situation> situations,
        CareAvailability careAvailability
) {

    /** 본문에 manualSelections가 없을 때 쓰는 빈 값. null 검사를 서비스에 퍼뜨리지 않기 위해서다. */
    public static ManualSelectionsRequest empty() {
        return new ManualSelectionsRequest(null, null, null, null, null, null);
    }

    public Set<Appearance> appearanceSet() {
        return toSet(appearances);
    }

    public Set<Sensation> sensationSet() {
        return toSet(sensations);
    }

    public Set<Situation> situationSet() {
        return toSet(situations);
    }

    private <E> Set<E> toSet(List<E> values) {
        if (values == null) {
            return Set.of();
        }
        LinkedHashSet<E> unique = new LinkedHashSet<>();
        values.stream().filter(java.util.Objects::nonNull).forEach(unique::add);
        return unique;
    }
}
