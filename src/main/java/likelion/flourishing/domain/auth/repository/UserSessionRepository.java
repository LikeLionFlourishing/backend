package likelion.flourishing.domain.auth.repository;

import java.util.Optional;
import java.util.UUID;
import likelion.flourishing.domain.auth.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * user_sessions 테이블 접근. 쿠키로 받은 세션 토큰의 SHA-256 해시로 세션을 찾는다.
 *
 * <p>토큰 원문은 저장하지 않으므로 조회 조건도 해시다. DB가 유출되어도 저장된 값만으로는
 * 쿠키를 만들어 낼 수 없다.
 */
public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    Optional<UserSession> findBySessionTokenHash(byte[] sessionTokenHash);
}
