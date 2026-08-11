package likelion.flourishing.domain.auth.repository;

import java.util.Optional;
import java.util.UUID;
import likelion.flourishing.domain.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * users 테이블 접근. 조회는 원문 email이 아니라 normalized_email로만 한다.
 *
 * <p>normalized_email은 대소문자와 앞뒤 공백을 없앤 값이고 유니크 제약이 걸려 있다.
 * 원문으로 찾으면 Soldier@x.com과 soldier@x.com이 다른 사람이 되어 중복 가입이 뚫린다.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByNormalizedEmail(String normalizedEmail);

    boolean existsByNormalizedEmail(String normalizedEmail);
}
