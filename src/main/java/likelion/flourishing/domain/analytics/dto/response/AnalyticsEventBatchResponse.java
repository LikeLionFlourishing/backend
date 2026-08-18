package likelion.flourishing.domain.analytics.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 서버가 접수한 이벤트 개수. 필드 이름은 명세 202 응답의 accepted를 그대로 따른다.
 *
 * <p>같은 묶음을 재전송해도 값이 달라지지 않는다. 저장된 행 수가 아니라 접수한 이벤트 수라서다.
 * 명세가 minimum 1을 요구하므로 영향 행 수를 세면 재전송에서 0이 나와 계약을 어긴다.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AnalyticsEventBatchResponse {

    private final int accepted;

    public static AnalyticsEventBatchResponse of(int accepted) {
        return new AnalyticsEventBatchResponse(accepted);
    }
}
