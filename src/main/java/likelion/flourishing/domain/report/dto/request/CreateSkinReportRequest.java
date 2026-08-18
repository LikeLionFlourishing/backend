package likelion.flourishing.domain.report.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import likelion.flourishing.domain.report.entity.PreCareCheck;

/**
 * 최종 보고 생성 요청.
 *
 * <p>결과 유형과 보고 날짜는 요청에 없다. 결과 유형은 관리 전 확인값으로 서버가 정하고, 날짜는
 * 서버가 Asia/Seoul 기준 오늘로 정한다. 둘 다 사용자가 보내면 위험 신호가 있는데도 일반 관리로
 * 저장하거나 지난 날짜에 보고를 끼워 넣을 수 있다.
 */
public record CreateSkinReportRequest(
        @NotBlank @Size(max = 500) String rawText,
        @NotNull @Valid ConfirmedSelectionsRequest confirmed,
        @NotEmpty List<PreCareCheck> preCareChecks
) {

    public Set<PreCareCheck> preCareCheckSet() {
        if (preCareChecks == null) {
            return Set.of();
        }
        LinkedHashSet<PreCareCheck> unique = new LinkedHashSet<>();
        preCareChecks.stream().filter(Objects::nonNull).forEach(unique::add);
        return unique;
    }
}
