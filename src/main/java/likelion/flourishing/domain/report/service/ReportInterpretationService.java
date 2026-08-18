package likelion.flourishing.domain.report.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.report.ai.ExtractedSelections;
import likelion.flourishing.domain.report.ai.OpenAiProperties;
import likelion.flourishing.domain.report.ai.SkinReportStructuringPort;
import likelion.flourishing.domain.report.ai.StructuringOutcome;
import likelion.flourishing.domain.report.dto.request.ManualSelectionsRequest;
import likelion.flourishing.domain.report.dto.request.ReportInterpretationRequest;
import likelion.flourishing.domain.report.dto.response.AmbiguityResponse;
import likelion.flourishing.domain.report.dto.response.InterpretationFailureCode;
import likelion.flourishing.domain.report.dto.response.MissingField;
import likelion.flourishing.domain.report.dto.response.ReportInterpretationResponse;
import likelion.flourishing.domain.report.dto.response.StructuredSelectionsResponse;
import likelion.flourishing.domain.report.entity.Appearance;
import likelion.flourishing.domain.report.entity.BodyArea;
import likelion.flourishing.domain.report.entity.CareAvailability;
import likelion.flourishing.domain.report.entity.Sensation;
import likelion.flourishing.domain.report.entity.Situation;
import likelion.flourishing.global.exception.ProblemFactory;
import likelion.flourishing.global.exception.TooManyRequestsException;
import likelion.flourishing.support.RateLimitResult;
import likelion.flourishing.support.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * 한 문장을 선택값으로 옮기고 사용자가 직접 고른 값을 앞세운다.
 *
 * <p>아무것도 저장하지 않는다. 이 단계는 사용자가 확인 화면에서 고칠 후보를 만드는 것뿐이고,
 * 저장은 사용자가 확정한 뒤 별도 요청으로 한다. 그래서 트랜잭션도 열지 않는다.
 *
 * <p>AI가 실패해도 200으로 답한다. 사용자는 직접 고르면 그대로 진행할 수 있고, 프런트는 실패
 * 화면이 아니라 선택 화면을 보여 주면 된다. 실패 사유는 failureCode로만 알린다.
 */
@Service
public class ReportInterpretationService {

    private static final Logger log = LoggerFactory.getLogger(ReportInterpretationService.class);

    private static final String RATE_LIMIT_SCOPE = "report-interpretation";

    private final SkinReportStructuringPort structuringPort;
    private final SensitiveDataConsentGuard consentGuard;
    private final RateLimiter rateLimiter;
    private final OpenAiProperties openAiProperties;

    public ReportInterpretationService(
            SkinReportStructuringPort structuringPort,
            SensitiveDataConsentGuard consentGuard,
            RateLimiter rateLimiter,
            OpenAiProperties openAiProperties
    ) {
        this.structuringPort = structuringPort;
        this.consentGuard = consentGuard;
        this.rateLimiter = rateLimiter;
        this.openAiProperties = openAiProperties;
    }

    /**
     * 원문을 구조화한다.
     *
     * <p>확인 순서는 동의 → 조합 검증 → 요청 제한 → AI 호출이다. 동의가 없으면 원문을 외부 모델에
     * 보내면 안 되고, 사용자가 고른 값이 이미 잘못된 조합이면 AI를 부를 필요가 없다. 제한 확인은
     * 실제로 호출을 하기 직전에 한다. 형식이 틀린 요청까지 사용자 몫으로 세지 않기 위해서다.
     */
    public ReportInterpretationResponse interpret(
            AuthenticatedUser principal,
            ReportInterpretationRequest request
    ) {
        consentGuard.assertConsented(principal.userId());

        ManualSelectionsRequest manual = request.manualSelectionsOrEmpty();
        SkinReportPolicy.assertExclusiveSelections(manual.situationSet(), Set.of());
        assertWithinRateLimit(principal.userId());

        StructuringOutcome outcome = structuringPort.structure(request.rawText());
        ExtractedSelections extracted = outcome.extracted();

        BodyArea primaryArea = mergeSingle(manual.primaryArea(), extracted.primaryArea());
        CareAvailability careAvailability = mergeSingle(
                manual.careAvailability(), extracted.careAvailability()
        );
        List<Appearance> appearances = mergeMultiple(manual.appearanceSet(), extracted.appearances());
        List<Sensation> sensations = mergeMultiple(manual.sensationSet(), extracted.sensations());
        List<Situation> situations = mergeMultiple(manual.situationSet(), extracted.situations());

        // 부위 보충 설명은 사용자가 직접 쓰는 자유 문장이라 AI가 채우지 않는다.
        String otherAreasNote = trimToNull(manual.otherAreasNote());

        StructuredSelectionsResponse proposed = StructuredSelectionsResponse.of(
                primaryArea, otherAreasNote, appearances, sensations, situations, careAvailability
        );
        List<MissingField> missingFields = missingFields(
                primaryArea, appearances, sensations, situations, careAvailability
        );

        // AI 출력 스키마에 모호 표현을 담을 자리가 아직 없다. 명세가 요구하는 키는 채워 두고
        // 내용은 AI 프롬프트 확장 뒤에 붙인다. 키가 없으면 프런트가 분기 자체를 못 짠다.
        List<AmbiguityResponse> ambiguities = List.of();

        if (outcome.isSucceeded()) {
            return ReportInterpretationResponse.succeeded(proposed, missingFields, ambiguities);
        }
        logInternalFailure(outcome);
        return ReportInterpretationResponse.failed(
                InterpretationFailureCode.from(outcome.failureCode()),
                proposed,
                missingFields,
                ambiguities
        );
    }

