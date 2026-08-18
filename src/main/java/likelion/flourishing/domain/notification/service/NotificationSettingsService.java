package likelion.flourishing.domain.notification.service;

import java.util.UUID;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.notification.dto.request.UpdateNotificationSettingsRequest;
import likelion.flourishing.domain.notification.dto.response.NotificationSettingsResponse;
import likelion.flourishing.domain.notification.repository.PushSubscriptionRepository;
import likelion.flourishing.domain.onboarding.dto.response.NotificationConsentResponse;
import likelion.flourishing.domain.onboarding.entity.ConsentType;
import likelion.flourishing.domain.onboarding.repository.UserConsentRepository;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import likelion.flourishing.global.config.OnboardingProperties;
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
 * <p>시간대는 Asia/Seoul 고정이지만 발송 시각은 명세 v2_1에서 사용자가 온보딩에서 고르는 값이
 * 되어 저장된 행에서 읽는다. 설정 화면에서 시각을 바꾸는 것은 P1이라 이 요청으로는 바꿀 수 없다.
 */
@Service
public class NotificationSettingsService {

    /**
     * 명세 NotificationSettings.timeEditable. P0 배포에서는 항상 false다.
     *
     * <p>기능명세서 5.2 기준 설정 화면의 시각 변경이 P1이라, 이 값이 false인 동안 클라이언트는
     * 시각 변경 UI를 노출하지 않는다.
     */
    private static final boolean TIME_EDITABLE = false;

    private final NotificationSettingRepository notificationSettingRepository;
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final UserConsentRepository userConsentRepository;
    private final OnboardingProperties onboardingProperties;

    public NotificationSettingsService(
            NotificationSettingRepository notificationSettingRepository,
            PushSubscriptionRepository pushSubscriptionRepository,
            UserConsentRepository userConsentRepository,
            OnboardingProperties onboardingProperties
    ) {
        this.notificationSettingRepository = notificationSettingRepository;
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.userConsentRepository = userConsentRepository;
        this.onboardingProperties = onboardingProperties;
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

        NotificationConsentResponse consent = describeConsent(userId);

        return notificationSettingRepository.findById(userId)
                .map(setting -> toResponse(setting, consent, activeSubscriptions))
                .orElseGet(() -> NotificationSettingsResponse.of(
                        false,
                        NotificationSchedule.DEFAULT_TIME_TEXT,
                        NotificationSchedule.ZONE_TEXT,
                        TIME_EDITABLE,
                        NotificationPermission.DEFAULT,
                        consent,
                        activeSubscriptions
                ));
    }

    /**
     * 알림 사용 여부만 저장한다.
     *
     * <p>브라우저 권한 상태는 이 요청으로 바꾸지 않는다. 명세의 요청 본문에 enabled 하나만
     * 정의되어 있고, 권한은 온보딩에서 받는다. 저장된 권한은 그대로 유지한다.
     *
     * <p>알림을 켜겠다는 의사와 브라우저 권한은 별개라 enabled = true, permission = DENIED
     * 조합도 그대로 남는다. 실제 발송은 활성 구독 유무로 다시 걸러진다.
     */
    @Transactional
    public NotificationSettingsResponse updateSettings(
            AuthenticatedUser principal,
            UpdateNotificationSettingsRequest request
    ) {
        if (request.requestsTimeChange()) {
            // 명세가 P0에서 이 요청을 422 FEATURE_NOT_AVAILABLE로 거부하도록 정한다.
            throw new BusinessException(ErrorCode.FEATURE_NOT_AVAILABLE);
        }

        UUID userId = principal.userId();
        NotificationSetting setting = notificationSettingRepository.findById(userId)
                .orElseGet(() -> NotificationSetting.create(
                        userId,
                        request.enabled(),
                        NotificationSetting.DEFAULT_TIME,
                        NotificationPermission.DEFAULT
                ));
        // 시각은 이 요청으로 바꾸지 않는다. 저장된 값을 그대로 다시 넣는다.
        setting.update(request.enabled(), setting.getNotificationTime(), setting.getPermissionState());

        NotificationSetting saved = notificationSettingRepository.saveAndFlush(setting);
        return toResponse(
                saved,
                describeConsent(userId),
                pushSubscriptionRepository.countByUserIdAndActiveIsTrue(userId)
        );
    }

    private NotificationSettingsResponse toResponse(
            NotificationSetting setting,
            NotificationConsentResponse consent,
            long activeSubscriptions
    ) {
        return NotificationSettingsResponse.of(
                setting.isEnabled(),
                setting.getNotificationTime(),
                NotificationSchedule.ZONE_TEXT,
                TIME_EDITABLE,
                setting.getPermissionState(),
                consent,
                activeSubscriptions
        );
    }

    /**
     * 알림 수신 동의 기록.
     *
     * <p>명세가 이 필드를 필수로 두었으므로 동의한 적 없는 사용자에게도 값을 만들어 준다.
     * 그때는 agreed = false 와 지금 받고 있는 활성 버전을 담는다. "이 문구에 대해 동의하지
     * 않았다"는 뜻이라 증빙으로도 어긋나지 않는다.
     *
     * <p>활성 버전이 아닌 예전 동의만 있는 사용자도 동의하지 않은 것으로 본다. 문구가 바뀌면
     * 다시 받아야 하기 때문이다. 온보딩이 같은 기준으로 저장한다.
     */
    private NotificationConsentResponse describeConsent(UUID userId) {
        String activeVersion = onboardingProperties.notificationConsentVersion();
        return userConsentRepository
                .findByUserIdAndConsentTypeAndConsentVersion(userId, ConsentType.NOTIFICATION, activeVersion)
                .map(NotificationConsentResponse::agreed)
                .orElseGet(() -> NotificationConsentResponse.notAgreed(activeVersion));
    }
}
