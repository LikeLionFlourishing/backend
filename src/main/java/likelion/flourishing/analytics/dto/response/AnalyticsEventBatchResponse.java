package likelion.flourishing.analytics.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 멱등 재전송을 포함해 서버가 접수한 이벤트 개수를 반환한다. */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AnalyticsEventBatchResponse {

    private final int acceptedCount;

    public static AnalyticsEventBatchResponse of(int acceptedCount) {
        return new AnalyticsEventBatchResponse(acceptedCount);
    }
}
