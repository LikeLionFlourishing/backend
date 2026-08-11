package likelion.flourishing.domain.auth.repository;

import java.util.Optional;
import java.util.UUID;
import likelion.flourishing.domain.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByNormalizedEmail(String normalizedEmail);

    boolean existsByNormalizedEmail(String normalizedEmail);
}
