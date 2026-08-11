package likelion.flourishing.domain.onboarding.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * OnboardingService의 저장 규칙 테스트. DB 없이 가짜 저장소와 고정 시계로만 돌린다.
 *
 * <p>시계를 고정한 이유는 저장 시각과 응답 시각이 같은지 값으로 비교하기 위해서다.
 * 실제 시간을 쓰면 호출할 때마다 값이 달라져 검증할 수 없다.
 *
 * <p>확인하는 것:
 * <ul>
 *   <li>첫 호출에 동의 이력과 알림 설정을 만들고 응답 다섯 필드를 채우는지
 *   <li>남기는 동의가 SENSITIVE_DATA 한 건뿐인지 — 요청에 없는 동의를 서버가 대신 기록하지 않는다
 *   <li>같은 버전으로 다시 부르면 최초 동의 시각을 유지하고 새 행을 만들지 않는지
 *   <li>알림 설정은 기존 행이 있으면 새로 만들지 않고 덮어쓰는지
 *   <li>알림 켜기 + 권한 거부 조합도 그대로 저장하는지
 *   <li>가입 완료 처리를 auth에 위임해 호출하는지
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OnboardingServiceTest {

    private static final UUID USER_ID = UUID.fromString("2c56fe08-ea1f-45fc-915d-c35b7c0bca39");
    private static final UUID SESSION_ID = UUID.fromString("5ecb88d8-6a21-4a54-8967-72599f078963");
    private static final String CONSENT_VERSION = "2026-08-09";
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
        onboardingService = new OnboardingService(
                authService,
                userConsentRepository,
                notificationSettingRepository,
                Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        );

        when(userConsentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(notificationSettingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(authService.completeSignup(any(), any())).thenReturn(NOW);
    }

    @Test
    void completeSavesConsentAndNotificationSettingForFirstCall() {
        when(userConsentRepository.findByUserIdAndConsentTypeAndConsentVersion(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(notificationSettingRepository.findById(USER_ID)).thenReturn(Optional.empty());

        OnboardingResponse response = onboardingService.complete(principal(), request(true, NotificationPermission.GRANTED));

        assertThat(response.getConsentVersion()).isEqualTo(CONSENT_VERSION);
        assertThat(response.getConsentedAt()).isEqualTo(NOW.atOffset(ZoneOffset.UTC));
        assertThat(response.isNotificationEnabled()).isTrue();
        assertThat(response.getNotificationPermission()).isEqualTo(NotificationPermission.GRANTED);
        assertThat(response.getCompletedAt()).isEqualTo(NOW.atOffset(ZoneOffset.UTC));
    }

    @Test
    void completeRecordsOnlySensitiveDataConsent() {
        when(userConsentRepository.findByUserIdAndConsentTypeAndConsentVersion(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(notificationSettingRepository.findById(USER_ID)).thenReturn(Optional.empty());

        onboardingService.complete(principal(), request(true, NotificationPermission.GRANTED));

        ArgumentCaptor<UserConsent> captor = ArgumentCaptor.forClass(UserConsent.class);
        verify(userConsentRepository).save(captor.capture());

        List<UserConsent> saved = captor.getAllValues();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getConsentType()).isEqualTo(ConsentType.SENSITIVE_DATA);
        assertThat(saved.get(0).isAccepted()).isTrue();
        assertThat(saved.get(0).getUserId()).isEqualTo(USER_ID);
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

        OnboardingResponse response = onboardingService.complete(principal(), request(true, NotificationPermission.GRANTED));

        assertThat(response.getConsentedAt()).isEqualTo(firstConsentedAt.atOffset(ZoneOffset.UTC));
        verify(userConsentRepository, never()).save(any());
    }

    @Test
    void completeOverwritesExistingNotificationSetting() {
        NotificationSetting existing = NotificationSetting.create(USER_ID, true, NotificationPermission.GRANTED);
        when(userConsentRepository.findByUserIdAndConsentTypeAndConsentVersion(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(notificationSettingRepository.findById(USER_ID)).thenReturn(Optional.of(existing));

        OnboardingResponse response = onboardingService.complete(principal(), request(false, NotificationPermission.DENIED));

        assertThat(existing.isEnabled()).isFalse();
        assertThat(existing.getPermissionState()).isEqualTo(NotificationPermission.DENIED);
        assertThat(response.isNotificationEnabled()).isFalse();
        verify(notificationSettingRepository, never()).save(any());
    }

    /** 알림을 켜겠다는 의사와 브라우저 권한은 별개라 거부 상태여도 그대로 저장한다. */
    @Test
    void completeStoresEnabledWithDeniedPermission() {
        when(userConsentRepository.findByUserIdAndConsentTypeAndConsentVersion(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(notificationSettingRepository.findById(USER_ID)).thenReturn(Optional.empty());

        OnboardingResponse response = onboardingService.complete(principal(), request(true, NotificationPermission.DENIED));

        assertThat(response.isNotificationEnabled()).isTrue();
        assertThat(response.getNotificationPermission()).isEqualTo(NotificationPermission.DENIED);
    }

    @Test
    void completeMarksSignupCompleted() {
        when(userConsentRepository.findByUserIdAndConsentTypeAndConsentVersion(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(notificationSettingRepository.findById(USER_ID)).thenReturn(Optional.empty());

        onboardingService.complete(principal(), request(true, NotificationPermission.GRANTED));

        verify(authService).completeSignup(any(AuthenticatedUser.class), eq(NOW));
    }

    private AuthenticatedUser principal() {
        return new AuthenticatedUser(
                USER_ID,
                SESSION_ID,
                LocalDateTime.of(2026, 8, 24, 0, 0),
                "csrf-token-value-that-is-long-enough"
        );
    }

    private OnboardingRequest request(boolean notificationEnabled, NotificationPermission permission) {
        return new OnboardingRequest(CONSENT_VERSION, true, notificationEnabled, permission);
    }
}
