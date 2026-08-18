package likelion.flourishing.domain.report.ai;

import java.util.LinkedHashSet;
import java.util.Set;
import likelion.flourishing.domain.report.entity.Appearance;
import likelion.flourishing.domain.report.entity.BodyArea;
import likelion.flourishing.domain.report.entity.CareAvailability;
import likelion.flourishing.domain.report.entity.Sensation;
import likelion.flourishing.domain.report.entity.Situation;

/**
 * AI가 원문에서 읽어 낸 선택값. 확정값이 아니라 사용자에게 보여 줄 후보다.
 *
 * <p>원문에 근거가 없으면 단일 값 필드는 null, 다중 선택 필드는 빈 집합이 된다. 근거 없는 값을
 * 채워 넣으면 사용자가 확인 화면에서 잘못된 값을 그대로 넘기게 된다.
 *
 * <p>부위 보충 설명(otherAreasNote)과 관리 전 확인(preCareChecks)은 여기에 없다. 앞은 사용자가
 * 직접 쓰는 자유 문장이고, 뒤는 긴급도 판단에 쓰여 사람이 확인해야 하는 값이다.
 */
public record ExtractedSelections(
        BodyArea primaryArea,
        Set<Appearance> appearances,
        Set<Sensation> sensations,
        Set<Situation> situations,
        CareAvailability careAvailability
) {

    public ExtractedSelections {
        appearances = copyOf(appearances);
        sensations = copyOf(sensations);
        situations = copyOf(situations);
    }

    public static ExtractedSelections empty() {
        return new ExtractedSelections(null, Set.of(), Set.of(), Set.of(), null);
    }

    private static <E> Set<E> copyOf(Set<E> values) {
        return values == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(values));
    }
}
