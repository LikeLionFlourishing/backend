package likelion.flourishing.domain.onboarding.repository;

import java.util.UUID;
import likelion.flourishing.domain.onboarding.entity.NotificationSetting;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * notification_settings 테이블 접근. 사용자당 한 행이라 기본키가 user_id이고,
 * 그래서 별도 조회 메서드 없이 findById(userId)로 찾는다.
 *
 * <p>알림 설정 조회·변경(Notifications 태그)도 같은 테이블을 쓴다. 그쪽 담당자가 정해지면
 * 이 저장소를 공유할지 각자 둘지 맞춰야 한다.
 */
public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, UUID> {
}
