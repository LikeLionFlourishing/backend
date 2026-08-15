package likelion.flourishing.domain.home.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import org.springframework.dao.DataIntegrityViolationException;
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
        // 저장 단계는 진짜 DailyCheckInWriter를 쓰고 저장소만 가짜로 둔다. 두 클래스를 나눈 것은
        // 트랜잭션 경계 때문이지 규칙이 갈린 것이 아니라, 저장 규칙 검증은 이어서 하는 편이 낫다.
        homeService = new HomeService(
                dailyCheckInRepository,
                homeReportQueryRepository,
                new DailyCheckInWriter(dailyCheckInRepository),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        // 조회 스텁을 USER_ID로 고정한다. any()로 두면 principal.userId() 대신 sessionId()를
        // 넘기도록 바뀌어도 테스트가 그대로 통과한다. 둘 다 UUID라 타입으로도 걸리지 않는다.
        when(homeReportQueryRepository.findOldestPendingFollowUp(eq(USER_ID), any()))
                .thenReturn(Optional.empty());
        when(homeReportQueryRepository.findMostRecentReport(eq(USER_ID))).thenReturn(Optional.empty());
        when(dailyCheckInRepository.findByUserIdAndCheckInDate(eq(USER_ID), any()))
                .thenReturn(Optional.empty());
        when(homeReportQueryRepository.findAppearanceCodes(any())).thenReturn(List.of("REDNESS"));
        when(homeReportQueryRepository.findSensationCodes(any())).thenReturn(List.of("ITCHING"));
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
    void priorityIsFollowUpWhenPendingFollowUpIsSubmittable() {
        when(homeReportQueryRepository.findOldestPendingFollowUp(eq(USER_ID), any()))
                .thenReturn(Optional.of(openPendingFollowUpRow()));
        when(dailyCheckInRepository.findByUserIdAndCheckInDate(eq(USER_ID), any()))
                .thenReturn(Optional.of(persisted(DailyCheckIn.noDiscomfort(USER_ID, SEOUL_TODAY))));
        when(homeReportQueryRepository.findMostRecentReport(eq(USER_ID))).thenReturn(Optional.of(recentReportRow()));

        HomeResponse response = homeService.getHome(principal());

        assertThat(response.getPriority()).isEqualTo(HomePriority.FOLLOW_UP);
        assertThat(response.getPendingFollowUp().getReportId()).isEqualTo(REPORT_ID);
    }

    @Test
    void priorityIsTodayCheckInWhenNoFollowUpButTodaySaved() {
        when(dailyCheckInRepository.findByUserIdAndCheckInDate(eq(USER_ID), any()))
                .thenReturn(Optional.of(persisted(DailyCheckIn.noDiscomfort(USER_ID, SEOUL_TODAY))));
        when(homeReportQueryRepository.findMostRecentReport(eq(USER_ID))).thenReturn(Optional.of(recentReportRow()));

        HomeResponse response = homeService.getHome(principal());

        assertThat(response.getPriority()).isEqualTo(HomePriority.TODAY_CHECK_IN);
        assertThat(response.getToday().getState()).isEqualTo(CheckInState.NO_DISCOMFORT);
        assertThat(response.getToday().getReportId()).isNull();
    }

    @Test
    void priorityIsRecentRecordWhenOnlyPastRecordExists() {
        when(homeReportQueryRepository.findMostRecentReport(eq(USER_ID))).thenReturn(Optional.of(recentReportRow()));

        HomeResponse response = homeService.getHome(principal());

        assertThat(response.getPriority()).isEqualTo(HomePriority.RECENT_RECORD);
        assertThat(response.getRecentReport().getAppearances()).containsExactly("REDNESS");
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

    /**
     * 같은 사용자의 첫 저장이 겹치면 둘 다 저장된 것이 없다고 보고 각자 넣으려 한다. 뒤늦은 쪽은
     * uq_daily_check_ins_user_date에 걸리는데, 되돌아간 뒤 다시 읽으면 먼저 저장된 값이 보인다.
     * 재시도가 없으면 이 자리가 그대로 500이 된다.
     */
    @Test
    void saveReturnsFirstWriterResultWhenConcurrentInsertLoses() {
        DailyCheckIn winner = persisted(DailyCheckIn.noDiscomfort(USER_ID, SEOUL_TODAY));
        when(dailyCheckInRepository.findByUserIdAndCheckInDate(USER_ID, SEOUL_TODAY))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(dailyCheckInRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uq_daily_check_ins_user_date"));

        SavedDailyCheckIn saved = homeService.saveNoDiscomfort(principal(), SEOUL_TODAY, noDiscomfortRequest());

        assertThat(saved.created()).isFalse();
        assertThat(saved.response().getState()).isEqualTo(CheckInState.NO_DISCOMFORT);
        verify(dailyCheckInRepository, times(1)).saveAndFlush(any());
    }

    /** 겹친 사이에 같은 날 피부 보고가 확정됐다면 다시 읽은 값이 SKIN_REPORT라 409가 나간다. */
    @Test
    void saveReturnsConflictWhenSkinReportWonTheRace() {
        DailyCheckIn reported = persisted(DailyCheckIn.noDiscomfort(USER_ID, SEOUL_TODAY));
        ReflectionTestUtils.setField(reported, "state", CheckInState.SKIN_REPORT);
        ReflectionTestUtils.setField(reported, "reportId", REPORT_ID);
        when(dailyCheckInRepository.findByUserIdAndCheckInDate(USER_ID, SEOUL_TODAY))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(reported));
        when(dailyCheckInRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uq_daily_check_ins_user_date"));

        assertThatThrownBy(() -> homeService.saveNoDiscomfort(principal(), SEOUL_TODAY, noDiscomfortRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CHECK_IN_ALREADY_REPORTED);
    }

    /**
     * 아직 입력할 수 없는 경과는 카드로는 보여 주되 최우선으로 고르지 않는다. 명세 PendingFollowUp이
     * availableFrom을 필수로 담는 이유가 사전 안내이고, 서버가 최우선이라고 한 항목을 눌렀는데
     * 아직 입력할 수 없으면 그것이 곧 오동작이다.
     */
    @Test
    void pendingFollowUpIsAnnouncedButNotPrioritizedBeforeItOpens() {
        when(homeReportQueryRepository.findOldestPendingFollowUp(eq(USER_ID), any()))
                .thenReturn(Optional.of(pendingFollowUpRow()));

        HomeResponse response = homeService.getHome(principal());

        assertThat(response.getPendingFollowUp()).isNotNull();
        assertThat(response.getPendingFollowUp().getAvailableFrom())
                .isAfter(NOW.atOffset(ZoneOffset.UTC));
        assertThat(response.getPriority()).isEqualTo(HomePriority.EMPTY);
    }

    /** 입력 시작 시각 정각부터 최우선이 된다. */
    @Test
    void pendingFollowUpBecomesPriorityExactlyAtAvailableTime() {
        when(homeReportQueryRepository.findOldestPendingFollowUp(eq(USER_ID), any())).thenReturn(Optional.of(
                new PendingFollowUpRow(
                        REPORT_ID,
                        SEOUL_TODAY.minusDays(1),
                        LocalDateTime.ofInstant(NOW, ZoneOffset.UTC),
                        LocalDateTime.of(2026, 8, 14, 0, 0),
                        "SELF_CARE_GUIDE"
                )
        ));

        HomeResponse response = homeService.getHome(principal());

        assertThat(response.getPriority()).isEqualTo(HomePriority.FOLLOW_UP);
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

    /** 이미 입력할 수 있는 경과. availableFrom이 고정 시계보다 앞선다. */
    private PendingFollowUpRow openPendingFollowUpRow() {
        return new PendingFollowUpRow(
                REPORT_ID,
                SEOUL_TODAY.minusDays(1),
                LocalDateTime.of(2026, 8, 11, 0, 0),
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
