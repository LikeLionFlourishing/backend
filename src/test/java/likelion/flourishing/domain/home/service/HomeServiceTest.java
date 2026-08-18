package likelion.flourishing.domain.home.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.home.dto.request.SaveDailyCheckInRequest;
import likelion.flourishing.domain.home.dto.response.HomeResponse;
import likelion.flourishing.domain.home.entity.CheckInState;
import likelion.flourishing.domain.home.entity.DailyCheckIn;
import likelion.flourishing.domain.home.entity.HomePriority;
import likelion.flourishing.domain.home.repository.DailyCheckInRepository;
import likelion.flourishing.domain.home.repository.HomeReportQueryRepository;
import likelion.flourishing.domain.home.repository.HomeReportQueryRepository.PendingFollowUpRow;
import likelion.flourishing.domain.home.repository.HomeReportQueryRepository.RecentReportRow;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * HomeService의 집계 규칙과 저장 규칙 테스트. DB 없이 가짜 저장소와 고정 시계로 돌린다.
 *
 * <p>시계를 UTC 2026-08-11 22:00으로 고정한 것은 의도된 값이다. 이 시각은 Asia/Seoul로
 * 2026-08-12 07:00이라, 날짜를 UTC로 끊는 실수를 하면 "오늘"이 하루 어긋나 바로 드러난다.
 *
 * <p>확인하는 것: priority 네 값이 각각 언제 나오는지, 오늘 상태 저장이 새로 만들 때와
 * 이미 있을 때를 구분하는지, 오늘이 아닌 날짜와 SKIN_REPORT 상태를 막는지.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HomeServiceTest {

    private static final UUID USER_ID = UUID.fromString("2c56fe08-ea1f-45fc-915d-c35b7c0bca39");
    private static final UUID SESSION_ID = UUID.fromString("5ecb88d8-6a21-4a54-8967-72599f078963");
    private static final UUID REPORT_ID = UUID.fromString("9a1d3f52-1f0b-4a44-9d2e-6e1d0c7a51bb");

    /** UTC 2026-08-11 22:00 = Asia/Seoul 2026-08-12 07:00 */
    private static final Instant NOW = Instant.parse("2026-08-11T22:00:00Z");
    private static final LocalDate SEOUL_TODAY = LocalDate.of(2026, 8, 12);

    @Mock
    private DailyCheckInRepository dailyCheckInRepository;

    @Mock
    private HomeReportQueryRepository homeReportQueryRepository;

    private HomeService homeService;

    @BeforeEach
    void setUp() {
        homeService = new HomeService(
                dailyCheckInRepository,
                homeReportQueryRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        when(homeReportQueryRepository.findOldestPendingFollowUp(any(), any())).thenReturn(Optional.empty());
        when(homeReportQueryRepository.findMostRecentReport(any())).thenReturn(Optional.empty());
        when(dailyCheckInRepository.findByUserIdAndCheckInDate(any(), any())).thenReturn(Optional.empty());
        when(homeReportQueryRepository.findAppearanceCodes(any())).thenReturn(List.of("APP_REDNESS"));
        when(homeReportQueryRepository.findSensationCodes(any())).thenReturn(List.of("BREAKOUT"));
        when(homeReportQueryRepository.findSituationCodes(any())).thenReturn(List.of("SHAVING"));
    }

    @Test
    void serverDateFollowsSeoulNotUtc() {
        HomeResponse response = homeService.getHome(principal());

        assertThat(response.getServerDate()).isEqualTo(SEOUL_TODAY);
    }

    @Test
    void priorityIsEmptyWhenNothingToShow() {
        HomeResponse response = homeService.getHome(principal());

        assertThat(response.getPriority()).isEqualTo(HomePriority.EMPTY);
        assertThat(response.getPendingFollowUp()).isNull();
        assertThat(response.getToday()).isNull();
        assertThat(response.getRecentReport()).isNull();
    }

    @Test
    void priorityIsFollowUpWhenPendingFollowUpExists() {
        when(homeReportQueryRepository.findOldestPendingFollowUp(any(), any()))
                .thenReturn(Optional.of(pendingFollowUpRow()));
        when(dailyCheckInRepository.findByUserIdAndCheckInDate(any(), any()))
                .thenReturn(Optional.of(persisted(DailyCheckIn.noDiscomfort(USER_ID, SEOUL_TODAY))));
        when(homeReportQueryRepository.findMostRecentReport(any())).thenReturn(Optional.of(recentReportRow()));

        HomeResponse response = homeService.getHome(principal());

        assertThat(response.getPriority()).isEqualTo(HomePriority.FOLLOW_UP);
        assertThat(response.getPendingFollowUp().getReportId()).isEqualTo(REPORT_ID);
    }

    @Test
    void priorityIsTodayCheckInWhenNoFollowUpButTodaySaved() {
        when(dailyCheckInRepository.findByUserIdAndCheckInDate(any(), any()))
                .thenReturn(Optional.of(persisted(DailyCheckIn.noDiscomfort(USER_ID, SEOUL_TODAY))));
        when(homeReportQueryRepository.findMostRecentReport(any())).thenReturn(Optional.of(recentReportRow()));

        HomeResponse response = homeService.getHome(principal());

        assertThat(response.getPriority()).isEqualTo(HomePriority.TODAY_CHECK_IN);
        assertThat(response.getToday().getState()).isEqualTo(CheckInState.NO_DISCOMFORT);
        assertThat(response.getToday().getReportId()).isNull();
    }

    @Test
    void priorityIsRecentRecordWhenOnlyPastRecordExists() {
        when(homeReportQueryRepository.findMostRecentReport(any())).thenReturn(Optional.of(recentReportRow()));

        HomeResponse response = homeService.getHome(principal());

        assertThat(response.getPriority()).isEqualTo(HomePriority.RECENT_RECORD);
        assertThat(response.getRecentReport().getAppearances()).containsExactly("APP_REDNESS");
        assertThat(response.getRecentReport().getSkinChange()).isNull();
    }

    @Test
    void saveCreatesCheckInWhenNoneExists() {
        when(dailyCheckInRepository.saveAndFlush(any()))
                .thenAnswer(call -> persisted(call.getArgument(0)));

        SavedDailyCheckIn saved = homeService.saveNoDiscomfort(principal(), SEOUL_TODAY, noDiscomfortRequest());

        assertThat(saved.created()).isTrue();
        assertThat(saved.response().getDate()).isEqualTo(SEOUL_TODAY);
        assertThat(saved.response().getState()).isEqualTo(CheckInState.NO_DISCOMFORT);
    }

    @Test
    void saveReturnsExistingWithoutCreatingWhenSameValueSaved() {
        when(dailyCheckInRepository.findByUserIdAndCheckInDate(USER_ID, SEOUL_TODAY))
                .thenReturn(Optional.of(persisted(DailyCheckIn.noDiscomfort(USER_ID, SEOUL_TODAY))));

        SavedDailyCheckIn saved = homeService.saveNoDiscomfort(principal(), SEOUL_TODAY, noDiscomfortRequest());

        assertThat(saved.created()).isFalse();
        verify(dailyCheckInRepository, never()).saveAndFlush(any());
    }

    @Test
    void saveRejectsDateOtherThanSeoulToday() {
        assertThatThrownBy(() -> homeService.saveNoDiscomfort(
                principal(), SEOUL_TODAY.minusDays(1), noDiscomfortRequest()
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CHECK_IN_DATE_NOT_TODAY);

        verify(dailyCheckInRepository, never()).saveAndFlush(any());
    }

    @Test
    void saveRejectsSkinReportState() {
        assertThatThrownBy(() -> homeService.saveNoDiscomfort(
                principal(), SEOUL_TODAY, new SaveDailyCheckInRequest(CheckInState.SKIN_REPORT)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CHECK_IN_STATE_NOT_ALLOWED);

        verify(dailyCheckInRepository, never()).findByUserIdAndCheckInDate(any(), any());
    }

    /** 피부 보고가 확정된 날은 불편 없음으로 되돌릴 수 없다. */
    @Test
    void saveRejectsDayAlreadyReplacedBySkinReport() {
        DailyCheckIn reported = persisted(DailyCheckIn.noDiscomfort(USER_ID, SEOUL_TODAY));
        ReflectionTestUtils.setField(reported, "state", CheckInState.SKIN_REPORT);
        ReflectionTestUtils.setField(reported, "reportId", REPORT_ID);
        when(dailyCheckInRepository.findByUserIdAndCheckInDate(USER_ID, SEOUL_TODAY))
                .thenReturn(Optional.of(reported));

        assertThatThrownBy(() -> homeService.saveNoDiscomfort(principal(), SEOUL_TODAY, noDiscomfortRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CHECK_IN_ALREADY_REPORTED);

        verify(dailyCheckInRepository, never()).saveAndFlush(any());
    }

    private AuthenticatedUser principal() {
        return new AuthenticatedUser(
                USER_ID,
                SESSION_ID,
                LocalDateTime.of(2026, 8, 24, 0, 0),
                "csrf-token-value-that-is-long-enough"
        );
    }

    private SaveDailyCheckInRequest noDiscomfortRequest() {
        return new SaveDailyCheckInRequest(CheckInState.NO_DISCOMFORT);
    }

    private PendingFollowUpRow pendingFollowUpRow() {
        return new PendingFollowUpRow(
                REPORT_ID,
                SEOUL_TODAY.minusDays(1),
                LocalDateTime.of(2026, 8, 12, 0, 0),
                LocalDateTime.of(2026, 8, 14, 0, 0),
                "SELF_CARE_GUIDE"
        );
    }

    private RecentReportRow recentReportRow() {
        return new RecentReportRow(
                REPORT_ID,
                SEOUL_TODAY.minusDays(1),
                "RIGHT_CHIN",
                "SELF_CARE_GUIDE",
                "FOLLOW_UP_PENDING",
                null
        );
    }

    /** 저장 시 JPA Auditing이 채우는 시각을 흉내낸다. */
    private DailyCheckIn persisted(DailyCheckIn checkIn) {
        LocalDateTime savedAt = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        ReflectionTestUtils.setField(checkIn, "createdAt", savedAt);
        ReflectionTestUtils.setField(checkIn, "updatedAt", savedAt);
        return checkIn;
    }
}
