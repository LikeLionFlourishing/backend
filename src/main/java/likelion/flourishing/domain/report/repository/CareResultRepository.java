package likelion.flourishing.domain.report.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import likelion.flourishing.domain.report.entity.AiGenerationStatus;
import likelion.flourishing.domain.report.entity.CareResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CareResultRepository extends JpaRepository<CareResult, UUID> {

    Optional<CareResult> findByReportIdAndUserId(UUID reportId, UUID userId);

    /**
     * 재생성 기회를 쓰면서 새 설명을 반영한다. 성공하면 1, 이미 누가 썼으면 0을 돌려준다.
     *
     * <p>읽어서 확인하고 쓰는 방식으로는 한 번 제한을 지킬 수 없다. 두 요청이 겹치면 둘 다 확인을
     * 통과한다. {@code retry_used = FALSE}를 조건에 넣어 DB가 한 번만 통과시키게 하고, 영향 행 수로
     * 누가 이겼는지 판단한다.
     *
     * <p>영속성 컨텍스트를 비워서 이 갱신 뒤에 다시 읽는 값이 갱신된 행을 반영하게 한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update CareResult careResult
               set careResult.retryUsed = true,
                   careResult.aiGenerationStatus = :aiGenerationStatus,
                   careResult.summary = :summary,
                   careResult.generatedAt = :generatedAt
             where careResult.id = :careResultId
               and careResult.retryUsed = false
            """)
    int consumeRetry(
            @Param("careResultId") UUID careResultId,
            @Param("aiGenerationStatus") AiGenerationStatus aiGenerationStatus,
            @Param("summary") String summary,
            @Param("generatedAt") LocalDateTime generatedAt
    );
}
