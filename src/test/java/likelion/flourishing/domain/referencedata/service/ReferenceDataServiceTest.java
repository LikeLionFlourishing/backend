package likelion.flourishing.domain.referencedata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import likelion.flourishing.domain.referencedata.dto.response.OptionResponse;
import likelion.flourishing.domain.referencedata.dto.response.SkinReportOptionsResponse;
import likelion.flourishing.domain.report.entity.GuideSectionKey;
import likelion.flourishing.domain.report.service.GuideSectionFixtures;
import org.junit.jupiter.api.Test;

class ReferenceDataServiceTest {

    private final ReferenceDataService referenceDataService = new ReferenceDataService(GuideSectionFixtures.assembler());

    @Test
    void returnsVersionedSkinReportOptions() {
        SkinReportOptionsResponse response = referenceDataService.getSkinReportOptions();

        assertThat(response.getVersion()).isEqualTo("2026-08-16");
        assertThat(response.getAreas()).hasSize(13);
        assertThat(response.getAppearances()).hasSize(6);
        assertThat(response.getSensations()).hasSize(3);
        assertThat(response.getSituations()).hasSize(6);
        assertThat(response.getCareAvailability()).hasSize(4);
        assertThat(response.getPreCareChecks()).hasSize(4);
        // 결과가 없는 화면에서도 섹션 제목을 그릴 수 있어야 한다. 본문이 없으니 전부 비어 있다.
        assertThat(response.getGuideSections()).hasSize(GuideSectionKey.SECTION_COUNT);
        assertThat(response.getGuideSections())
                .allSatisfy(section -> assertThat(section.isEmpty()).isTrue());
    }

    @Test
    void returnsImmutableOptionLists() {
        SkinReportOptionsResponse response = referenceDataService.getSkinReportOptions();

        assertThatThrownBy(() -> response.getAreas().add(OptionResponse.of("NEW", "새 값")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