    /**
     * 합친 뒤에도 비어 있는 항목을 모은다.
     *
     * <p>선언 순서가 곧 응답 순서다. 같은 입력에 항상 같은 응답이 나가야 프런트가 목록을 그대로
     * 그릴 수 있다.
     */
    private List<MissingField> missingFields(
            BodyArea primaryArea,
            List<Appearance> appearances,
            List<Sensation> sensations,
            List<Situation> situations,
            CareAvailability careAvailability
    ) {
        List<MissingField> missing = new ArrayList<>();
        if (primaryArea == null) {
            missing.add(MissingField.PRIMARY_AREA);
        }
        if (appearances.isEmpty()) {
            missing.add(MissingField.APPEARANCES);
        }
        if (sensations.isEmpty()) {
            missing.add(MissingField.SENSATIONS);
        }
        if (situations.isEmpty()) {
            missing.add(MissingField.SITUATIONS);
        }
        if (careAvailability == null) {
            missing.add(MissingField.CARE_AVAILABILITY);
        }
        return List.copyOf(missing);
    }

    /**
     * 좁히기 전 실패 코드를 로그에만 남긴다.
     *
     * <p>응답에는 세 값만 나가므로 어느 단계에서 무엇이 어긋났는지는 여기서만 알 수 있다.
     * requestId를 함께 적어 사용자가 신고한 응답과 로그를 이어 붙일 수 있게 한다.
     */
    private void logInternalFailure(StructuringOutcome outcome) {
        log.warn(
                "구조화에 실패했습니다. requestId={} internalCode={} responseCode={}",
                currentRequestId(),
                outcome.failureCode(),
                InterpretationFailureCode.from(outcome.failureCode())
        );
    }

    private String currentRequestId() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        Object requestId = attributes.getAttribute(
                ProblemFactory.REQUEST_ID_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST
        );
        return requestId instanceof String value ? value : null;
    }

    /**
     * 사용자당 호출 상한을 확인한다.
     *
     * <p>호출마다 원문 한 건이 외부로 나가고 응답까지 스레드를 붙잡는다. 인증만으로 열어 두면 비용과
     * 외부 전송량이 클라이언트 재량이 된다.
     */
    private void assertWithinRateLimit(UUID userId) {
        OpenAiProperties.RateLimit rule = openAiProperties.rateLimit();
        RateLimitResult result = rateLimiter.consume(
                RATE_LIMIT_SCOPE, userId.toString(), rule.limit(), rule.window()
        );
        if (!result.allowed()) {
            throw new TooManyRequestsException(result);
        }
    }

    /** 사용자가 고른 값이 있으면 그것을 쓴다. 없을 때에만 AI 값을 쓴다. */
    private <E extends Enum<E>> E mergeSingle(E manualValue, E extractedValue) {
        return manualValue != null ? manualValue : extractedValue;
    }

    /**
     * 다중 선택은 합치지 않고 한쪽만 쓴다.
     *
     * <p>둘을 합치면 사용자가 일부러 뺀 값이 AI 값으로 되살아난다. 사용자가 하나라도 골랐으면
     * 그 목록이 사용자의 답이다.
     */
    private <E extends Enum<E>> List<E> mergeMultiple(Set<E> manualValues, Set<E> extractedValues) {
        return sorted(manualValues.isEmpty() ? extractedValues : manualValues);
    }

    /** 응답 순서를 enum 선언 순으로 고정한다. 같은 입력에 항상 같은 응답이 나가야 한다. */
    private <E extends Enum<E>> List<E> sorted(Set<E> values) {
        return values.stream().sorted(Comparator.comparingInt(Enum::ordinal)).toList();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
