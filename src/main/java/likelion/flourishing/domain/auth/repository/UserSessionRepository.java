package likelion.flourishing.domain.auth.repository;

import java.util.Optional;
import java.util.UUID;
import likelion.flourishing.domain.auth.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    Optional<UserSession> findBySessionTokenHash(byte[] sessionTokenHash);
}
