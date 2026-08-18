package likelion.flourishing.domain.report.idempotency;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

    Optional<IdempotencyRecord> findByUserIdAndOperationIdAndIdempotencyKey(
            UUID userId,
            String operationId,
            UUID idempotencyKey
    );
}
