package likelion.flourishing.domain.onboarding.repository;

import java.util.Optional;
import java.util.UUID;
import likelion.flourishing.domain.onboarding.entity.ConsentType;
import likelion.flourishing.domain.onboarding.entity.UserConsent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserConsentRepository extends JpaRepository<UserConsent, UUID> {

    Optional<UserConsent> findByUserIdAndConsentTypeAndConsentVersion(
            UUID userId,
            ConsentType consentType,
            String consentVersion
    );
}
