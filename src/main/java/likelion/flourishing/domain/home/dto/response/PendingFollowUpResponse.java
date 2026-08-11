package likelion.flourishing.domain.home.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import likelion.flourishing.domain.home.repository.HomeReportQueryRepository.PendingFollowUpRow;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 명세 PendingFollowUp 스키마. 아직 경과를 입력하지 않은 보고 한 건을 가리킨다.
 *
 * <p>availableFrom과 expiresAt을 함께 주는 이유는, 아직 입력 시점이 되지 않은 건도 홈에
 * 보여주되 프론트가 "언제부터 입력 가능한지"와 "언제까지인지"를 직접 판단하게 하기 위해서다.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PendingFollowUpResponse {

    private final UUID reportId;

    private final LocalDate reportDate;

    private final OffsetDateTime availableFrom;

    private final OffsetDateTime expiresAt;

    private final String resultType;

    public static PendingFollowUpResponse of(
            UUID reportId,
            LocalDate reportDate,
            OffsetDateTime availableFrom,
            OffsetDateTime expiresAt,
            String resultType
    ) {
        return new PendingFollowUpResponse(reportId, reportDate, availableFrom, expiresAt, resultType);
    }

    public static PendingFollowUpResponse from(PendingFollowUpRow row) {
        return new PendingFollowUpResponse(
                row.reportId(),
                row.reportDate(),
                row.availableFrom().atOffset(ZoneOffset.UTC),
                row.expiresAt().atOffset(ZoneOffset.UTC),
                row.resultType()
        );
    }
}
