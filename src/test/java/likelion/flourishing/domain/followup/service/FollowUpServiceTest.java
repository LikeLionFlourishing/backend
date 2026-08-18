package likelion.flourishing.domain.followup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.followup.dto.request.ClinicianFollowUpRequest;
import likelion.flourishing.domain.followup.dto.request.SaveFollowUpRequest;
import likelion.flourishing.domain.followup.dto.request.SelfCareFollowUpRequest;
import likelion.flourishing.domain.followup.entity.ActionCompletion;
import likelion.flourishing.domain.followup.entity.ClinicianCheckStatus;
import likelion.flourishing.domain.followup.entity.FollowUp;
import likelion.flourishing.domain.followup.entity.FollowUpKind;
import likelion.flourishing.domain.followup.entity.SkinChange;
import likelion.flourishing.domain.followup.repository.FollowUpReportRepository;
import likelion.flourishing.domain.followup.repository.FollowUpReportRepository.ReportRow;
import likelion.flourishing.domain.followup.repository.FollowUpRepository;
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

/**
 * FollowUpService의 저장 규칙 테스트. DB 없이 가짜 저장소와 고정 시계로 돌린다.
 *
 * <p>확인하는 것: 저장이 보고를 COMPLETED로 바꾸는지, 같은 내용 재요청과 다른 내용
 * 덮어쓰기를 가르는지, 보고 종류와 맞지 않는 경과·기한을 벗어난 요청을 막는지,
 * 남의 보고를 존재 여부 없이 404로 처리하는지.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FollowUpServiceTest {

    private static final UUID USER_ID = UUID.fromString("2c56fe08-ea1f-45fc-915d-c35b7c0bca39");
    private static final UUID SESSION_ID = UUID.fromString("5ecb88d8-6a21-4a54-8967-72599f078963");
    private static final UUID REPORT_ID = UUID.fromString("9a1d3f52-1f0b-4a44-9d2e-6e1d0c7a51bb");

    private static final Instant NOW = Instant.parse("2026-08-11T12:00:00Z");
    private static final LocalDateTime AVAILABLE_AT = LocalDateTime.of(2026, 8, 11, 0, 0);
    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 8, 13, 0, 0);

    @Mock
    private FollowUpRepository followUpRepository;

    @Mock
    private FollowUpReportRepository followUpReportRepository;

    private FollowUpService followUpService;

    @BeforeEach
    void setUp() {
        // 저장 단계는 진짜 FollowUpWriter를 쓰고 저장소만 가짜로 둔다. 두 클래스를 나눈 것은
        // 트랜잭션 경계 때문이지 규칙이 갈린 것이 아니라, 저장 규칙 검증은 이어서 하는 편이 낫다.
        followUpService = new FollowUpService(
                followUpRepository,
                new FollowUpWriter(
                        followUpRepository,
                        followUpReportRepository,
                        Clock.fixed(NOW, ZoneOffset.UTC)
                )
        );
        when(followUpReportRepository.findOwnedReport(any(), any()))
                .thenReturn(Optional.of(report("SELF_CARE_GUIDE", AVAILABLE_AT, EXPIRES_AT)));
        when(followUpRepository.findByReportIdAndUserId(any(), any())).thenReturn(Optional.empty());
        when(followUpRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void saveStoresSelfCareFollowUpAndCompletesReport() {
        SavedFollowUp saved = followUpService.saveFollowUp(principal(), REPORT_ID, selfCareRequest());

        assertThat(saved.created()).isTrue();
        assertThat(saved.response().getKind()).isEqualTo(FollowUpKind.SELF_CARE);
        assertThat(saved.response().getActionCompletion()).isEqualTo(ActionCompletion.MOSTLY_DONE);
        assertThat(saved.response().getClinicianCheckStatus()).isNull();
        verify(followUpReportRepository).markCompleted(REPORT_ID, USER_ID);
    }

    @Test
    void saveStoresClinicianFollowUpForClinicianReport() {
        when(followUpReportRepository.findOwnedReport(any(), any()))
                .thenReturn(Optional.of(report("CLINICIAN_CHECK", AVAILABLE_AT, EXPIRES_AT)));

        SavedFollowUp saved = followUpService.saveFollowUp(principal(), REPORT_ID, clinicianRequest());

        assertThat(saved.created()).isTrue();
        assertThat(saved.response().getClinicianCheckStatus()).isEqualTo(ClinicianCheckStatus.CHECKED);
        // 명세 v2_1에서 의료진 확인 경과도 행동 실행 여부를 저장한다.
        assertThat(saved.response().getActionCompletion()).isEqualTo(ActionCompletion.PARTLY_DONE);
    }

    @Test
    void saveReturnsExistingWhenSameContentSentAgain() {
        when(followUpRepository.findByReportIdAndUserId(REPORT_ID, USER_ID))
                .thenReturn(Optional.of(FollowUp.of(REPORT_ID, USER_ID, selfCareRequest(), AVAILABLE_AT)));

        SavedFollowUp saved = followUpService.saveFollowUp(principal(), REPORT_ID, selfCareRequest());

        assertThat(saved.created()).isFalse();
        verify(followUpRepository, never()).saveAndFlush(any());
        verify(followUpReportRepository, never()).markCompleted(any(), any());
    }

    @Test
    void saveRejectsOverwriteWithDifferentContent() {
        when(followUpRepository.findByReportIdAndUserId(REPORT_ID, USER_ID))
                .thenReturn(Optional.of(FollowUp.of(REPORT_ID, USER_ID, selfCareRequest(), AVAILABLE_AT)));

        SaveFollowUpRequest changed = new SelfCareFollowUpRequest(
                FollowUpKind.SELF_CARE, SkinChange.WORSENED, ActionCompletion.NOT_DONE
        );

        assertThatThrownBy(() -> followUpService.saveFollowUp(principal(), REPORT_ID, changed))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FOLLOW_UP_ALREADY_SUBMITTED);
    }

    /**
     * 의료진 확인 여부는 그대로인데 행동 실행 여부만 다른 재요청도 덮어쓰기다.
     * 명세 v2_1에서 이 값이 CLINICIAN_CHECK의 필수 필드가 됐으므로 같은 내용 판정에 들어가야 한다.
     */
    @Test
    void saveRejectsClinicianOverwriteThatOnlyChangesActionCompletion() {
        when(followUpReportRepository.findOwnedReport(any(), any()))
                .thenReturn(Optional.of(report("CLINICIAN_CHECK", AVAILABLE_AT, EXPIRES_AT)));
        when(followUpRepository.findByReportIdAndUserId(REPORT_ID, USER_ID))
                .thenReturn(Optional.of(FollowUp.of(REPORT_ID, USER_ID, clinicianRequest(), AVAILABLE_AT)));

        SaveFollowUpRequest changed = new ClinicianFollowUpRequest(
                FollowUpKind.CLINICIAN_CHECK,
                SkinChange.SIMILAR,
                ActionCompletion.NOT_DONE,
                ClinicianCheckStatus.CHECKED
        );

        assertThatThrownBy(() -> followUpService.saveFollowUp(principal(), REPORT_ID, changed))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FOLLOW_UP_ALREADY_SUBMITTED);
    }

    @Test
    void saveRejectsKindThatDoesNotMatchReport() {
        assertThatThrownBy(() -> followUpService.saveFollowUp(principal(), REPORT_ID, clinicianRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FOLLOW_UP_KIND_MISMATCH);

        verify(followUpRepository, never()).saveAndFlush(any());
    }

    @Test
    void saveRejectsBeforeAvailableTime() {
        when(followUpReportRepository.findOwnedReport(any(), any())).thenReturn(Optional.of(
                report("SELF_CARE_GUIDE", LocalDateTime.of(2026, 8, 12, 0, 0), EXPIRES_AT)
        ));

        assertThatThrownBy(() -> followUpService.saveFollowUp(principal(), REPORT_ID, selfCareRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FOLLOW_UP_NOT_AVAILABLE_YET);
    }

    @Test
    void saveRejectsAfterExpiry() {
        when(followUpReportRepository.findOwnedReport(any(), any())).thenReturn(Optional.of(
                report("SELF_CARE_GUIDE", AVAILABLE_AT, LocalDateTime.of(2026, 8, 11, 6, 0))
        ));

        assertThatThrownBy(() -> followUpService.saveFollowUp(principal(), REPORT_ID, selfCareRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FOLLOW_UP_EXPIRED);
    }

    /** 기한이 지난 뒤에도 이미 저장한 경과의 같은 내용 재요청은 200이어야 한다. */
    @Test
    void saveStillReturnsStoredFollowUpAfterExpiry() {
        when(followUpReportRepository.findOwnedReport(any(), any())).thenReturn(Optional.of(
                report("SELF_CARE_GUIDE", AVAILABLE_AT, LocalDateTime.of(2026, 8, 11, 6, 0))
        ));
        when(followUpRepository.findByReportIdAndUserId(REPORT_ID, USER_ID))
                .thenReturn(Optional.of(FollowUp.of(REPORT_ID, USER_ID, selfCareRequest(), AVAILABLE_AT)));

        SavedFollowUp saved = followUpService.saveFollowUp(principal(), REPORT_ID, selfCareRequest());

        assertThat(saved.created()).isFalse();
    }

    @Test
    void saveRejectsReportOwnedBySomeoneElseAsNotFound() {
        when(followUpReportRepository.findOwnedReport(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> followUpService.saveFollowUp(principal(), REPORT_ID, selfCareRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void getReturnsNotFoundWhenNoFollowUpStored() {
        assertThatThrownBy(() -> followUpService.getFollowUp(principal(), REPORT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void getReturnsStoredFollowUp() {
        when(followUpRepository.findByReportIdAndUserId(REPORT_ID, USER_ID))
                .thenReturn(Optional.of(FollowUp.of(REPORT_ID, USER_ID, selfCareRequest(), AVAILABLE_AT)));

        assertThat(followUpService.getFollowUp(principal(), REPORT_ID).getSkinChange())
                .isEqualTo(SkinChange.IMPROVED);
    }

    /** 입력 구간은 availableAt 이상 expiresAt 미만이다. 만료 시각 자체는 이미 끝난 시점이다. */
    @Test
    void saveRejectsRequestExactlyAtExpiryTime() {
        when(followUpReportRepository.findOwnedReport(any(), any())).thenReturn(Optional.of(
                report("SELF_CARE_GUIDE", AVAILABLE_AT, LocalDateTime.ofInstant(NOW, ZoneOffset.UTC))
        ));

        assertThatThrownBy(() -> followUpService.saveFollowUp(principal(), REPORT_ID, selfCareRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FOLLOW_UP_EXPIRED);

        verify(followUpRepository, never()).saveAndFlush(any());
    }

    /** 반대쪽 경계는 열려 있다. 입력 시작 시각 정각은 받는다. */
    @Test
    void saveAcceptsRequestExactlyAtAvailableTime() {
        when(followUpReportRepository.findOwnedReport(any(), any())).thenReturn(Optional.of(
                report("SELF_CARE_GUIDE", LocalDateTime.ofInstant(NOW, ZoneOffset.UTC), EXPIRES_AT)
        ));

        SavedFollowUp saved = followUpService.saveFollowUp(principal(), REPORT_ID, selfCareRequest());

        assertThat(saved.created()).isTrue();
    }

    /**
     * 같은 보고에 첫 저장이 겹치면 둘 다 저장된 경과가 없다고 보고 각자 넣으려 한다. 뒤늦은 쪽은
     * uq_follow_ups_report에 걸리는데, 되돌아간 뒤 다시 읽으면 먼저 저장된 경과가 보인다.
     * 재시도가 없으면 이 자리가 그대로 500이 된다.
     */
    @Test
    void saveReturnsStoredFollowUpWhenConcurrentInsertLoses() {
        when(followUpRepository.findByReportIdAndUserId(any(), any()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(FollowUp.of(REPORT_ID, USER_ID, selfCareRequest(), AVAILABLE_AT)));
        when(followUpRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uq_follow_ups_report"));

        SavedFollowUp saved = followUpService.saveFollowUp(principal(), REPORT_ID, selfCareRequest());

        assertThat(saved.created()).isFalse();
        verify(followUpRepository, times(1)).saveAndFlush(any());
    }

    /** 겹친 두 요청의 내용이 다르면 뒤늦은 쪽은 덮어쓰기 시도이므로 409가 되어야 한다. */
    @Test
    void saveRejectsConcurrentInsertWithDifferentContent() {
        when(followUpRepository.findByReportIdAndUserId(any(), any()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(FollowUp.of(REPORT_ID, USER_ID, selfCareRequest(), AVAILABLE_AT)));
        when(followUpRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uq_follow_ups_report"));

        SaveFollowUpRequest other = new SelfCareFollowUpRequest(
                FollowUpKind.SELF_CARE, SkinChange.WORSENED, ActionCompletion.NOT_DONE
        );

        assertThatThrownBy(() -> followUpService.saveFollowUp(principal(), REPORT_ID, other))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FOLLOW_UP_ALREADY_SUBMITTED);
    }

    /**
     * 모르는 result_type을 조용히 CLINICIAN_CHECK로 몰지 않는다. 그러면 종류가 맞지 않는 경과가
     * FOLLOW_UP_KIND_MISMATCH 검사를 그대로 통과한다.
     */
    @Test
    void saveFailsLoudlyForUnknownResultType() {
        when(followUpReportRepository.findOwnedReport(any(), any()))
                .thenReturn(Optional.of(report("SELF_CARE_GUIDE_V2", AVAILABLE_AT, EXPIRES_AT)));

        assertThatThrownBy(() -> followUpService.saveFollowUp(principal(), REPORT_ID, selfCareRequest()))
                .isInstanceOf(IllegalStateException.class);

        verify(followUpRepository, never()).saveAndFlush(any());
    }

    private AuthenticatedUser principal() {
        return new AuthenticatedUser(
                USER_ID,
                SESSION_ID,
                LocalDateTime.of(2026, 8, 24, 0, 0),
                "csrf-token-value-that-is-long-enough"
        );
    }

    private ReportRow report(String resultType, LocalDateTime availableAt, LocalDateTime expiresAt) {
        return new ReportRow(resultType, "FOLLOW_UP_PENDING", availableAt, expiresAt);
    }

    private SaveFollowUpRequest selfCareRequest() {
        return new SelfCareFollowUpRequest(
                FollowUpKind.SELF_CARE, SkinChange.IMPROVED, ActionCompletion.MOSTLY_DONE
        );
    }

    private SaveFollowUpRequest clinicianRequest() {
        return new ClinicianFollowUpRequest(
                FollowUpKind.CLINICIAN_CHECK,
                SkinChange.SIMILAR,
                ActionCompletion.PARTLY_DONE,
                ClinicianCheckStatus.CHECKED
        );
    }
}
