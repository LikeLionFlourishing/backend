package likelion.flourishing.domain.onboarding.repository;

import java.util.UUID;
import likelion.flourishing.domain.onboarding.entity.NotificationSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, UUID> {
}
