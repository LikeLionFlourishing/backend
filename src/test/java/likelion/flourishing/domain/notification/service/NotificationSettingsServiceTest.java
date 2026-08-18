package likelion.flourishing.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.notification.dto.request.UpdateNotificationSettingsRequest;
import likelion.flourishing.domain.notification.dto.response.NotificationSettingsResponse;
import likelion.flourishing.domain.notification.repository.PushSubscriptionRepository;
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
    private static final UUID SESSION_ID = UUID.fromString("5ecb88d8-6a21-4a54-8967-72599f078963");

    @Mock
    private NotificationSettingRepository notificationSettingRepository;

    @Mock
    private PushSubscriptionRepository pushSubscriptionRepository;

    private NotificationSettingsService service;

    @BeforeEach
    void setUp() {
        service = new NotificationSettingsService(notificationSettingRepository, pushSubscriptionRepository);
        when(notificationSettingRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void settingsReportFixedTimeAndZoneWithActiveSubscriptionCount() {
        when(notificationSettingRepository.findById(USER_ID)).thenReturn(Optional.of(
                NotificationSetting.create(USER_ID, true, "17:30", NotificationPermission.GRANTED)
        ));
        when(pushSubscriptionRepository.countByUserIdAndActiveIsTrue(USER_ID)).thenReturn(2L);

        NotificationSettingsResponse response = service.getSettings(principal());

        assertThat(response.isEnabled()).isTrue();
        assertThat(response.getTime()).isEqualTo("17:30");
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
        NotificationSetting setting = NotificationSetting.create(USER_ID, false, "17:30", NotificationPermission.GRANTED);
        when(notificationSettingRepository.findById(USER_ID)).thenReturn(Optional.of(setting));

        NotificationSettingsResponse response = service.updateSettings(
                principal(), new UpdateNotificationSettingsRequest(true)
        );

        assertThat(response.isEnabled()).isTrue();
        assertThat(setting.isEnabled()).isTrue();
        verify(notificationSettingRepository).saveAndFlush(setting);
    }

    @Test
    void updateKeepsStoredPermissionWhenRequestOmitsIt() {
        NotificationSetting setting = NotificationSetting.create(USER_ID, true, "17:30", NotificationPermission.GRANTED);
        when(notificationSettingRepository.findById(USER_ID)).thenReturn(Optional.of(setting));

        service.updateSettings(principal(), new UpdateNotificationSettingsRequest(false));

        assertThat(setting.getPermissionState()).isEqualTo(NotificationPermission.GRANTED);
        assertThat(setting.isEnabled()).isFalse();
    }

    /** 권한은 온보딩에서만 바뀐다. 이 요청은 저장된 권한을 건드리지 않는다. */
    @Test
    void updateKeepsStoredPermission() {
        NotificationSetting setting = NotificationSetting.create(USER_ID, true, "17:30", NotificationPermission.GRANTED);
        when(notificationSettingRepository.findById(USER_ID)).thenReturn(Optional.of(setting));

        service.updateSettings(principal(), new UpdateNotificationSettingsRequest(true));

        assertThat(setting.getPermissionState()).isEqualTo(NotificationPermission.GRANTED);
    }

    @Test
    void updateCreatesRowWhenOnboardingNeverStoredOne() {
        when(notificationSettingRepository.findById(USER_ID)).thenReturn(Optional.empty());

        NotificationSettingsResponse response = service.updateSettings(
                principal(), new UpdateNotificationSettingsRequest(true)
        );

        assertThat(response.isEnabled()).isTrue();
        assertThat(response.getPermission()).isEqualTo(NotificationPermission.DEFAULT);
        verify(notificationSettingRepository).saveAndFlush(any(NotificationSetting.class));
    }

    @Test
    void enabledWithDeniedPermissionIsStoredAsIs() {
        when(notificationSettingRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(pushSubscriptionRepository.countByUserIdAndActiveIsTrue(eq(USER_ID))).thenReturn(0L);

        NotificationSetting stored = NotificationSetting.create(USER_ID, false, "17:30", NotificationPermission.DENIED);
        when(notificationSettingRepository.findById(USER_ID)).thenReturn(Optional.of(stored));

        NotificationSettingsResponse response = service.updateSettings(
                principal(), new UpdateNotificationSettingsRequest(true)
        );

        assertThat(response.isEnabled()).isTrue();
        assertThat(response.getPermission()).isEqualTo(NotificationPermission.DENIED);
        assertThat(response.getActiveSubscriptionCount()).isZero();
    }

    private AuthenticatedUser principal() {
        return new AuthenticatedUser(
                USER_ID, SESSION_ID, LocalDateTime.of(2026, 8, 24, 0, 0), "csrf-token-value-that-is-long-enough"
        );
    }
}
