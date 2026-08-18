package likelion.flourishing.domain.report.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 한 문장 구조화 요청.
 *
 * <p>rawText는 사용자가 자기 말로 적은 한 문장이다. 이 값은 로그에 남기지 않고, 이 요청으로는
 * 저장하지도 않는다. 보고로 남는 것은 사용자가 확인 화면을 거친 뒤 보내는 별도 요청이다.
 */
public record ReportInterpretationRequest(
        @NotBlank @Size(max = 500) String rawText,
        @Valid ManualSelectionsRequest manualSelections
) {

    public ManualSelectionsRequest manualSelectionsOrEmpty() {
        return manualSelections == null ? ManualSelectionsRequest.empty() : manualSelections;
    }
}
