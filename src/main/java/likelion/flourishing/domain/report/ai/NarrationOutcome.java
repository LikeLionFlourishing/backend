package likelion.flourishing.domain.report.ai;

import java.util.List;

/**
 * 관리 설명 생성 결과.
 *
 * <p>실패하면 호출한 쪽이 규칙의 승인된 fallbackText로 결과를 만들고 aiGenerationStatus를
 * FALLBACK으로 저장한다. 그래서 실패도 예외가 아니라 결과값이다.
 *
 * @param summary     사용자에게 보여 줄 요약 문장. 실패면 null이다.
 * @param failureCode 실패 사유. 성공이면 null이다.
 */
public record NarrationOutcome(
        String summary,
        List<String> doToday,
        List<String> avoidToday,
        List<String> checkNext,
        AiFailureCode failureCode
) {

    public NarrationOutcome {
        doToday = List.copyOf(doToday);
        avoidToday = List.copyOf(avoidToday);
        checkNext = List.copyOf(checkNext);
    }

    public static NarrationOutcome succeeded(
            String summary,
            List<String> doToday,
            List<String> avoidToday,
            List<String> checkNext
    ) {
        return new NarrationOutcome(summary, doToday, avoidToday, checkNext, null);
    }

    public static NarrationOutcome failed(AiFailureCode failureCode) {
        return new NarrationOutcome(null, List.of(), List.of(), List.of(), failureCode);
    }

    public boolean isSucceeded() {
        return failureCode == null;
    }
}
