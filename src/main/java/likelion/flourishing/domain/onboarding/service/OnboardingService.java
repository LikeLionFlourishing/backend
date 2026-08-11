package likelion.flourishing.domain.onboarding.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.auth.service.AuthService;
import likelion.flourishing.domain.onboarding.dto.request.OnboardingRequest;
import likelion.flourishing.domain.onboarding.dto.response.OnboardingResponse;
import likelion.flourishing.domain.onboarding.entity.ConsentType;
import likelion.flourishing.domain.onboarding.entity.NotificationSetting;
import likelion.flourishing.domain.onboarding.entity.UserConsent;
import likelion.flourishing.domain.onboarding.repository.NotificationSettingRepository;
import likelion.flourishing.domain.onboarding.repository.UserConsentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 최신 필수 동의와 알림 선택을 저장하고 가입을 완료 처리한다. */
@Service
public class OnboardingService {

    private final AuthService authService;
    private final UserConsentRepository userConsentRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final Clock clock;

    public OnboardingService(
            AuthService authService,
            UserConsentRepository userConsentRepository,
            NotificationSettingRepository notificationSettingRepository,
            Clock clock
    ) {
        this.authService = authService;
        this.userConsentRepository = userConsentRepository;
        this.notificationSettingRepository = notificationSettingRepository;
        this.clock = clock;
    }

    /**
     * 온보딩을 완료한다. PUT이라 여러 번 불러도 같은 결과를 남긴다.
     *
     * <p>같은 동의 버전으로 다시 부르면 최초 동의 시각을 유지하고, 다른 버전이면 새 이력을 남긴다.
     * 알림 설정은 항상 최신 요청으로 덮어쓴다.
     */
    @Transactional
    public OnboardingResponse complete(AuthenticatedUser principal, OnboardingRequest request) {
        UUID userId = principal.userId();
        LocalDateTime now = LocalDateTime.now(clock);

        UserConsent consent = saveConsent(userId, request.consentVersion(), now);
        NotificationSetting notificationSetting = saveNotificationSetting(userId, request);
        LocalDateTime completedAt = authService.completeSignup(principal, now);

        return OnboardingResponse.from(consent, notificationSetting, completedAt);
    }

    /**
     * 요청이 담은 동의는 민감정보 동의 하나뿐이라 그 종류로만 이력을 남긴다.
     * 사용자가 보내지 않은 동의를 서버가 대신 기록하지 않는다.
     */
    private UserConsent saveConsent(UUID userId, String consentVersion, LocalDateTime now) {
        return userConsentRepository
                .findByUserIdAndConsentTypeAndConsentVersion(userId, ConsentType.SENSITIVE_DATA, consentVersion)
                .orElseGet(() -> userConsentRepository.save(
                        UserConsent.accept(userId, ConsentType.SENSITIVE_DATA, consentVersion, now)
                ));
    }

    private NotificationSetting saveNotificationSetting(UUID userId, OnboardingRequest request) {
        return notificationSettingRepository.findById(userId)
                .map(existing -> {
                    existing.update(request.notificationEnabled(), request.notificationPermission());
                    return existing;
                })
                .orElseGet(() -> notificationSettingRepository.save(NotificationSetting.create(
                        userId,
                        request.notificationEnabled(),
                        request.notificationPermission()
                )));
    }
}
