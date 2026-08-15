package likelion.flourishing.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.report.ai.AiFailureCode;
import likelion.flourishing.domain.report.ai.ExtractedSelections;
import likelion.flourishing.domain.report.ai.SkinReportStructuringPort;
import likelion.flourishing.domain.report.ai.StructuringOutcome;
import likelion.flourishing.domain.report.dto.request.ManualSelectionsRequest;
import likelion.flourishing.domain.report.dto.request.ReportInterpretationRequest;
import likelion.flourishing.domain.report.dto.response.FieldSource;
import likelion.flourishing.domain.report.dto.response.ProcessingStatus;
import likelion.flourishing.domain.report.dto.response.ReportInterpretationResponse;
import likelion.flourishing.domain.report.entity.Appearance;
import likelion.flourishing.domain.report.entity.BodyArea;
import likelion.flourishing.domain.report.entity.CareAvailability;
import likelion.flourishing.domain.report.entity.Sensation;
import likelion.flourishing.domain.report.entity.Situation;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 구조화 서비스 테스트.
 *
 * <p>확인하는 것: 사용자가 고른 값이 AI 값을 밀어내는지, AI가 실패해도 200으로 수동 선택값을
 * 돌려주는지, 어느 값이 어디서 왔는지 알리는지, 동의가 없으면 원문을 모델에 보내지 않는지.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportInterpretationServiceTest {

    private static final UUID USER_ID = UUID.fromString("2c56fe08-ea1f-45fc-915d-c35b7c0bca39");
    private static final UUID SESSION_ID = UUID.fromString("5ecb88d8-6a21-4a54-8967-72599f078963");
    private static final Instant NOW = Instant.parse("2026-08-15T03:00:00Z");

    @Mock
    private SkinReportStructuringPort structuringPort;

    @Mock
    private SensitiveDataConsentGuard consentGuard;

    private ReportInterpretationService service;

    @BeforeEach
    void setUp() {
        service = new ReportInterpretationService(
                structuringPort, consentGuard, Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void manualSelectionWinsOverExtractedValue() {
        stubExtracted(new ExtractedSelections(
                BodyArea.NOSE,
                Set.of(Appearance.CRUST),
                Set.of(Sensation.HEAT),
                Set.of(Situation.NEW_PRODUCT),
                CareAvailability.ADDITIONAL_CARE_DIFFICULT
        ));
        ManualSelectionsRequest manual = new ManualSelectionsRequest(
                BodyArea.RIGHT_CHIN,
                "왼쪽 목에도 조금 있어요",
                List.of(Appearance.REDNESS),
                null,
                null,
                null
        );

        ReportInterpretationResponse response = service.interpret(
                principal(), new ReportInterpretationRequest("오른쪽 턱이 빨개요.", manual)
        );

        assertThat(response.getProcessingStatus()).isEqualTo(ProcessingStatus.SUCCEEDED);
        assertThat(response.getStructured().getPrimaryArea()).isEqualTo(BodyArea.RIGHT_CHIN);
        assertThat(response.getStructured().getAppearances()).containsExactly(Appearance.REDNESS);
        assertThat(response.getStructured().getOtherAreasNote()).isEqualTo("왼쪽 목에도 조금 있어요");
        assertThat(response.getFieldSources())
                .containsEntry("primaryArea", FieldSource.MANUAL)
                .containsEntry("appearances", FieldSource.MANUAL)
                .containsEntry("otherAreasNote", FieldSource.MANUAL)
                .containsEntry("sensations", FieldSource.AI)
                .containsEntry("situations", FieldSource.AI)
                .containsEntry("careAvailability", FieldSource.AI);
        assertThat(response.getStructured().getSensations()).containsExactly(Sensation.HEAT);
        assertThat(response.getInterpretedAt())
                .isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).atOffset(ZoneOffset.UTC));
    }

    /** 사용자가 일부러 뺀 값이 AI 값으로 되살아나면 안 된다. */
    @Test
    void manualListReplacesExtractedListInsteadOfMerging() {
        stubExtracted(new ExtractedSelections(
                null,
                Set.of(Appearance.REDNESS, Appearance.OOZING),
                Set.of(),
                Set.of(),
                null
        ));
        ManualSelectionsRequest manual = new ManualSelectionsRequest(
                null, null, List.of(Appearance.REDNESS), null, null, null
        );

        ReportInterpretationResponse response = service.interpret(
                principal(), new ReportInterpretationRequest("턱이 빨개요.", manual)
        );

        assertThat(response.getStructured().getAppearances()).containsExactly(Appearance.REDNESS);
    }

    @Test
    void failedStructuringStillReturnsManualSelections() {
        when(structuringPort.structure(anyString()))
                .thenReturn(StructuringOutcome.failed(AiFailureCode.AI_TIMEOUT));
        ManualSelectionsRequest manual = new ManualSelectionsRequest(
                BodyArea.NECK, null, List.of(Appearance.UNSURE), null, null, null
        );

        ReportInterpretationResponse response = service.interpret(
                principal(), new ReportInterpretationRequest("목이 이상해요.", manual)
        );

        assertThat(response.getProcessingStatus()).isEqualTo(ProcessingStatus.FAILED);
        assertThat(response.getFailureCode()).isEqualTo(AiFailureCode.AI_TIMEOUT);
        assertThat(response.getStructured().getPrimaryArea()).isEqualTo(BodyArea.NECK);
        assertThat(response.getStructured().getAppearances()).containsExactly(Appearance.UNSURE);
        assertThat(response.getFieldSources())
                .containsEntry("primaryArea", FieldSource.MANUAL)
                .containsEntry("sensations", FieldSource.NONE);
    }

    @Test
    void emptyManualSelectionsAreAllowed() {
        stubExtracted(ExtractedSelections.empty());

        ReportInterpretationResponse response = service.interpret(
                principal(), new ReportInterpretationRequest("잘 모르겠어요.", null)
        );

        assertThat(response.getProcessingStatus()).isEqualTo(ProcessingStatus.SUCCEEDED);
        assertThat(response.getStructured().getPrimaryArea()).isNull();
        assertThat(response.getFieldSources())
                .containsEntry("primaryArea", FieldSource.NONE)
                .containsEntry("otherAreasNote", FieldSource.NONE);
    }

    @Test
    void invalidManualCombinationIsRejectedBeforeCallingTheModel() {
        ManualSelectionsRequest manual = new ManualSelectionsRequest(
                null, null, List.of(Appearance.UNSURE, Appearance.REDNESS), null, null, null
        );

        assertThatThrownBy(() -> service.interpret(
                principal(), new ReportInterpretationRequest("턱이요.", manual)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SELECTION_COMBINATION_INVALID);
        verify(structuringPort, never()).structure(anyString());
    }

    @Test
    void missingConsentStopsTheRequestBeforeTheModelCall() {
        doThrow(new BusinessException(ErrorCode.CONSENT_REQUIRED)).when(consentGuard).assertConsented(USER_ID);

        assertThatThrownBy(() -> service.interpret(
                principal(), new ReportInterpretationRequest("턱이 빨개요.", null)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CONSENT_REQUIRED);
        verify(structuringPort, never()).structure(anyString());
    }

    private void stubExtracted(ExtractedSelections extracted) {
        when(structuringPort.structure(anyString())).thenReturn(StructuringOutcome.succeeded(extracted));
    }

    private AuthenticatedUser principal() {
        return new AuthenticatedUser(
                USER_ID, SESSION_ID, LocalDateTime.of(2026, 8, 24, 0, 0), "csrf-token-value-that-is-long-enough"
        );
    }
}
