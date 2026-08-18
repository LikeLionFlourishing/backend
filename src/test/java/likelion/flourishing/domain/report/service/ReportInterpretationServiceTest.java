package likelion.flourishing.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.report.ai.AiFailureCode;
import likelion.flourishing.domain.report.ai.ExtractedSelections;
import likelion.flourishing.domain.report.ai.OpenAiProperties;
import likelion.flourishing.domain.report.ai.SkinReportStructuringPort;
import likelion.flourishing.domain.report.ai.StructuringOutcome;
import likelion.flourishing.domain.report.dto.request.ManualSelectionsRequest;
import likelion.flourishing.domain.report.dto.request.ReportInterpretationRequest;
import likelion.flourishing.domain.report.dto.response.InterpretationFailureCode;
import likelion.flourishing.domain.report.dto.response.MissingField;
import likelion.flourishing.domain.report.dto.response.ProcessingStatus;
import likelion.flourishing.domain.report.dto.response.ReportInterpretationResponse;
import likelion.flourishing.domain.report.entity.Appearance;
import likelion.flourishing.domain.report.entity.BodyArea;
import likelion.flourishing.domain.report.entity.CareAvailability;
import likelion.flourishing.domain.report.entity.Sensation;
import likelion.flourishing.domain.report.entity.Situation;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import likelion.flourishing.global.exception.TooManyRequestsException;
import likelion.flourishing.support.RateLimitResult;
import likelion.flourishing.support.RateLimiter;
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

    @Mock
    private SkinReportStructuringPort structuringPort;

    @Mock
    private SensitiveDataConsentGuard consentGuard;

    @Mock
    private RateLimiter rateLimiter;

    private ReportInterpretationService service;

    @BeforeEach
    void setUp() {
        service = new ReportInterpretationService(
                structuringPort,
                consentGuard,
                rateLimiter,
                new OpenAiProperties(null, null, null, null, null, 0, null)
        );
        when(rateLimiter.consume(any(), any(), anyInt(), any()))
                .thenReturn(new RateLimitResult(true, 30, 29, 3600, 0L));
    }

    @Test
    void exceedingTheHourlyLimitStopsTheModelCall() {
        when(rateLimiter.consume(any(), any(), anyInt(), any()))
                .thenReturn(new RateLimitResult(false, 30, 0, 600, 0L));

        assertThatThrownBy(() -> service.interpret(
                principal(), new ReportInterpretationRequest("턱이 빨개요.", null)
        ))
                .isInstanceOf(TooManyRequestsException.class);
        verify(structuringPort, never()).structure(anyString());
    }

    @Test
    void manualSelectionWinsOverExtractedValue() {
        stubExtracted(new ExtractedSelections(
                BodyArea.NOSE,
                Set.of(Appearance.APP_OTHER),
                Set.of(Sensation.REDNESS),
                Set.of(Situation.NEW_PRODUCT),
                CareAvailability.ADDITIONAL_CARE_DIFFICULT
        ));
        ManualSelectionsRequest manual = new ManualSelectionsRequest(
                BodyArea.RIGHT_CHIN,
                "왼쪽 목에도 조금 있어요",
                List.of(Appearance.APP_REDNESS),
                null,
                null,
                null
        );

        ReportInterpretationResponse response = service.interpret(
                principal(), new ReportInterpretationRequest("오른쪽 턱이 빨개요.", manual)
        );

        assertThat(response.getProcessingStatus()).isEqualTo(ProcessingStatus.SUCCESS);
        assertThat(response.getProposed().getPrimaryArea()).isEqualTo(BodyArea.RIGHT_CHIN);
        assertThat(response.getProposed().getAppearances()).containsExactly(Appearance.APP_REDNESS);
        assertThat(response.getProposed().getOtherAreasNote()).isEqualTo("왼쪽 목에도 조금 있어요");
        assertThat(response.getProposed().getSensations()).containsExactly(Sensation.REDNESS);
        assertThat(response.getMissingFields()).isEmpty();
        assertThat(response.getAmbiguities()).isEmpty();
    }

    /** 사용자가 일부러 뺀 값이 AI 값으로 되살아나면 안 된다. */
    @Test
    void manualListReplacesExtractedListInsteadOfMerging() {
        stubExtracted(new ExtractedSelections(
                null,
                Set.of(Appearance.APP_REDNESS, Appearance.APP_PUS_BUMP),
                Set.of(),
                Set.of(),
                null
        ));
        ManualSelectionsRequest manual = new ManualSelectionsRequest(
                null, null, List.of(Appearance.APP_REDNESS), null, null, null
        );

        ReportInterpretationResponse response = service.interpret(
                principal(), new ReportInterpretationRequest("턱이 빨개요.", manual)
        );

        assertThat(response.getProposed().getAppearances()).containsExactly(Appearance.APP_REDNESS);
    }

    @Test
    void failedStructuringStillReturnsManualSelections() {
        when(structuringPort.structure(anyString()))
                .thenReturn(StructuringOutcome.failed(AiFailureCode.AI_TIMEOUT));
        ManualSelectionsRequest manual = new ManualSelectionsRequest(
                BodyArea.NECK, null, List.of(Appearance.APP_OTHER), null, null, null
        );

        ReportInterpretationResponse response = service.interpret(
                principal(), new ReportInterpretationRequest("목이 이상해요.", manual)
        );

        assertThat(response.getProcessingStatus()).isEqualTo(ProcessingStatus.FAILED);
        assertThat(response.getFailureCode()).isEqualTo(InterpretationFailureCode.AI_TIMEOUT);
        assertThat(response.getProposed().getPrimaryArea()).isEqualTo(BodyArea.NECK);
        assertThat(response.getProposed().getAppearances()).containsExactly(Appearance.APP_OTHER);
        assertThat(response.getMissingFields())
                .containsExactly(
                        MissingField.SENSATIONS,
                        MissingField.SITUATIONS,
                        MissingField.CARE_AVAILABILITY
                );
    }

    @Test
    void emptyManualSelectionsAreAllowed() {
        stubExtracted(ExtractedSelections.empty());

        ReportInterpretationResponse response = service.interpret(
                principal(), new ReportInterpretationRequest("잘 모르겠어요.", null)
        );

        assertThat(response.getProcessingStatus()).isEqualTo(ProcessingStatus.SUCCESS);
        assertThat(response.getProposed().getPrimaryArea()).isNull();
        assertThat(response.getMissingFields())
                .containsExactly(
                        MissingField.PRIMARY_AREA,
                        MissingField.APPEARANCES,
                        MissingField.SENSATIONS,
                        MissingField.SITUATIONS,
                        MissingField.CARE_AVAILABILITY
                );
    }

    /** 단독 선택 위반이 남은 그룹은 직전 상황뿐이다. 겉모습·불편은 어떤 조합이든 통과한다. */
    @Test
    void invalidManualCombinationIsRejectedBeforeCallingTheModel() {
        ManualSelectionsRequest manual = new ManualSelectionsRequest(
                null, null, null, null,
                List.of(Situation.NONE_RECALLED, Situation.SHAVING), null
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
