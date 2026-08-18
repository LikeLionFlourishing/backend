package likelion.flourishing.domain.auth.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import likelion.flourishing.domain.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * users 테이블 접근. 조회는 원문 email이 아니라 normalized_email로만 한다.
 *
 * <p>normalized_email은 대소문자와 앞뒤 공백을 없앤 값이고 유니크 제약이 걸려 있다.
 * 원문으로 찾으면 Soldier@x.com과 soldier@x.com이 다른 사람이 되어 중복 가입이 뚫린다.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByNormalizedEmail(String normalizedEmail);

    boolean existsByNormalizedEmail(String normalizedEmail);

    /**
     * 행에 쓰기 잠금을 걸고 읽는다(select ... for update). 읽은 값을 보고 고칠지 정하는 자리에서
     * 쓰며, 잠금을 쥔 트랜잭션이 끝날 때까지 다른 트랜잭션은 같은 행을 읽지 못한다.
     *
     * <p>그냥 findById로 읽으면 두 요청이 모두 고치기 전 상태를 읽고 각자 쓰기 때문에,
     * 나중에 커밋한 쪽이 먼저 쓴 값을 덮어쓴다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);
}
