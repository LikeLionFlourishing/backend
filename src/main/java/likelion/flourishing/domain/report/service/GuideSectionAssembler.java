package likelion.flourishing.domain.report.service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import likelion.flourishing.domain.report.dto.response.GuideSectionResponse;
import likelion.flourishing.domain.report.entity.GuideSectionCopy;
import likelion.flourishing.domain.report.entity.GuideSectionKey;
import likelion.flourishing.domain.report.repository.GuideSectionCopyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결과 카드에 붙는 가이드 섹션을 만든다.
 *
 * <p>제목과 설명은 guide_sections 테이블에서 읽는다. 프론트가 문구를 하드코딩하지 않게 하려는
 * 명세 요구를 지키면서, 배포 없이 문구를 다듬을 수 있게 하기 위해서다.
 *
 * <p>각 섹션이 어느 본문을 가리키는지는 명세가 표로 고정해 두었다. 본문이 비면 섹션을 지우지 않고
 * {@code empty = true}로 표시한다. 섹션이 사라지면 화면 구성이 결과마다 달라진다.
 */
@Component
public class GuideSectionAssembler {

    private static final Logger log = LoggerFactory.getLogger(GuideSectionAssembler.class);

    private final GuideSectionCopyRepository guideSectionCopyRepository;

    public GuideSectionAssembler(GuideSectionCopyRepository guideSectionCopyRepository) {
        this.guideSectionCopyRepository = guideSectionCopyRepository;
    }

    /**
     * 결과 내용에 맞춰 여섯 섹션을 만든다.
     *
     * <p>문구 행이 없는 섹션은 건너뛴다. 제목 없이 빈 칸만 그리게 하는 것보다 낫고, 규칙표에
     * 행을 채우면 곧바로 나타난다. 로그로 남겨 누락을 알아차릴 수 있게 한다.
     */
    @Transactional(readOnly = true)
    public List<GuideSectionResponse> assemble(GuideSectionContent content) {
        List<GuideSectionCopy> copies = guideSectionCopyRepository.findAllByOrderByDisplayOrderAsc();
        if (copies.size() < GuideSectionKey.SECTION_COUNT) {
            log.warn(
                    "가이드 섹션 문구가 모자랍니다. 있음={} 필요={}",
                    copies.size(),
                    GuideSectionKey.SECTION_COUNT
            );
        }

        Map<GuideSectionKey, Boolean> emptyByKey = emptyFlags(content);
        List<GuideSectionResponse> sections = new ArrayList<>();
        for (GuideSectionCopy copy : copies) {
            sections.add(GuideSectionResponse.of(
                    copy.getSectionKey(),
                    copy.getTitle(),
                    copy.getDescription(),
                    emptyByKey.getOrDefault(copy.getSectionKey(), true)
            ));
        }
        return List.copyOf(sections);
    }

    /** 결과가 없는 화면(기준정보 응답)에서 쓰는 기본 섹션. 본문이 없으므로 전부 비어 있다. */
    @Transactional(readOnly = true)
    public List<GuideSectionResponse> assembleDefaults() {
        return assemble(GuideSectionContent.empty());
    }

    private Map<GuideSectionKey, Boolean> emptyFlags(GuideSectionContent content) {
        Map<GuideSectionKey, Boolean> flags = new EnumMap<>(GuideSectionKey.class);
        flags.put(GuideSectionKey.CURRENT_SUMMARY, isBlank(content.summary()));
        flags.put(GuideSectionKey.DO_TODAY, content.doToday().isEmpty());
        flags.put(GuideSectionKey.AVOID_TODAY, content.avoidToday().isEmpty());
        flags.put(GuideSectionKey.SIMILAR_EXPERIENCE, !content.hasSimilarExperience());
        flags.put(GuideSectionKey.CHECK_NEXT, content.checkNext().isEmpty());
        flags.put(GuideSectionKey.RECOMMENDED_INGREDIENTS, content.recommendedIngredients().isEmpty());
        return flags;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 섹션의 빈 여부를 정하는 결과 본문.
     *
     * <p>응답 DTO를 그대로 받지 않는 이유는, 보고 생성과 기록 조회가 서로 다른 타입으로 같은
     * 내용을 들고 있어서다. 필요한 것만 여기로 모으면 두 경로가 같은 판단을 쓴다.
     */
    public record GuideSectionContent(
            String summary,
            List<String> doToday,
            List<String> avoidToday,
            List<String> checkNext,
            List<?> recommendedIngredients,
            boolean hasSimilarExperience
    ) {
        public GuideSectionContent {
            doToday = List.copyOf(doToday);
            avoidToday = List.copyOf(avoidToday);
            checkNext = List.copyOf(checkNext);
            recommendedIngredients = List.copyOf(recommendedIngredients);
        }

        public static GuideSectionContent empty() {
            return new GuideSectionContent(null, List.of(), List.of(), List.of(), List.of(), false);
        }
    }
}
