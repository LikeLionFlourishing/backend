package likelion.flourishing.domain.notification.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.notification.dto.request.UpdateNotificationSettingsRequest;
import likelion.flourishing.domain.notification.dto.response.NotificationSettingsResponse;
import likelion.flourishing.domain.notification.repository.PushSubscriptionRepository;
import likelion.flourishing.domain.onboarding.entity.NotificationPermission;
import likelion.flourishing.domain.onboarding.entity.NotificationSetting;
import likelion.flourishing.domain.onboarding.repository.NotificationSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 설정 조회와 사용 여부 변경.
 *
 * <p>notification_settings는 온보딩이 먼저 만드는 행이라 엔티티와 저장소를 새로 만들지 않고
 * onboarding 도메인의 것을 함께 쓴다. 같은 테이블에 엔티티가 두 벌 생기면 매핑이 어긋난다.
 *
 * <p>발송 시각과 시간대는 P0 고정값이라 요청으로 바꿀 수 없다. 응답에는 상수를 넣는다.
 */
@Service
public class NotificationSettingsService {

    private final NotificationSettingRepository notificationSettingRepository;
    private final PushSubscriptionRepository pushSubscriptionRepository;

    public NotificationSettingsService(
            NotificationSettingRepository notificationSettingRepository,
            PushSubscriptionRepository pushSubscriptionRepository
    ) {
        this.notificationSettingRepository = notificationSettingRepository;
        this.pushSubscriptionRepository = pushSubscriptionRepository;
    }

    /**
     * 저장된 설정. 아직 행이 없으면 기본값을 만들어 돌려준다.
     *
     * <p>조회 요청으로 행을 새로 쓰지는 않는다. 온보딩을 건너뛴 사용자에게도 화면이 보여야 해서
     * 기본값만 응답으로 만들고 updatedAt은 비운다.
     */
    @Transactional(readOnly = true)
    public NotificationSettingsResponse getSettings(AuthenticatedUser principal) {
        UUID userId = principal.userId();
        long activeSubscriptions = pushSubscriptionRepository.countByUserIdAndActiveIsTrue(userId);

        return notificationSettingRepository.findById(userId)
                .map(setting -> toResponse(setting, activeSubscriptions))
                .orElseGet(() -> NotificationSettingsResponse.of(
                        false,
                        NotificationSchedule.NOTIFICATION_TIME_TEXT,
                        NotificationSchedule.ZONE_TEXT,
                        NotificationPermission.DEFAULT,
                        activeSubscriptions,
                        null
                ));
    }

    /**
     * 사용 여부와 브라우저 권한 상태를 저장한다.
     *
     * <p>permissionState를 보내지 않으면 저장된 값을 그대로 둔다. 알림을 끄고 켜는 요청과
     * 브라우저 권한이 바뀐 사실을 알리는 요청이 서로 다른 시점에 오기 때문이다.
     *
     * <p>알림을 켜겠다는 의사와 브라우저 권한은 별개라 enabled = true, permissionState = DENIED
     * 조합도 그대로 저장한다. 실제 발송은 활성 구독 유무로 다시 걸러진다.
     */
    @Transactional
    public NotificationSettingsResponse updateSettings(
            AuthenticatedUser principal,
            UpdateNotificationSettingsRequest request
    ) {
        UUID userId = principal.userId();
        NotificationSetting setting = notificationSettingRepository.findById(userId)
                .orElseGet(() -> NotificationSetting.create(
                        userId, request.enabled(), permissionOrDefault(request.permissionState())
                ));
        setting.update(
                request.enabled(),
                request.permissionState() == null ? setting.getPermissionState() : request.permissionState()
        );

        NotificationSetting saved = notificationSettingRepository.saveAndFlush(setting);
        return toResponse(saved, pushSubscriptionRepository.countByUserIdAndActiveIsTrue(userId));
    }

    private NotificationSettingsResponse toResponse(NotificationSetting setting, long activeSubscriptions) {
        return NotificationSettingsResponse.of(
                setting.isEnabled(),
                NotificationSchedule.NOTIFICATION_TIME_TEXT,
                NotificationSchedule.ZONE_TEXT,
                setting.getPermissionState(),
                activeSubscriptions,
                updatedAt(setting)
        );
    }

    private OffsetDateTime updatedAt(NotificationSetting setting) {
        return setting.getUpdatedAt() == null ? null : setting.getUpdatedAt().atOffset(ZoneOffset.UTC);
    }

    private NotificationPermission permissionOrDefault(NotificationPermission permission) {
        return permission == null ? NotificationPermission.DEFAULT : permission;
    }
}
