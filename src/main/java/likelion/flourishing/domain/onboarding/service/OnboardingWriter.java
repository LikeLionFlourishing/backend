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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 온보딩 저장의 트랜잭션 단계만 담당한다.
 *
 * <p>별도 빈으로 둔 이유는 트랜잭션 경계 때문이다. 유니크 제약에 걸린 트랜잭션은 되돌아가야
 * 다시 읽을 수 있는데, 같은 클래스 안에서 자기 메서드를 부르면 프록시를 지나지 않아 새 트랜잭션이
 * 열리지 않는다. {@link OnboardingService}가 이 빈을 통해 불러야 재시도가 성립한다.
 */
@Component
public class OnboardingWriter {

    private final AuthService authService;
    private final UserConsentRepository userConsentRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final Clock clock;

    public OnboardingWriter(
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
     * 동의 이력과 알림 설정을 저장하고 가입을 완료 처리한다.
     *
     * <p>같은 동의 버전으로 다시 부르면 최초 동의 시각을 유지하고, 알림 설정은 항상 최신 요청으로
     * 덮어쓴다. 세 가지가 한 트랜잭션에서 함께 커밋되어야 동의 없이 가입만 완료된 상태가 남지 않는다.
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
                .orElseGet(() -> userConsentRepository.saveAndFlush(
                        UserConsent.accept(userId, ConsentType.SENSITIVE_DATA, consentVersion, now)
                ));
    }

    private NotificationSetting saveNotificationSetting(UUID userId, OnboardingRequest request) {
        return notificationSettingRepository.findById(userId)
                .map(existing -> {
                    existing.update(request.notificationEnabled(), request.notificationPermission());
                    return existing;
                })
                .orElseGet(() -> notificationSettingRepository.saveAndFlush(NotificationSetting.create(
                        userId,
                        request.notificationEnabled(),
                        request.notificationPermission()
                )));
    }
}
