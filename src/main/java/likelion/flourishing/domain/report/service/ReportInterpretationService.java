package likelion.flourishing.domain.report.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.report.ai.ExtractedSelections;
import likelion.flourishing.domain.report.ai.OpenAiProperties;
import likelion.flourishing.domain.report.ai.SkinReportStructuringPort;
import likelion.flourishing.domain.report.ai.StructuringOutcome;
import likelion.flourishing.domain.report.dto.request.ManualSelectionsRequest;
import likelion.flourishing.domain.report.dto.request.ReportInterpretationRequest;
import likelion.flourishing.domain.report.dto.response.FieldSource;
import likelion.flourishing.domain.report.dto.response.ReportInterpretationResponse;
import likelion.flourishing.domain.report.dto.response.StructuredSelectionsResponse;
import likelion.flourishing.domain.report.entity.Appearance;
import likelion.flourishing.domain.report.entity.BodyArea;
import likelion.flourishing.domain.report.entity.CareAvailability;
import likelion.flourishing.domain.report.entity.Sensation;
import likelion.flourishing.domain.report.entity.Situation;
import likelion.flourishing.global.exception.TooManyRequestsException;
import likelion.flourishing.support.RateLimitResult;
import likelion.flourishing.support.RateLimiter;
import org.springframework.stereotype.Service;

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

    private static final String FIELD_PRIMARY_AREA = "primaryArea";
    private static final String FIELD_OTHER_AREAS_NOTE = "otherAreasNote";
    private static final String FIELD_APPEARANCES = "appearances";
    private static final String FIELD_SENSATIONS = "sensations";
    private static final String FIELD_SITUATIONS = "situations";
    private static final String FIELD_CARE_AVAILABILITY = "careAvailability";

    private static final String RATE_LIMIT_SCOPE = "report-interpretation";

    private final SkinReportStructuringPort structuringPort;
    private final SensitiveDataConsentGuard consentGuard;
    private final RateLimiter rateLimiter;
    private final OpenAiProperties openAiProperties;
    private final Clock clock;

    public ReportInterpretationService(
            SkinReportStructuringPort structuringPort,
            SensitiveDataConsentGuard consentGuard,
            RateLimiter rateLimiter,
            OpenAiProperties openAiProperties,
            Clock clock
    ) {
        this.structuringPort = structuringPort;
        this.consentGuard = consentGuard;
        this.rateLimiter = rateLimiter;
        this.openAiProperties = openAiProperties;
        this.clock = clock;
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

        Map<String, FieldSource> fieldSources = new LinkedHashMap<>();
        BodyArea primaryArea = mergeSingle(
                manual.primaryArea(), extracted.primaryArea(), FIELD_PRIMARY_AREA, fieldSources
        );
        CareAvailability careAvailability = mergeSingle(
                manual.careAvailability(), extracted.careAvailability(), FIELD_CARE_AVAILABILITY, fieldSources
        );
        List<Appearance> appearances = mergeMultiple(
                manual.appearanceSet(), extracted.appearances(), FIELD_APPEARANCES, fieldSources
        );
        List<Sensation> sensations = mergeMultiple(
                manual.sensationSet(), extracted.sensations(), FIELD_SENSATIONS, fieldSources
        );
        List<Situation> situations = mergeMultiple(
                manual.situationSet(), extracted.situations(), FIELD_SITUATIONS, fieldSources
        );

        // 부위 보충 설명은 사용자가 직접 쓰는 자유 문장이라 AI가 채우지 않는다.
        String otherAreasNote = trimToNull(manual.otherAreasNote());
        fieldSources.put(
                FIELD_OTHER_AREAS_NOTE,
                otherAreasNote == null ? FieldSource.NONE : FieldSource.MANUAL
        );

        StructuredSelectionsResponse structured = StructuredSelectionsResponse.of(
                primaryArea, otherAreasNote, appearances, sensations, situations, careAvailability
        );
        OffsetDateTime interpretedAt = LocalDateTime.now(clock).atOffset(ZoneOffset.UTC);

        if (outcome.isSucceeded()) {
            return ReportInterpretationResponse.succeeded(structured, fieldSources, interpretedAt);
        }
        return ReportInterpretationResponse.failed(
                outcome.failureCode(), structured, fieldSources, interpretedAt
        );
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
    private <E extends Enum<E>> E mergeSingle(
            E manualValue,
            E extractedValue,
            String field,
            Map<String, FieldSource> fieldSources
    ) {
        if (manualValue != null) {
            fieldSources.put(field, FieldSource.MANUAL);
            return manualValue;
        }
        fieldSources.put(field, extractedValue == null ? FieldSource.NONE : FieldSource.AI);
        return extractedValue;
    }

    /**
     * 다중 선택은 합치지 않고 한쪽만 쓴다.
     *
     * <p>둘을 합치면 사용자가 일부러 뺀 값이 AI 값으로 되살아난다. 사용자가 하나라도 골랐으면
     * 그 목록이 사용자의 답이다.
     */
    private <E extends Enum<E>> List<E> mergeMultiple(
            Set<E> manualValues,
            Set<E> extractedValues,
            String field,
            Map<String, FieldSource> fieldSources
    ) {
        if (!manualValues.isEmpty()) {
            fieldSources.put(field, FieldSource.MANUAL);
            return sorted(manualValues);
        }
        fieldSources.put(field, extractedValues.isEmpty() ? FieldSource.NONE : FieldSource.AI);
        return sorted(extractedValues);
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
