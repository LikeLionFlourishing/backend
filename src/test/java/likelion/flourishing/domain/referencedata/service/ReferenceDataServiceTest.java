package likelion.flourishing.domain.referencedata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import likelion.flourishing.domain.referencedata.dto.response.OptionResponse;
import likelion.flourishing.domain.referencedata.dto.response.SkinReportOptionsResponse;
import org.junit.jupiter.api.Test;

class ReferenceDataServiceTest {

    private final ReferenceDataService referenceDataService = new ReferenceDataService();

    @Test
    void returnsVersionedSkinReportOptions() {
        SkinReportOptionsResponse response = referenceDataService.getSkinReportOptions();

        assertThat(response.getVersion()).isEqualTo("2026-08-09");
        assertThat(response.getAreas()).hasSize(13);
        assertThat(response.getAppearances()).hasSize(8);
        assertThat(response.getSensations()).hasSize(7);
        assertThat(response.getSituations()).hasSize(9);
        assertThat(response.getCareAvailability()).hasSize(4);
        assertThat(response.getPreCareChecks()).hasSize(4);
    }

    @Test
    void returnsImmutableOptionLists() {
        SkinReportOptionsResponse response = referenceDataService.getSkinReportOptions();

        assertThatThrownBy(() -> response.getAreas().add(OptionResponse.of("NEW", "새 값")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
