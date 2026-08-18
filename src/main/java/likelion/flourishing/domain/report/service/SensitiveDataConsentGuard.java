package likelion.flourishing.domain.report.service;

import java.util.UUID;
import likelion.flourishing.domain.onboarding.entity.ConsentType;
import likelion.flourishing.domain.onboarding.repository.UserConsentRepository;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 민감정보 동의를 확인한다.
 *
 * <p>피부 상태는 민감정보다. 동의 없이 저장하거나 외부 모델에 보내면 안 되므로 보고 관련 요청은
 * 모두 이 확인을 먼저 통과해야 한다. 로그인만 되어 있고 온보딩을 마치지 않은 상태가 여기 걸린다.
 */
@Component
public class SensitiveDataConsentGuard {

    private final UserConsentRepository userConsentRepository;

    public SensitiveDataConsentGuard(UserConsentRepository userConsentRepository) {
        this.userConsentRepository = userConsentRepository;
    }

    @Transactional(readOnly = true)
    public void assertConsented(UUID userId) {
        if (!userConsentRepository.existsByUserIdAndConsentType(userId, ConsentType.SENSITIVE_DATA)) {
            throw new BusinessException(ErrorCode.CONSENT_REQUIRED);
        }
    }
}
