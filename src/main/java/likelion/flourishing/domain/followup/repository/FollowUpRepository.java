package likelion.flourishing.domain.followup.repository;

import java.util.Optional;
import java.util.UUID;
import likelion.flourishing.domain.followup.entity.FollowUp;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * follow_ups 테이블 접근.
 *
 * <p>report_id에만 유니크 제약이 있지만 조회는 user_id까지 함께 건다. 남의 보고 번호를
 * 넣어도 결과가 비어 404가 되게 해서, 그 보고가 존재하는지조차 알 수 없게 하려는 것이다.
 */
public interface FollowUpRepository extends JpaRepository<FollowUp, UUID> {

    Optional<FollowUp> findByReportIdAndUserId(UUID reportId, UUID userId);
}
