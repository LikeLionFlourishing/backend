package likelion.flourishing.domain.notification.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import likelion.flourishing.domain.notification.entity.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * push_subscriptions 테이블 접근.
 *
 * <p>조회 조건에 항상 userId를 함께 넣는다. 다른 사용자의 구독 번호를 넣어도 결과가 없어야
 * 소유권 검증이 SQL 단계에서 끝난다.
 */
public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {

    /** 같은 endpoint 재등록을 갱신으로 처리하기 위한 조회. 테이블의 유니크 제약과 같은 조합이다. */
    Optional<PushSubscription> findByUserIdAndEndpointFingerprint(UUID userId, byte[] endpointFingerprint);

    Optional<PushSubscription> findByIdAndUserId(UUID id, UUID userId);

    List<PushSubscription> findAllByUserIdAndActiveIsTrue(UUID userId);

    long countByUserIdAndActiveIsTrue(UUID userId);
}
