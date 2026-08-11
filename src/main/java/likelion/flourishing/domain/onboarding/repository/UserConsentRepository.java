package likelion.flourishing.domain.onboarding.repository;

import java.util.Optional;
import java.util.UUID;
import likelion.flourishing.domain.onboarding.entity.ConsentType;
import likelion.flourishing.domain.onboarding.entity.UserConsent;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * user_consents 테이블 접근. 동의는 수정하지 않고 버전마다 한 행씩 쌓는 이력이다.
 *
 * <p>조회 조건이 (사용자, 동의 종류, 동의 버전) 세 개인 이유는 테이블의 유니크 제약과 같은
 * 조합이기 때문이다. 온보딩을 다시 호출했을 때 이미 남긴 동의인지 판단해 최초 동의 시각을
 * 유지하는 데 쓴다.
 */
public interface UserConsentRepository extends JpaRepository<UserConsent, UUID> {

    Optional<UserConsent> findByUserIdAndConsentTypeAndConsentVersion(
            UUID userId,
            ConsentType consentType,
            String consentVersion
    );
}
