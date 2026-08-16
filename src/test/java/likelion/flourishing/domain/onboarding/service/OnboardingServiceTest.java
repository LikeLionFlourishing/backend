package likelion.flourishing.domain.onboarding.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.auth.service.AuthService;
import likelion.flourishing.domain.onboarding.dto.request.OnboardingRequest;
import likelion.flourishing.domain.onboarding.dto.response.OnboardingResponse;
import likelion.flourishing.domain.onboarding.entity.ConsentType;
import likelion.flourishing.domain.onboarding.entity.NotificationPermission;
import likelion.flourishing.domain.onboarding.entity.NotificationSetting;
import likelion.flourishing.domain.onboarding.entity.UserConsent;
import likelion.flourishing.domain.onboarding.repository.NotificationSettingRepository;
import likelion.flourishing.domain.onboarding.repository.UserConsentRepository;
import likelion.flourishing.global.config.OnboardingProperties;
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
 * OnboardingService의 저장 규칙 테스트. DB 없이 가짜 저장소와 고정 시계로만 돌린다.
 *
 * <p>시계를 고정한 이유는 저장 시각과 응답 시각이 같은지 값으로 비교하기 위해서다.
 * 실제 시간을 쓰면 호출할 때마다 값이 달라져 검증할 수 없다.
 *
 * <p>확인하는 것:
 * <ul>
 *   <li>첫 호출에 동의 이력과 알림 설정을 만들고 응답 필드를 채우는지
 *   <li>알림을 켜면 동의 2건(SENSITIVE_DATA, NOTIFICATION)을 남기는지
 *   <li>알림을 받지 않겠다고 하면 알림 동의 이력을 남기지 않고 기본 시각을 저장하는지
 *   <li>시간 피커에서 고른 값을 그대로 저장하는지
 *   <li>같은 버전으로 다시 부르면 최초 동의 시각을 유지하고 새 행을 만들지 않는지
 *   <li>알림 설정은 기존 행이 있으면 새로 만들지 않고 덮어쓰는지
 *   <li>알림 켜기 + 권한 거부 조합도 그대로 저장하는지
 *   <li>서버가 받고 있지 않은 동의 버전 두 가지를 각각 거절하는지
 *   <li>가입 완료 처리를 auth에 위임해 호출하는지
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OnboardingServiceTest {

    private static final UUID USER_ID = UUID.fromString("2c56fe08-ea1f-45fc-915d-c35b7c0bca39");
    private static final UUID SESSION_ID = UUID.fromString("5ecb88d8-6a21-4a54-8967-72599f078963");
    private static final String CONSENT_VERSION = "2026-08-16";
    private static final String NOTIFICATION_CONSENT_VERSION = "2026-08-16";
    private static final String PICKED_TIME = "21:00";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 11, 7, 0);

    @Mock
    private AuthService authService;

    @Mock
    private UserConsentRepository userConsentRepository;

    @Mock
    private NotificationSettingRepository notificationSettingRepository;

    private OnboardingService onboardingService;

    @BeforeEach
    void setUp() {
        // 저장 단계는 진짜 OnboardingWriter를 쓰고 저장소만 가짜로 둔다. 두 클래스를 나눈 것은
        // 트랜잭션 경계 때문이지 규칙이 갈린 것이 아니라, 규칙 검증은 이어서 하는 편이 낫다.
        OnboardingProperties properties = new OnboardingProperties(CONSENT_VERSION, NOTIFICATION_CONSENT_VERSION);
        OnboardingWriter onboardingWriter = new OnboardingWriter(
                authService,
                userConsentRepository,
                notificationSettingRepository,
                properties,
                Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        );
        onboardingService = new OnboardingService(properties, onboardingWriter);

        when(userConsentRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(notificationSettingRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(authService.completeSignup(any(), any())).thenReturn(NOW);
    }

    @Test
    void completeSavesConsentAndNotificationSettingForFirstCall() {
        when(userConsentRepository.findByUserIdAndConsentTypeAndConsentVersion(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(notificationSettingRepository.findById(USER_ID)).thenReturn(Optional.empty());

        OnboardingResponse response = onboardingService.complete(principal(), enabledRequest(PICKED_TIME));

        assertThat(response.getConsentVersion()).isEqualTo(CONSENT_VERSION);
        assertThat(response.getConsentedAt()).isEqualTo(NOW.atOffset(ZoneOffset.UTC));
        assertThat(response.isNotificationEnabled()).isTrue();
        assertThat(response.getNotificationPermission()).isEqualTo(NotificationPermission.GRANTED);
        assertThat(response.getNotificationTime()).isEqualTo(PICKED_TIME);
        assertThat(response.getNotificationConsent().agreed()).isTrue();
        assertThat(response.getNotificationConsent().version()).isEqualTo(NOTIFICATION_CONSENT_VERSION);
        assertThat(response.getNotificationConsent().agreedAt()).isEqualTo(NOW.atOffset(ZoneOffset.UTC));
        assertThat(response.getCompletedAt()).isEqualTo(NOW.atOffset(ZoneOffset.UTC));
    }

    /** 명세 v2_1의 동의는 2건이다. 알림을 켜는 요청은 두 종류를 모두 이력으로 남긴다. */
    @Test
    void completeRecordsBothConsentsWhenNotificationEnabled() {
        when(userConsentRepository.findByUserIdAndConsentTypeAndConsentVersion(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(notificationSettingRepository.findById(USER_ID)).thenReturn(Optional.empty());

        onboardingService.complete(principal(), enabledRequest(PICKED_TIME));

        ArgumentCaptor<UserConsent> captor = ArgumentCaptor.forClass(UserConsent.class);
        verify(userConsentRepository, times(2)).saveAndFlush(captor.capture());

        List<UserConsent> saved = captor.getAllValues();
        assertThat(saved).allSatisfy(consent -> {
            assertThat(consent.getUserId()).isEqualTo(USER_ID);
            assertThat(consent.isAccepted()).isTrue();
        });
        assertThat(saved).extracting(UserConsent::getConsentType)
                .containsExactly(ConsentType.SENSITIVE_DATA, ConsentType.NOTIFICATION);
    }

    /**
     * 알림을 받지 않겠다고 한 요청은 동의한 적이 없으므로 알림 동의 이력을 남기지 않는다.
     * 응답에는 동의하지 않았다는 사실과 지금 받고 있는 문구 버전을 담는다.
     */
    @Test
    void completeDoesNotRecordNotificationConsentWhenSkipped() {
        when(userConsentRepository.findByUserIdAndConsentTypeAndConsentVersion(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(notificationSettingRepository.findById(USER_ID)).thenReturn(Optional.empty());

        OnboardingResponse response = onboardingService.complete(principal(), skippedRequest());

        ArgumentCaptor<UserConsent> captor = ArgumentCaptor.forClass(UserConsent.class);
        verify(userConsentRepository, times(1)).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getConsentType()).isEqualTo(ConsentType.SENSITIVE_DATA);

        assertThat(response.isNotificationEnabled()).isFalse();
        assertThat(response.getNotificationConsent().agreed()).isFalse();
        assertThat(response.getNotificationConsent().version()).isEqualTo(NOTIFICATION_CONSENT_VERSION);
        assertThat(response.getNotificationConsent().agreedAt()).isNull();
    }

    /**
     * 알림을 껐어도 기본 시각을 저장한다. 다음 날 경과 입력 가능 시점을 이 값으로 계산하기
     * 때문에 알림을 받지 않는 사용자에게도 필요하다.
     */
    @Test
    void completeStoresDefaultTimeWhenNotificationSkipped() {
        when(userConsentRepository.findByUserIdAndConsentTypeAndConsentVersion(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(notificationSettingRepository.findById(USER_ID)).thenReturn(Optional.empty());

        OnboardingResponse response = onboardingService.complete(principal(), skippedRequest());

        ArgumentCaptor<NotificationSetting> captor = ArgumentCaptor.forClass(NotificationSetting.class);
        verify(notificationSettingRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getNotificationTime()).isEqualTo(NotificationSetting.DEFAULT_TIME);
        assertThat(response.getNotificationTime()).isEqualTo(NotificationSetting.DEFAULT_TIME);
    }

    @Test
    void completeKeepsFirstConsentedAtWhenSameVersionIsSentAgain() {
        LocalDateTime firstConsentedAt = NOW.minusDays(3);
        UserConsent existing = UserConsent.accept(
                USER_ID,
                ConsentType.SENSITIVE_DATA,
                CONSENT_VERSION,
                firstConsentedAt
        );
        when(userConsentRepository.findByUserIdAndConsentTypeAndConsentVersion(
                USER_ID,
                ConsentType.SENSITIVE_DATA,
                CONSENT_VERSION
        )).thenReturn(Optional.of(existing));
        when(notificationSettingRepository.findById(USER_ID)).thenReturn(Optional.empty());

        OnboardingResponse response = onboardingService.complete(principal(), skippedRequest());

        assertThat(response.getConsentedAt()).isEqualTo(firstConsentedAt.atOffset(ZoneOffset.UTC));
        verify(userConsentRepository, never()).saveAndFlush(any());
    }

    @Test
    void completeOverwritesExistingNotificationSetting() {
        NotificationSetting existing = NotificationSetting.create(
                USER_ID, true, PICKED_TIME, NotificationPermission.GRANTED
        );
        when(userConsentRepository.findByUserIdAndConsentTypeAndConsentVersion(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(notificationSettingRepository.findById(USER_ID)).thenReturn(Optional.of(existing));

        OnboardingResponse response = onboardingService.complete(
                principal(), skippedRequest(NotificationPermission.DENIED)
        );

        assertThat(existing.isEnabled()).isFalse();
        assertThat(existing.getNotificationTime()).isEqualTo(NotificationSetting.DEFAULT_TIME);
        assertThat(existing.getPermissionState()).isEqualTo(NotificationPermission.DENIED);
        assertThat(response.isNotificationEnabled()).isFalse();
        verify(notificationSettingRepository, never()).saveAndFlush(any());
    }

    /** 알림을 켜겠다는 의사와 브라우저 권한은 별개라 거부 상태여도 그대로 저장한다. */
    @Test
    void completeStoresEnabledWithDeniedPermission() {
        when(userConsentRepository.findByUserIdAndConsentTypeAndConsentVersion(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(notificationSettingRepository.findById(USER_ID)).thenReturn(Optional.empty());

        OnboardingResponse response = onboardingService.complete(
                principal(), enabledRequest(PICKED_TIME, NotificationPermission.DENIED)
        );

        assertThat(response.isNotificationEnabled()).isTrue();
        assertThat(response.getNotificationPermission()).isEqualTo(NotificationPermission.DENIED);
    }

    @Test
    void completeMarksSignupCompleted() {
        when(userConsentRepository.findByUserIdAndConsentTypeAndConsentVersion(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(notificationSettingRepository.findById(USER_ID)).thenReturn(Optional.empty());

        onboardingService.complete(principal(), enabledRequest(PICKED_TIME));

        verify(authService).completeSignup(any(AuthenticatedUser.class), eq(NOW));
    }

    /**
     * 신규 알림 설정 행이 실제로 저장되는지 확인한다. 기존 행을 덮어쓰는 경로만 검증하면
     * 저장 호출이 통째로 빠져도 스위트가 통과한다.
     */
    @Test
    void completeSavesNewNotificationSettingRow() {
        when(userConsentRepository.findByUserIdAndConsentTypeAndConsentVersion(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(notificationSettingRepository.findById(USER_ID)).thenReturn(Optional.empty());

        onboardingService.complete(principal(), enabledRequest(PICKED_TIME));

        ArgumentCaptor<NotificationSetting> captor = ArgumentCaptor.forClass(NotificationSetting.class);
        verify(notificationSettingRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().isEnabled()).isTrue();
        assertThat(captor.getValue().getNotificationTime()).isEqualTo(PICKED_TIME);
        assertThat(captor.getValue().getPermissionState()).isEqualTo(NotificationPermission.GRANTED);
    }

    /**
     * 동의 증빙은 서버가 아는 문구여야 의미가 있다. 서버가 들고 있는 활성 버전이 아니면
     * 이력을 남기지 않고 가입 완료도 하지 않는다.
     */
    @Test
    void completeRejectsConsentVersionServerIsNotCollecting() {
        OnboardingRequest outdated = new OnboardingRequest(
                "2020-01-01", true, false, NotificationPermission.DEFAULT, null, null, null
        );

        assertThatThrownBy(() -> onboardingService.complete(principal(), outdated))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CONSENT_VERSION_NOT_ACCEPTED);

        verify(userConsentRepository, never()).saveAndFlush(any());
        verify(authService, never()).completeSignup(any(), any());
    }

    /** 알림 수신 동의 버전도 같은 기준으로 본다. 두 문구는 따로 개정되므로 따로 검사한다. */
    @Test
    void completeRejectsNotificationConsentVersionServerIsNotCollecting() {
        OnboardingRequest outdated = new OnboardingRequest(
                CONSENT_VERSION, true, true, NotificationPermission.GRANTED, PICKED_TIME, true, "2020-01-01"
        );

        assertThatThrownBy(() -> onboardingService.complete(principal(), outdated))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CONSENT_VERSION_NOT_ACCEPTED);

        verify(userConsentRepository, never()).saveAndFlush(any());
        verify(authService, never()).completeSignup(any(), any());
    }

    /**
     * 같은 사용자의 첫 PUT이 겹치면 둘 다 저장된 것이 없다고 보고 각자 넣으려 한다. 뒤늦은 쪽은
     * 유니크 제약에 걸리는데, 그때는 이미 먼저 저장된 값이 있으므로 다시 읽어 같은 응답을 돌려준다.
     * 재시도가 없으면 이 자리가 그대로 500이 된다.
     */
    @Test
    void completeReturnsFirstWriterResultWhenConcurrentInsertLoses() {
        LocalDateTime firstConsentedAt = NOW.minusMinutes(1);
        UserConsent winner = UserConsent.accept(
                USER_ID, ConsentType.SENSITIVE_DATA, CONSENT_VERSION, firstConsentedAt
        );
        // 첫 시도에는 아무것도 안 보이고, 되돌아간 뒤 다시 읽으면 먼저 저장된 행이 보인다.
        when(userConsentRepository.findByUserIdAndConsentTypeAndConsentVersion(any(), any(), any()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(notificationSettingRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(userConsentRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uq_user_consents_version"));

        OnboardingResponse response = onboardingService.complete(principal(), skippedRequest());

        assertThat(response.getConsentedAt()).isEqualTo(firstConsentedAt.atOffset(ZoneOffset.UTC));
        verify(userConsentRepository, times(1)).saveAndFlush(any());
    }

    /** 두 번째도 제약에 걸리면 경합이 원인이 아니므로 더 시도하지 않고 그대로 올린다. */
    @Test
    void completeGivesUpWhenRetryHitsTheSameConflict() {
        when(userConsentRepository.findByUserIdAndConsentTypeAndConsentVersion(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(notificationSettingRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(userConsentRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uq_user_consents_version"));

        assertThatThrownBy(() -> onboardingService.complete(principal(), skippedRequest()))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(userConsentRepository, times(2)).saveAndFlush(any());
    }

    private AuthenticatedUser principal() {
        return new AuthenticatedUser(
                USER_ID,
                SESSION_ID,
                LocalDateTime.of(2026, 8, 24, 0, 0),
                "csrf-token-value-that-is-long-enough"
        );
    }

    /** 시간 피커에서 값을 고르고 {@code 시작하기}를 누른 요청. 동의 2/2가 함께 온다. */
    private OnboardingRequest enabledRequest(String notificationTime) {
        return enabledRequest(notificationTime, NotificationPermission.GRANTED);
    }

    private OnboardingRequest enabledRequest(String notificationTime, NotificationPermission permission) {
        return new OnboardingRequest(
                CONSENT_VERSION, true, true, permission, notificationTime, true, NOTIFICATION_CONSENT_VERSION
        );
    }

    /** {@code 알림을 받지 않을게요}를 누른 요청. 피커 값과 동의 2/2가 모두 없다. */
    private OnboardingRequest skippedRequest() {
        return skippedRequest(NotificationPermission.DEFAULT);
    }

    private OnboardingRequest skippedRequest(NotificationPermission permission) {
        return new OnboardingRequest(CONSENT_VERSION, true, false, permission, null, null, null);
    }
}
