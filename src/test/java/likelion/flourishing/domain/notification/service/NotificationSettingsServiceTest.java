package likelion.flourishing.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.notification.dto.request.NotificationConsentInput;
import likelion.flourishing.domain.notification.dto.request.UpdateNotificationSettingsRequest;
import likelion.flourishing.domain.notification.dto.response.NotificationSettingsResponse;
import likelion.flourishing.domain.notification.repository.PushSubscriptionRepository;
import likelion.flourishing.domain.onboarding.repository.UserConsentRepository;
import likelion.flourishing.global.config.OnboardingProperties;
import likelion.flourishing.domain.onboarding.entity.ConsentType;
import likelion.flourishing.domain.onboarding.entity.UserConsent;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import likelion.flourishing.domain.onboarding.entity.NotificationPermission;
import likelion.flourishing.domain.onboarding.entity.NotificationSetting;
import likelion.flourishing.domain.onboarding.repository.NotificationSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationSettingsServiceTest {

    private static final UUID USER_ID = UUID.fromString("2c56fe08-ea1f-45fc-915d-c35b7c0bca39");
    private static final Instant NOW = Instant.parse("2026-08-19T03:00:00Z");
    private static final String ACTIVE_VERSION = "2026-08-16";
    private static final UUID SESSION_ID = UUID.fromString("5ecb88d8-6a21-4a54-8967-72599f078963");

    @Mock
    private NotificationSettingRepository notificationSettingRepository;

    @Mock
    private PushSubscriptionRepository pushSubscriptionRepository;

    @Mock
    private UserConsentRepository userConsentRepository;

    private NotificationSettingsService service;

    @BeforeEach
    void setUp() {
        service = new NotificationSettingsService(
                notificationSettingRepository,
                pushSubscriptionRepository,
                userConsentRepository,
                new OnboardingProperties("2026-08-16", "2026-08-16"),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        when(notificationSettingRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        // 켜기에는 활성 버전 동의가 있어야 한다. 동의를 다루지 않는 테스트는 이 기본값을 쓴다.
        storedConsent(true);
    }

    @Test
    void settingsReportTheUserChosenTimeAndFixedZone() {
        when(notificationSettingRepository.findById(USER_ID)).thenReturn(Optional.of(
                NotificationSetting.create(USER_ID, true, "21:00", NotificationPermission.GRANTED)
        ));
        when(pushSubscriptionRepository.countByUserIdAndActiveIsTrue(USER_ID)).thenReturn(2L);

        NotificationSettingsResponse response = service.getSettings(principal());

        assertThat(response.isEnabled()).isTrue();
        assertThat(response.getTime()).isEqualTo("21:00");
        assertThat(response.getTimezone()).isEqualTo("Asia/Seoul");
        assertThat(response.getPermission()).isEqualTo(NotificationPermission.GRANTED);
        assertThat(response.getActiveSubscriptionCount()).isEqualTo(2L);
    }

    @Test
    void missingSettingReadsAsDisabledWithoutWriting() {
        when(notificationSettingRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(pushSubscriptionRepository.countByUserIdAndActiveIsTrue(USER_ID)).thenReturn(0L);

        NotificationSettingsResponse response = service.getSettings(principal());

        assertThat(response.isEnabled()).isFalse();
        assertThat(response.getPermission()).isEqualTo(NotificationPermission.DEFAULT);
        verify(notificationSettingRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateStoresEnabledFlag() {
        NotificationSetting setting = NotificationSetting.create(USER_ID, false, "21:00", NotificationPermission.GRANTED);
        when(notificationSettingRepository.findById(USER_ID)).thenReturn(Optional.of(setting));

        NotificationSettingsResponse response = service.updateSettings(
                principal(), new UpdateNotificationSettingsRequest(true, null, null)
        );

        assertThat(response.isEnabled()).isTrue();
        assertThat(setting.isEnabled()).isTrue();
        verify(notificationSettingRepository).saveAndFlush(setting);
    }

    @Test
    void updateKeepsStoredPermissionWhenRequestOmitsIt() {
        NotificationSetting setting = NotificationSetting.create(USER_ID, true, "21:00", NotificationPermission.GRANTED);
        when(notificationSettingRepository.findById(USER_ID)).thenReturn(Optional.of(setting));

        service.updateSettings(principal(), new UpdateNotificationSettingsRequest(false, null, null));

        assertThat(setting.getPermissionState()).isEqualTo(NotificationPermission.GRANTED);
        assertThat(setting.isEnabled()).isFalse();
    }

    /** 권한은 온보딩에서만 바뀐다. 이 요청은 저장된 권한을 건드리지 않는다. */
    @Test
    void updateKeepsStoredPermission() {
        NotificationSetting setting = NotificationSetting.create(USER_ID, true, "21:00", NotificationPermission.GRANTED);
        when(notificationSettingRepository.findById(USER_ID)).thenReturn(Optional.of(setting));

        service.updateSettings(principal(), new UpdateNotificationSettingsRequest(true, null, null));

        assertThat(setting.getPermissionState()).isEqualTo(NotificationPermission.GRANTED);
    }

    @Test
    void updateCreatesRowWhenOnboardingNeverStoredOne() {
        when(notificationSettingRepository.findById(USER_ID)).thenReturn(Optional.empty());

        NotificationSettingsResponse response = service.updateSettings(
                principal(), new UpdateNotificationSettingsRequest(true, null, null)
        );

        assertThat(response.isEnabled()).isTrue();
        assertThat(response.getPermission()).isEqualTo(NotificationPermission.DEFAULT);
        verify(notificationSettingRepository).saveAndFlush(any(NotificationSetting.class));
    }

    @Test
    void enabledWithDeniedPermissionIsStoredAsIs() {
        when(notificationSettingRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(pushSubscriptionRepository.countByUserIdAndActiveIsTrue(eq(USER_ID))).thenReturn(0L);

        NotificationSetting stored = NotificationSetting.create(USER_ID, false, "21:00", NotificationPermission.DENIED);
        when(notificationSettingRepository.findById(USER_ID)).thenReturn(Optional.of(stored));

        NotificationSettingsResponse response = service.updateSettings(
                principal(), new UpdateNotificationSettingsRequest(true, null, null)
        );

        assertThat(response.isEnabled()).isTrue();
        assertThat(response.getPermission()).isEqualTo(NotificationPermission.DENIED);
        assertThat(response.getActiveSubscriptionCount()).isZero();
    }

    /** 명세가 consent를 필수로 두어 동의한 적 없는 사용자에게도 값을 만들어 준다. */
    @Test
    void consentIsReportedAsNotAgreedWithTheActiveVersionWhenThereIsNoRecord() {
        when(notificationSettingRepository.findById(USER_ID)).thenReturn(Optional.of(
                NotificationSetting.create(USER_ID, true, "21:00", NotificationPermission.GRANTED)
        ));
        when(pushSubscriptionRepository.countByUserIdAndActiveIsTrue(USER_ID)).thenReturn(1L);
        when(userConsentRepository.findByUserIdAndConsentTypeAndConsentVersion(
                USER_ID, ConsentType.NOTIFICATION, "2026-08-16")).thenReturn(Optional.empty());

        NotificationSettingsResponse response = service.getSettings(principal());

        assertThat(response.getConsent().agreed()).isFalse();
        assertThat(response.getConsent().version()).isEqualTo("2026-08-16");
        assertThat(response.getConsent().agreedAt()).isNull();
    }

    /**
     * 명세가 time 만 담긴 요청도 받도록 정한다. timeEditable 은 화면에 변경 UI 를 노출할지에 대한
     * 값일 뿐이라 API 가 값을 받지 않는다는 뜻이 아니다.
     */
    @Test
    void changingOnlyTheTimeKeepsTheEnabledFlag() {
        NotificationSetting setting = NotificationSetting.create(
                USER_ID, true, "17:30", NotificationPermission.GRANTED
        );
        when(notificationSettingRepository.findById(USER_ID)).thenReturn(Optional.of(setting));

        NotificationSettingsResponse response = service.updateSettings(
                principal(), new UpdateNotificationSettingsRequest(null, "21:00", null)
        );

        assertThat(response.getTime()).isEqualTo("21:00");
        assertThat(response.isEnabled()).isTrue();
    }

    /** 켜기에는 이번 요청이든 이전 기록이든 활성 버전 동의가 있어야 한다. */
    @Test
    void enablingWithoutAnyConsentIsRejected() {
        storedConsent(false);
        when(notificationSettingRepository.findById(USER_ID)).thenReturn(Optional.of(
                NotificationSetting.create(USER_ID, false, "17:30", NotificationPermission.GRANTED)
        ));

        assertThatThrownBy(() -> service.updateSettings(
                principal(), new UpdateNotificationSettingsRequest(true, null, null)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CONSENT_REQUIRED);
    }

    /** 한 요청으로 "동의하면서 켜기"가 되어야 한다. 동의를 먼저 반영하지 않으면 422가 난다. */
    @Test
    void enablingWithConsentInTheSameRequestIsAllowed() {
        storedConsent(false);
        when(notificationSettingRepository.findById(USER_ID)).thenReturn(Optional.of(
                NotificationSetting.create(USER_ID, false, "17:30", NotificationPermission.GRANTED)
        ));
        when(userConsentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(userConsentRepository.findByUserIdAndConsentTypeAndConsentVersion(
                USER_ID, ConsentType.NOTIFICATION, ACTIVE_VERSION))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(UserConsent.accept(
                        USER_ID, ConsentType.NOTIFICATION, ACTIVE_VERSION,
                        LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)
                )));

        NotificationSettingsResponse response = service.updateSettings(
                principal(),
                new UpdateNotificationSettingsRequest(
                        true, null, new NotificationConsentInput(true, ACTIVE_VERSION)
                )
        );

        assertThat(response.isEnabled()).isTrue();
        verify(userConsentRepository).save(any(UserConsent.class));
    }

    /** 끄기는 동의 없이도 언제나 된다. */
    @Test
    void disablingNeedsNoConsent() {
        storedConsent(false);
        NotificationSetting setting = NotificationSetting.create(
                USER_ID, true, "17:30", NotificationPermission.GRANTED
        );
        when(notificationSettingRepository.findById(USER_ID)).thenReturn(Optional.of(setting));

        NotificationSettingsResponse response = service.updateSettings(
                principal(), new UpdateNotificationSettingsRequest(false, null, null)
        );

        assertThat(response.isEnabled()).isFalse();
    }

    /** 철회는 행을 지우지 않고 상태를 뒤집는다. "동의한 적 없음"과 "물렸음"은 다른 사실이다. */
    @Test
    void withdrawingConsentFlipsTheStoredRowInsteadOfDeletingIt() {
        UserConsent stored = UserConsent.accept(
                USER_ID, ConsentType.NOTIFICATION, ACTIVE_VERSION,
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).minusDays(3)
        );
        when(userConsentRepository.findByUserIdAndConsentTypeAndConsentVersion(
                USER_ID, ConsentType.NOTIFICATION, ACTIVE_VERSION)).thenReturn(Optional.of(stored));
        when(userConsentRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(notificationSettingRepository.findById(USER_ID)).thenReturn(Optional.of(
                NotificationSetting.create(USER_ID, false, "17:30", NotificationPermission.GRANTED)
        ));

        service.updateSettings(
                principal(),
                new UpdateNotificationSettingsRequest(
                        null, null, new NotificationConsentInput(false, ACTIVE_VERSION)
                )
        );

        assertThat(stored.isAccepted()).isFalse();
        verify(userConsentRepository, never()).delete(any());
    }

    /** 문구가 바뀌면 다시 받아야 한다. 온보딩과 같은 규칙이다. */
    @Test
    void consentWithAnInactiveVersionIsRejected() {
        when(notificationSettingRepository.findById(USER_ID)).thenReturn(Optional.of(
                NotificationSetting.create(USER_ID, false, "17:30", NotificationPermission.GRANTED)
        ));

        assertThatThrownBy(() -> service.updateSettings(
                principal(),
                new UpdateNotificationSettingsRequest(
                        null, null, new NotificationConsentInput(true, "2026-01-01")
                )
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CONSENT_VERSION_NOT_ACCEPTED);
    }

    private void storedConsent(boolean agreed) {
        when(userConsentRepository.findByUserIdAndConsentTypeAndConsentVersion(
                USER_ID, ConsentType.NOTIFICATION, ACTIVE_VERSION))
                .thenReturn(agreed
                        ? Optional.of(UserConsent.accept(
                                USER_ID, ConsentType.NOTIFICATION, ACTIVE_VERSION,
                                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)))
                        : Optional.empty());
    }

    /** 설정 화면의 시각 변경은 P1이라 P0 배포에서는 항상 false다. */
    @Test
    void timeEditableIsFalseInThisRelease() {
        when(notificationSettingRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(pushSubscriptionRepository.countByUserIdAndActiveIsTrue(USER_ID)).thenReturn(0L);
        when(userConsentRepository.findByUserIdAndConsentTypeAndConsentVersion(any(), any(), any()))
                .thenReturn(Optional.empty());

        assertThat(service.getSettings(principal()).isTimeEditable()).isFalse();
    }

    private AuthenticatedUser principal() {
        return new AuthenticatedUser(
                USER_ID, SESSION_ID, LocalDateTime.of(2026, 8, 24, 0, 0), "csrf-token-value-that-is-long-enough"
        );
    }
}
