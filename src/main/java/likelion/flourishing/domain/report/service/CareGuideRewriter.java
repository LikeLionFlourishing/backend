package likelion.flourishing.domain.report.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import likelion.flourishing.domain.report.entity.AiGenerationStatus;
import likelion.flourishing.domain.report.entity.CareResult;
import likelion.flourishing.domain.report.entity.CareResultItem;
import likelion.flourishing.domain.report.repository.CareResultItemRepository;
import likelion.flourishing.domain.report.repository.CareResultRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다시 만든 관리 설명을 반영하는 쓰기 단계.
 *
 * <p>별도 빈으로 둔 이유는 트랜잭션 경계 때문이다. 같은 클래스 안에서 부르면 프록시를 지나지 않아
 * {@code @Transactional}이 걸리지 않는다. AI 호출을 밖에서 끝내고 여기서 저장만 하면 트랜잭션이
 * 외부 응답을 기다리지 않는다.
 *
 * <p>재생성 기회를 조건부 갱신으로 가져간다. 읽어서 확인하고 쓰는 방식은 두 요청이 겹칠 때 둘 다
 * 통과해 AI를 두 번 부르고 항목 삽입이 서로 엉킨다. DB가 한 번만 통과시키게 하고 못 가져간 쪽은
 * 아무것도 바꾸지 않는다.
 */
@Component
public class CareGuideRewriter {

    private final CareResultRepository careResultRepository;
    private final CareResultItemRepository careResultItemRepository;

    public CareGuideRewriter(
            CareResultRepository careResultRepository,
            CareResultItemRepository careResultItemRepository
    ) {
        this.careResultRepository = careResultRepository;
        this.careResultItemRepository = careResultItemRepository;
    }

    /**
     * 재생성 기회를 가져가고 결과를 갱신한다.
     *
     * @param items 성공했을 때 새로 넣을 항목. 실패했으면 비어 있고 저장된 항목을 그대로 둔다.
     * @return 갱신된 결과. 이미 다른 요청이 기회를 썼으면 빈 값이다.
     */
    @Transactional
    public Optional<CareResult> rewrite(
            UUID careResultId,
            AiGenerationStatus aiGenerationStatus,
            String summary,
            LocalDateTime generatedAt,
            List<PlannedCareItem> items
    ) {
        int claimed = careResultRepository.consumeRetry(
                careResultId, aiGenerationStatus, summary, generatedAt
        );
        if (claimed == 0) {
            return Optional.empty();
        }

        if (!items.isEmpty()) {
            // (결과, 유형, 순서) 유니크 제약이 있어 덮어쓸 수 없다. 비운 뒤 넣는다.
            careResultItemRepository.deleteAllByCareResultId(careResultId);
            careResultItemRepository.flush();
            careResultItemRepository.saveAll(items.stream()
                    .map(item -> CareResultItem.snapshot(
                            careResultId,
                            item.sourceRuleActionId(),
                            item.itemType(),
                            item.content(),
                            item.displayOrder()
                    ))
                    .toList());
        }
        return careResultRepository.findById(careResultId);
    }
}
