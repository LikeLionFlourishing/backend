package likelion.flourishing.domain.home.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import likelion.flourishing.domain.home.entity.HomePriority;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 명세 Home 스키마. 홈 화면이 한 번의 호출로 필요한 것을 모두 받는다.
 *
 * <p>세 항목은 각각 없을 수 있고 명세가 null을 허용하므로 필드를 지우지 않고 내보낸다.
 * priority는 그중 무엇을 먼저 보여줄지에 대한 서버의 판단이다.
 *
 * <p>serverDate는 Asia/Seoul 기준 오늘이다. 기기 시계가 틀어져 있어도 프론트가 같은
 * "오늘"을 쓰도록 서버가 알려 준다.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.ALWAYS)
public class HomeResponse {

    private final LocalDate serverDate;

    private final HomePriority priority;

    private final PendingFollowUpResponse pendingFollowUp;

    private final DailyCheckInResponse today;

    private final SkinReportSummaryResponse recentReport;

    public static HomeResponse of(
            LocalDate serverDate,
            HomePriority priority,
            PendingFollowUpResponse pendingFollowUp,
            DailyCheckInResponse today,
            SkinReportSummaryResponse recentReport
    ) {
        return new HomeResponse(serverDate, priority, pendingFollowUp, today, recentReport);
    }
}
