package likelion.flourishing.domain.onboarding.service;

import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.onboarding.dto.request.OnboardingRequest;
import likelion.flourishing.domain.onboarding.dto.response.OnboardingResponse;
import likelion.flourishing.global.config.OnboardingProperties;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** 최신 필수 동의와 알림 선택을 저장하고 가입을 완료 처리한다. */
@Service
public class OnboardingService {

    private final OnboardingProperties onboardingProperties;
    private final OnboardingWriter onboardingWriter;

    public OnboardingService(OnboardingProperties onboardingProperties, OnboardingWriter onboardingWriter) {
        this.onboardingProperties = onboardingProperties;
        this.onboardingWriter = onboardingWriter;
    }

    /**
     * 온보딩을 완료한다. PUT이라 여러 번 불러도 같은 결과를 남긴다.
     *
     * <p>동의 버전은 서버가 들고 있는 활성 버전과 같을 때만 받는다. 클라이언트가 보낸 문자열을
     * 그대로 저장하면 서버가 알지 못하는 버전이 동의 증빙으로 남는다.
     *
     * <p>트랜잭션을 여기 걸지 않는 이유는 재시도 때문이다. 아래 설명대로 유니크 제약에 걸린
     * 트랜잭션은 되돌아간 뒤에야 다시 읽을 수 있다.
     */
    public OnboardingResponse complete(AuthenticatedUser principal, OnboardingRequest request) {
        if (!onboardingProperties.isActive(request.consentVersion())) {
            throw new BusinessException(ErrorCode.CONSENT_VERSION_NOT_ACCEPTED);
        }

        try {
            return onboardingWriter.complete(principal, request);
        } catch (DataIntegrityViolationException raced) {
            // 같은 사용자의 첫 PUT이 겹치면 둘 다 "저장된 것이 없다"를 보고 각자 넣으려 한다.
            // 뒤늦은 쪽이 uq_user_consents_version이나 notification_settings 기본 키에 걸리는데,
            // 이때 이미 먼저 저장된 값이 있으므로 트랜잭션을 새로 열어 다시 읽으면 같은 응답이 나간다.
            // 재시도는 한 번뿐이다. 두 번째도 실패하면 제약 위반의 원인이 경합이 아니라는 뜻이다.
            return onboardingWriter.complete(principal, request);
        }
    }
}
