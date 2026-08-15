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
 * 다만 아직 입력할 수 없는 건은 홈의 priority가 FOLLOW_UP으로 고르지 않는다. 안내 카드일 뿐
 * 지금 할 일은 아니기 때문이다.
 *
 * <p>두 시각은 DB에 UTC로 저장되어 있다고 보고 오프셋을 붙인다. skin_reports를 쓰는 주체는
 * Reports 기능이고, 이 저장소는 읽기만 하므로 그 전제가 어긋나면 여기서 9시간이 밀린다.
 * report_date만 Asia/Seoul 기준 날짜이고 두 시각은 UTC라는 것이 이 응답이 기대하는 계약이다.
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
