package likelion.flourishing.domain.report.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import likelion.flourishing.domain.report.entity.Appearance;
import likelion.flourishing.domain.report.entity.BodyArea;
import likelion.flourishing.domain.report.entity.CareAvailability;
import likelion.flourishing.domain.report.entity.Sensation;
import likelion.flourishing.domain.report.entity.Situation;

/**
 * 사용자가 확인 화면에서 최종 확정한 선택값.
 *
 * <p>모두 필수다. 다중 선택도 비워 둘 수 없는데, "모름"과 "없음"이 각각 값으로 있어서 답을
 * 고르지 않을 이유가 없기 때문이다. 빈 배열을 허용하면 답을 안 한 것과 없다고 답한 것을
 * 구분할 수 없게 된다.
 */
public record ConfirmedSelectionsRequest(
        @NotNull BodyArea primaryArea,
        @Size(max = 200) String otherAreasNote,
        @NotEmpty List<Appearance> appearances,
        @NotEmpty List<Sensation> sensations,
        @NotEmpty List<Situation> situations,
        @NotNull CareAvailability careAvailability
) {

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
        values.stream().filter(Objects::nonNull).forEach(unique::add);
        return unique;
    }
}
