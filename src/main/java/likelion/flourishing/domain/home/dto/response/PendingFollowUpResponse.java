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
 *
 * <p><b>availableFrom의 산출 근거는 보고 생성 시점의 notification_settings.notification_time이다.</b>
 * 명세 v2_1에서 "다음 날 17:30 고정"이 "온보딩에서 설정한 피부 점호 시각"으로 바뀌었다.
 * 이 값을 계산해 skin_reports.follow_up_available_at에 쓰는 것은 Reports 기능이고 홈은 읽기만
 * 하므로 이 클래스에는 바꿀 것이 없다. 홈 어디에도 17:30을 가정한 계산이나 테스트를 두지 않는다.
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
