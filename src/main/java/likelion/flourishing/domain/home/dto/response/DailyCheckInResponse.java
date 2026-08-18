package likelion.flourishing.domain.home.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import likelion.flourishing.domain.home.entity.CheckInState;
import likelion.flourishing.domain.home.entity.DailyCheckIn;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 명세 DailyCheckIn 스키마.
 *
 * <p>reportId는 명세가 nullable로 정의했고 상태가 NO_DISCOMFORT면 항상 null이다.
 * 필드를 지우지 않고 null로 내보내야 해서 여기서는 NON_NULL을 쓰지 않는다.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.ALWAYS)
public class DailyCheckInResponse {

    private final LocalDate date;

    private final CheckInState state;

    private final UUID reportId;

    private final OffsetDateTime updatedAt;

    public static DailyCheckInResponse of(
            LocalDate date,
            CheckInState state,
            UUID reportId,
            OffsetDateTime updatedAt
    ) {
        return new DailyCheckInResponse(date, state, reportId, updatedAt);
    }

    public static DailyCheckInResponse from(DailyCheckIn checkIn) {
        return new DailyCheckInResponse(
                checkIn.getCheckInDate(),
                checkIn.getState(),
                checkIn.getReportId(),
                checkIn.getUpdatedAt().atOffset(ZoneOffset.UTC)
        );
    }
}
