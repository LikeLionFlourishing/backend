package likelion.flourishing.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import likelion.flourishing.domain.home.entity.CheckInState;
import likelion.flourishing.domain.home.entity.DailyCheckIn;
import likelion.flourishing.domain.home.repository.DailyCheckInRepository;
import likelion.flourishing.domain.report.entity.AiGenerationStatus;
import likelion.flourishing.domain.report.entity.Appearance;
import likelion.flourishing.domain.report.entity.BodyArea;
import likelion.flourishing.domain.report.entity.CareAvailability;
import likelion.flourishing.domain.report.entity.CareResult;
import likelion.flourishing.domain.report.entity.PreCareCheck;
import likelion.flourishing.domain.report.entity.ResultType;
import likelion.flourishing.domain.report.entity.Sensation;
import likelion.flourishing.domain.report.entity.Situation;
import likelion.flourishing.domain.report.entity.SkinReport;
import likelion.flourishing.domain.report.idempotency.IdempotencyService;
import likelion.flourishing.domain.report.idempotency.IdempotentResponse;
import likelion.flourishing.domain.report.repository.SkinReportRepository;
import likelion.flourishing.domain.report.rule.CareRuleFixtures;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 보고 저장 단계 테스트.
 *
 * <p>확인하는 것: 그날 피부 점호를 보고 상태로 바꾸는지, 점호 기록이 없으면 만드는지,
 * 검사와 저장 사이에 다른 요청이 먼저 들어와 유니크 제약이 깨졌을 때 500이 아니라 409가 되는지,
 * 멱등 기록을 응답과 함께 남기는지.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SkinReportWriterTest {

    private static final UUID USER_ID = UUID.fromString("2c56fe08-ea1f-45fc-915d-c35b7c0bca39");
    private static final UUID IDEMPOTENCY_KEY = UUID.fromString("11111111-2222-4333-8444-555555555555");
    private static final UUID RULE_SET_ID = UUID.fromString("0198a31f-f33f-7000-8000-000000000901");
    private static final String OPERATION_ID = "POST /v1/skin-reports";
    private static final LocalDate REPORT_DATE = LocalDate.of(2026, 8, 15);

    @Mock
    private SkinReportRepository skinReportRepository;

    @Mock
    private DailyCheckInRepository dailyCheckInRepository;

    @Mock
    private CareResultGenerator careResultGenerator;

    @Mock
    private IdempotencyService idempotencyService;

    private SkinReportWriter writer;

    @BeforeEach
    void setUp() {
        writer = new SkinReportWriter(
                skinReportRepository, dailyCheckInRepository, careResultGenerator, idempotencyService
        );
        when(skinReportRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(dailyCheckInRepository.findByUserIdAndCheckInDate(any(), any())).thenReturn(Optional.empty());
        when(idempotencyService.serialize(any())).thenReturn("{\"id\":\"stored\"}");
        when(careResultGenerator.persist(any(), any(), any(), any()))
                .thenAnswer(call -> generated(call.getArgument(0)));
    }

    @Test
    void storedResponseCarriesTheReportIdAndIsSaved() {
        IdempotentResponse response = write();

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.resourceId()).isNotNull();
        verify(idempotencyService).store(USER_ID, OPERATION_ID, IDEMPOTENCY_KEY, "fingerprint", response);
    }

    @Test
    void missingCheckInIsCreatedAsSkinReport() {
        write();

        ArgumentCaptor<DailyCheckIn> saved = ArgumentCaptor.forClass(DailyCheckIn.class);
        verify(dailyCheckInRepository).save(saved.capture());
        assertThat(saved.getValue().getState()).isEqualTo(CheckInState.SKIN_REPORT);
        assertThat(saved.getValue().getCheckInDate()).isEqualTo(REPORT_DATE);
        assertThat(saved.getValue().getReportId()).isNotNull();
    }

    @Test
    void todayCheckInBecomesSkinReport() {
        DailyCheckIn noDiscomfort = DailyCheckIn.noDiscomfort(USER_ID, REPORT_DATE);
        when(dailyCheckInRepository.findByUserIdAndCheckInDate(USER_ID, REPORT_DATE))
                .thenReturn(Optional.of(noDiscomfort));

        write();

        assertThat(noDiscomfort.getState()).isEqualTo(CheckInState.SKIN_REPORT);
        assertThat(noDiscomfort.getReportId()).isNotNull();
        verify(dailyCheckInRepository, never()).save(any());
    }

    /** 검사와 저장 사이에 상대가 먼저 들어오면 유니크 제약이 깨진다. 그때도 사용자에게는 409가 나가야 한다. */
    @Test
    void concurrentInsertBecomesConflictInsteadOfServerError() {
        when(skinReportRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uq_skin_reports_user_date"));

        assertThatThrownBy(this::write)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.REPORT_ALREADY_EXISTS);
        verify(careResultGenerator, never()).persist(any(), any(), any(), any());
        verify(idempotencyService, never()).store(any(), any(), any(), any(), any());
    }

    private IdempotentResponse write() {
        return writer.write(
                USER_ID,
                OPERATION_ID,
                IDEMPOTENCY_KEY,
                "fingerprint",
                report(),
                plan(),
                null,
                (savedReport, generated) -> savedReport.getId()
        );
    }

    private CareResultPlan plan() {
        return new CareResultPlan(
                RULE_SET_ID,
                "2026-08-15-v1",
                ResultType.SELF_CARE_GUIDE,
                AiGenerationStatus.GENERATED,
                "오늘은 자극을 줄여 주세요.",
                null,
                List.of(CareRuleFixtures.commonRule()),
                List.of()
        );
    }

    private GeneratedCareResult generated(UUID reportId) {
        CareResult careResult = CareResult.selfCareGuide(
                reportId,
                USER_ID,
                RULE_SET_ID,
                null,
                null,
                AiGenerationStatus.GENERATED,
                "오늘은 자극을 줄여 주세요.",
                LocalDateTime.of(2026, 8, 15, 3, 0)
        );
        return new GeneratedCareResult(
                careResult, "2026-08-15-v1", List.of(CareRuleFixtures.commonRule()), List.of()
        );
    }

    private SkinReport report() {
        return SkinReport.create(
                USER_ID,
                REPORT_DATE,
                new byte[]{1},
                BodyArea.RIGHT_CHIN,
                null,
                CareAvailability.ALREADY_WASHED,
                ResultType.SELF_CARE_GUIDE,
                LocalDateTime.of(2026, 8, 15, 15, 0),
                LocalDateTime.of(2026, 8, 17, 15, 0),
                Set.of(Appearance.REDNESS),
                Set.of(Sensation.STINGING_BURNING),
                Set.of(Situation.SHAVING),
                Set.of(PreCareCheck.NONE)
        );
    }
}
