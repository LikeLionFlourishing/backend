package likelion.flourishing.domain.onboarding.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.auth.service.AuthService;
import likelion.flourishing.domain.onboarding.dto.request.OnboardingRequest;
import likelion.flourishing.domain.onboarding.dto.response.NotificationConsentResponse;
import likelion.flourishing.domain.onboarding.dto.response.OnboardingResponse;
import likelion.flourishing.domain.onboarding.entity.ConsentType;
import likelion.flourishing.domain.onboarding.entity.NotificationSetting;
import likelion.flourishing.domain.onboarding.entity.UserConsent;
import likelion.flourishing.domain.onboarding.repository.NotificationSettingRepository;
import likelion.flourishing.domain.onboarding.repository.UserConsentRepository;
import likelion.flourishing.global.config.OnboardingProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 온보딩 저장의 트랜잭션 단계만 담당한다.
 *
 * <p>별도 빈으로 둔 이유는 트랜잭션 경계 때문이다. 유니크 제약에 걸린 트랜잭션은 되돌아가야
 * 다시 읽을 수 있는데, 같은 클래스 안에서 자기 메서드를 부르면 프록시를 지나지 않아 새 트랜잭션이
 * 열리지 않는다. {@link OnboardingService}가 이 빈을 통해 불러야 재시도가 성립한다.
 *
 * <p>전파를 REQUIRES_NEW로 못 박은 이유도 같다. 프록시를 지나는 것과 새 트랜잭션이 열리는 것은
 * 별개이고, 기본값인 REQUIRED는 호출자에 트랜잭션이 없을 때만 새로 연다. 나중에 상위에
 * {@code @Transactional}이 붙으면 첫 시도의 제약 위반이 그 트랜잭션을 rollback-only로 표시해
 * 재시도가 죽은 트랜잭션에서 돌게 된다. 재시도가 이 빈의 존재 이유라 전파를 조건에 맡기지 않는다.
 */
@Component
public class OnboardingWriter {

    private final AuthService authService;
    private final UserConsentRepository userConsentRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final OnboardingProperties onboardingProperties;
    private final Clock clock;

    public OnboardingWriter(
            AuthService authService,
            UserConsentRepository userConsentRepository,
            NotificationSettingRepository notificationSettingRepository,
            OnboardingProperties onboardingProperties,
            Clock clock
    ) {
        this.authService = authService;
        this.userConsentRepository = userConsentRepository;
        this.notificationSettingRepository = notificationSettingRepository;
        this.onboardingProperties = onboardingProperties;
        this.clock = clock;
    }

    /**
     * 동의 이력과 알림 설정을 저장하고 가입을 완료 처리한다.
     *
     * <p>같은 동의 버전으로 다시 부르면 최초 동의 시각을 유지하고, 알림 설정은 항상 최신 요청으로
     * 덮어쓴다. 저장이 한 트랜잭션에서 함께 커밋되어야 동의 없이 가입만 완료된 상태가 남지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OnboardingResponse complete(AuthenticatedUser principal, OnboardingRequest request) {
        UUID userId = principal.userId();
        LocalDateTime now = LocalDateTime.now(clock);

        UserConsent consent = saveConsent(userId, ConsentType.SENSITIVE_DATA, request.consentVersion(), now);
        NotificationConsentResponse notificationConsent = saveNotificationConsent(userId, request, now);
        NotificationSetting notificationSetting = saveNotificationSetting(userId, request);
        LocalDateTime completedAt = authService.completeSignup(principal, now);

        return OnboardingResponse.from(consent, notificationSetting, notificationConsent, completedAt);
    }

    /**
     * 알림 수신 동의 2/2. 동의한 경우에만 이력을 남긴다.
     *
     * <p>동의하지 않은 요청에 행을 만들지 않는 이유는 두 가지다. 사용자가 하지 않은 동의를 서버가
     * 기록으로 남기지 않는다는 것이 하나고, DDL의 ck_user_consents_required_accepted가
     * accepted = TRUE만 허용한다는 것이 다른 하나다.
     *
     * <p>이전에 동의했다가 이번에 알림을 끈 경우 예전 행은 이력으로 남겨 두고 응답만 지금 선택을
     * 따른다. 동의 테이블은 시점별 이력이지 현재 상태가 아니다.
     */
    private NotificationConsentResponse saveNotificationConsent(
            UUID userId,
            OnboardingRequest request,
            LocalDateTime now
    ) {
        if (!Boolean.TRUE.equals(request.notificationConsent())) {
            return NotificationConsentResponse.notAgreed(onboardingProperties.notificationConsentVersion());
        }

        UserConsent consent = saveConsent(
                userId,
                ConsentType.NOTIFICATION,
                request.notificationConsentVersion(),
                now
        );
        return NotificationConsentResponse.agreed(consent);
    }

    /** 이미 같은 버전으로 남긴 동의가 있으면 최초 동의 시각을 유지한다. */
    private UserConsent saveConsent(
            UUID userId,
            ConsentType consentType,
            String consentVersion,
            LocalDateTime now
    ) {
        return userConsentRepository
                .findByUserIdAndConsentTypeAndConsentVersion(userId, consentType, consentVersion)
                .orElseGet(() -> userConsentRepository.saveAndFlush(
                        UserConsent.accept(userId, consentType, consentVersion, now)
                ));
    }

    private NotificationSetting saveNotificationSetting(UUID userId, OnboardingRequest request) {
        String notificationTime = notificationTimeOf(request);
        return notificationSettingRepository.findById(userId)
                .map(existing -> {
                    existing.update(request.notificationEnabled(), notificationTime, request.notificationPermission());
                    return existing;
                })
                .orElseGet(() -> notificationSettingRepository.saveAndFlush(NotificationSetting.create(
                        userId,
                        request.notificationEnabled(),
                        notificationTime,
                        request.notificationPermission()
                )));
    }

    /**
     * 저장할 알림 시각. 알림을 끈 요청은 피커 값을 함께 보냈더라도 기본 시각으로 저장한다.
     *
     * <p>다음 날 경과 입력 가능 시점을 이 값으로 계산하기 때문에 알림을 끈 사용자에게도 필요하다.
     *
     * <p>명세 {@code PUT /me/onboarding} 설명의 두 번째 갈래가 "알림을 받지 않을게요를 누른
     * 경우에도 서버는 notificationTime을 기본값 17:30으로 저장한다"고 적고 있어 그대로 따른다.
     * 스키마의 if/then은 {@code notificationEnabled: true} 갈래에만 걸려 있어
     * {@code enabled=false} + {@code time="21:00"} 조합도 검증을 통과한다. 그 값을 그대로
     * 저장하면 알림을 끈 사용자의 경과 입력이 21:00에 열려 명세와 갈리므로 여기서 떨어뜨린다.
     */
    private String notificationTimeOf(OnboardingRequest request) {
        if (!request.notificationEnabled() || request.notificationTime() == null) {
            return NotificationSetting.DEFAULT_TIME;
        }
        return request.notificationTime();
    }
}
