package likelion.flourishing.domain.report.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import likelion.flourishing.domain.report.entity.PreCareCheck;

/**
 * 최종 보고 생성 요청.
 *
 * <p>결과 유형은 요청에 없다. 관리 전 확인값으로 서버가 정한다. 사용자가 보내면 위험 신호가
 * 있는데도 일반 관리로 저장할 수 있다.
 *
 * <p>reportDate는 클라이언트가 보내지만 서버가 Asia/Seoul 기준 오늘과 일치하는지 확인한다.
 * 받아 두는 이유는 클라이언트가 어느 날짜로 저장할 셈이었는지 드러내기 위해서다. 서버가 조용히
 * 자기 날짜로 덮으면, 자정 근처에서 사용자가 본 화면과 저장된 날짜가 어긋나도 아무도 모른다.
 * 어긋나면 422로 돌려보내 클라이언트가 화면을 다시 그리게 한다.
 */
public record CreateSkinReportRequest(
        @NotNull LocalDate reportDate,
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
