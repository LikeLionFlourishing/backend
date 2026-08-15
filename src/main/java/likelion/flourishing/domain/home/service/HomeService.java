package likelion.flourishing.domain.home.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.home.dto.request.SaveDailyCheckInRequest;
import likelion.flourishing.domain.home.dto.response.DailyCheckInResponse;
import likelion.flourishing.domain.home.dto.response.HomeResponse;
import likelion.flourishing.domain.home.dto.response.PendingFollowUpResponse;
import likelion.flourishing.domain.home.dto.response.SkinReportSummaryResponse;
import likelion.flourishing.domain.home.entity.HomePriority;
import likelion.flourishing.domain.home.repository.DailyCheckInRepository;
import likelion.flourishing.domain.home.repository.HomeReportQueryRepository;
import likelion.flourishing.domain.home.repository.HomeReportQueryRepository.PendingFollowUpRow;
import likelion.flourishing.domain.home.repository.HomeReportQueryRepository.RecentReportRow;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 홈 화면 집계 조회와 "오늘 불편 없음" 저장. */
@Service
public class HomeService {

    private static final Logger log = LoggerFactory.getLogger(HomeService.class);

    /**
     * 하루 경계는 Asia/Seoul로 판단한다. 저장하는 시각은 UTC지만 "오늘"은 사용자가 사는
     * 시간대 기준이어야 한다. UTC로 날짜를 끊으면 한국 시간 오전 9시 이전이 전날로 잡힌다.
     */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final DailyCheckInRepository dailyCheckInRepository;
    private final HomeReportQueryRepository homeReportQueryRepository;
    private final DailyCheckInWriter dailyCheckInWriter;
    private final Clock clock;

    public HomeService(
            DailyCheckInRepository dailyCheckInRepository,
            HomeReportQueryRepository homeReportQueryRepository,
            DailyCheckInWriter dailyCheckInWriter,
            Clock clock
    ) {
        this.dailyCheckInRepository = dailyCheckInRepository;
        this.homeReportQueryRepository = homeReportQueryRepository;
        this.dailyCheckInWriter = dailyCheckInWriter;
        this.clock = clock;
    }

    /**
     * 홈 화면에 필요한 것을 모아 돌려준다.
     *
     * <p>아직 입력할 수 없는 경과도 pendingFollowUp에는 담는다. 명세가 availableFrom을 필수로
     * 둔 이유가 "언제부터 쓸 수 있는지"를 프런트가 안내하게 하려는 것이라, 사전 안내 카드를
     * 없애지 않는다. 다만 priority는 지금 실제로 열 수 있는 것만 FOLLOW_UP으로 고른다.
     * 서버가 최우선이라고 한 항목을 눌렀는데 아직 입력할 수 없으면 그것이 곧 오동작이다.
     */
    @Transactional(readOnly = true)
    public HomeResponse getHome(AuthenticatedUser principal) {
        UUID userId = principal.userId();
        LocalDate serverDate = today();
        LocalDateTime now = LocalDateTime.now(clock);

        PendingFollowUpRow pendingRow = homeReportQueryRepository
                .findOldestPendingFollowUp(userId, now)
                .orElse(null);
        PendingFollowUpResponse pendingFollowUp = pendingRow == null
                ? null
                : PendingFollowUpResponse.from(pendingRow);
        boolean followUpOpen = pendingRow != null && !now.isBefore(pendingRow.availableFrom());

        DailyCheckInResponse todayCheckIn = dailyCheckInRepository
                .findByUserIdAndCheckInDate(userId, serverDate)
                .map(DailyCheckInResponse::from)
                .orElse(null);

        SkinReportSummaryResponse recentReport = homeReportQueryRepository
                .findMostRecentReport(userId)
                .map(this::toSummary)
                .orElse(null);

        return HomeResponse.of(
                serverDate,
                resolvePriority(followUpOpen, todayCheckIn, recentReport),
                pendingFollowUp,
                todayCheckIn,
                recentReport
        );
    }

    /**
     * "오늘 불편 없음"을 저장한다. 같은 값이 이미 있으면 그대로 돌려준다.
     *
     * <p>확인 순서가 중요하다. 날짜와 상태를 먼저 보고, 그다음에 이미 저장된 값을 본다.
     * 잘못된 요청이 DB 조회까지 가지 않게 하려는 것이다.
     *
     * <p>트랜잭션은 저장 단계에만 건다. 같은 사용자의 첫 요청이 겹치면 둘 다 저장된 것이 없다고
     * 보고 각자 넣으려다 뒤늦은 쪽이 uq_daily_check_ins_user_date에 걸리는데, 그 트랜잭션이
     * 되돌아간 뒤 다시 읽어야 먼저 저장된 값이 보인다.
     */
    public SavedDailyCheckIn saveNoDiscomfort(
            AuthenticatedUser principal,
            LocalDate date,
            SaveDailyCheckInRequest request
    ) {
        if (!request.isNoDiscomfort()) {
            throw new BusinessException(ErrorCode.CHECK_IN_STATE_NOT_ALLOWED);
        }
        if (!today().equals(date)) {
            throw new BusinessException(ErrorCode.CHECK_IN_DATE_NOT_TODAY);
        }

        UUID userId = principal.userId();
        try {
            return dailyCheckInWriter.saveNoDiscomfort(userId, date);
        } catch (DataIntegrityViolationException | ConcurrencyFailureException raced) {
            // 유니크 위반만 잡지 않는 이유는, 지는 쪽 INSERT가 이긴 쪽이 커밋할 때까지 대기하다가
            // 잠금 대기 시간을 넘기거나 서로 물릴 수 있기 때문이다. 둘 다 ConcurrencyFailureException
            // 계열이라 따로 받지 않으면 그대로 500이 된다.
            //
            // 재시도는 한 번뿐이다. 두 번째도 제약에 걸리면 원인이 경합이 아니라는 뜻이다.
            // 그 사이 같은 날 피부 보고가 확정됐다면 다시 읽은 값이 SKIN_REPORT라 409가 나간다.
            // 삼킨 예외를 남기지 않으면 경합 흡수와 실제 무결성 오류를 나중에 구분할 수 없다.
            log.warn("피부 점호 저장이 경합으로 실패해 한 번 더 시도합니다. type={}", raced.getClass().getSimpleName());
            return dailyCheckInWriter.saveNoDiscomfort(userId, date);
        }
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(SERVICE_ZONE));
    }

    /**
     * @param followUpOpen 미완료 경과가 있고 지금 입력할 수 있으면 참. 있기만 한 것으로는
     *                     최우선이 되지 않는다. 입력 시작 전 카드는 안내일 뿐 할 일이 아니다.
     */
    private HomePriority resolvePriority(
            boolean followUpOpen,
            DailyCheckInResponse todayCheckIn,
            SkinReportSummaryResponse recentReport
    ) {
        if (followUpOpen) {
            return HomePriority.FOLLOW_UP;
        }
        if (todayCheckIn != null) {
            return HomePriority.TODAY_CHECK_IN;
        }
        if (recentReport != null) {
            return HomePriority.RECENT_RECORD;
        }
        return HomePriority.EMPTY;
    }

    private SkinReportSummaryResponse toSummary(RecentReportRow row) {
        UUID reportId = row.id();
        return SkinReportSummaryResponse.from(
                row,
                homeReportQueryRepository.findAppearanceCodes(reportId),
                homeReportQueryRepository.findSensationCodes(reportId),
                homeReportQueryRepository.findSituationCodes(reportId)
        );
    }
}
