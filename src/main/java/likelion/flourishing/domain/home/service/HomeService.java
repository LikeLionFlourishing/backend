package likelion.flourishing.domain.home.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.home.dto.request.SaveDailyCheckInRequest;
import likelion.flourishing.domain.home.dto.response.DailyCheckInResponse;
import likelion.flourishing.domain.home.dto.response.HomeResponse;
import likelion.flourishing.domain.home.dto.response.PendingFollowUpResponse;
import likelion.flourishing.domain.home.dto.response.SkinReportSummaryResponse;
import likelion.flourishing.domain.home.entity.DailyCheckIn;
import likelion.flourishing.domain.home.entity.HomePriority;
import likelion.flourishing.domain.home.repository.DailyCheckInRepository;
import likelion.flourishing.domain.home.repository.HomeReportQueryRepository;
import likelion.flourishing.domain.home.repository.HomeReportQueryRepository.RecentReportRow;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 홈 화면 집계 조회와 "오늘 불편 없음" 저장. */
@Service
public class HomeService {

    /**
     * 하루 경계는 Asia/Seoul로 판단한다. 저장하는 시각은 UTC지만 "오늘"은 사용자가 사는
     * 시간대 기준이어야 한다. UTC로 날짜를 끊으면 한국 시간 오전 9시 이전이 전날로 잡힌다.
     */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final DailyCheckInRepository dailyCheckInRepository;
    private final HomeReportQueryRepository homeReportQueryRepository;
    private final Clock clock;

    public HomeService(
            DailyCheckInRepository dailyCheckInRepository,
            HomeReportQueryRepository homeReportQueryRepository,
            Clock clock
    ) {
        this.dailyCheckInRepository = dailyCheckInRepository;
        this.homeReportQueryRepository = homeReportQueryRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public HomeResponse getHome(AuthenticatedUser principal) {
        UUID userId = principal.userId();
        LocalDate serverDate = today();

        PendingFollowUpResponse pendingFollowUp = homeReportQueryRepository
                .findOldestPendingFollowUp(userId, LocalDateTime.now(clock))
                .map(PendingFollowUpResponse::from)
                .orElse(null);

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
                resolvePriority(pendingFollowUp, todayCheckIn, recentReport),
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
     */
    @Transactional
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
        Optional<DailyCheckIn> existing = dailyCheckInRepository.findByUserIdAndCheckInDate(userId, date);
        if (existing.isPresent()) {
            DailyCheckIn checkIn = existing.get();
            // 같은 날 피부 보고가 확정되면 서버가 상태를 SKIN_REPORT로 바꾼다. 되돌릴 수 없다.
            if (!checkIn.isNoDiscomfort()) {
                throw new BusinessException(ErrorCode.CHECK_IN_ALREADY_REPORTED);
            }
            return new SavedDailyCheckIn(DailyCheckInResponse.from(checkIn), false);
        }

        DailyCheckIn saved = dailyCheckInRepository.saveAndFlush(DailyCheckIn.noDiscomfort(userId, date));
        return new SavedDailyCheckIn(DailyCheckInResponse.from(saved), true);
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(SERVICE_ZONE));
    }

    private HomePriority resolvePriority(
            PendingFollowUpResponse pendingFollowUp,
            DailyCheckInResponse todayCheckIn,
            SkinReportSummaryResponse recentReport
    ) {
        if (pendingFollowUp != null) {
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
