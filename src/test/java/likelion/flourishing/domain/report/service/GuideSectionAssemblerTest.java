package likelion.flourishing.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import likelion.flourishing.domain.report.dto.response.GuideSectionResponse;
import likelion.flourishing.domain.report.entity.GuideSectionKey;
import likelion.flourishing.domain.report.service.GuideSectionAssembler.GuideSectionContent;
import org.junit.jupiter.api.Test;

/**
 * 섹션은 본문이 비어도 사라지지 않고 빈 상태로 남는다. 여기서 확인하는 것은 여섯 개가 정해진
 * 순서로 나오는지와 어떤 본문이 비었을 때 어떤 섹션이 empty 로 표시되는지다.
 */
class GuideSectionAssemblerTest {

    private final GuideSectionAssembler assembler = GuideSectionFixtures.assembler();

    @Test
    void sectionsFollowTheOrderFixedByTheRuleTable() {
        List<GuideSectionResponse> sections = assembler.assembleDefaults();

        assertThat(sections).hasSize(GuideSectionKey.SECTION_COUNT);
        assertThat(sections).extracting(GuideSectionResponse::getKey).containsExactly(
                GuideSectionKey.CURRENT_SUMMARY,
                GuideSectionKey.DO_TODAY,
                GuideSectionKey.AVOID_TODAY,
                GuideSectionKey.SIMILAR_EXPERIENCE,
                GuideSectionKey.CHECK_NEXT,
                GuideSectionKey.RECOMMENDED_INGREDIENTS
        );
    }

    @Test
    void everySectionIsEmptyWhenThereIsNoResultYet() {
        // 기준정보 응답이 쓰는 경로다. 결과가 없어도 화면을 그릴 수 있게 제목·설명은 준다.
        assertThat(assembler.assembleDefaults())
                .allSatisfy(section -> assertThat(section.isEmpty()).isTrue());
        assertThat(assembler.assembleDefaults())
                .allSatisfy(section -> assertThat(section.getTitle()).isNotBlank());
    }

    @Test
    void eachSectionTracksItsOwnBodyField() {
        List<GuideSectionResponse> sections = assembler.assemble(new GuideSectionContent(
                "오늘 상태 요약",
                List.of("미지근한 물로 세안하기"),
                List.of(),
                List.of("붉은 기가 번지는지"),
                List.of(),
                true
        ));

        assertThat(emptyOf(sections, GuideSectionKey.CURRENT_SUMMARY)).isFalse();
        assertThat(emptyOf(sections, GuideSectionKey.DO_TODAY)).isFalse();
        assertThat(emptyOf(sections, GuideSectionKey.AVOID_TODAY)).isTrue();
        assertThat(emptyOf(sections, GuideSectionKey.SIMILAR_EXPERIENCE)).isFalse();
        assertThat(emptyOf(sections, GuideSectionKey.CHECK_NEXT)).isFalse();
        assertThat(emptyOf(sections, GuideSectionKey.RECOMMENDED_INGREDIENTS)).isTrue();
    }

    @Test
    void blankSummaryCountsAsEmpty() {
        List<GuideSectionResponse> sections = assembler.assemble(new GuideSectionContent(
                "   ", List.of(), List.of(), List.of(), List.of(), false
        ));

        assertThat(emptyOf(sections, GuideSectionKey.CURRENT_SUMMARY)).isTrue();
    }

    private boolean emptyOf(List<GuideSectionResponse> sections, GuideSectionKey key) {
        return sections.stream()
                .filter(section -> section.getKey() == key)
                .findFirst()
                .orElseThrow()
                .isEmpty();
    }
}
