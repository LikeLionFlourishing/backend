package likelion.flourishing.analytics.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import likelion.flourishing.analytics.domain.AnalyticsEventName;
import likelion.flourishing.analytics.dto.request.AnalyticsEventBatchRequest;
import likelion.flourishing.analytics.dto.request.AnalyticsEventPropertiesRequest;
import likelion.flourishing.analytics.dto.request.AnalyticsEventRequest;
import likelion.flourishing.analytics.repository.AnalyticsEventRepository;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 이벤트 허용 목록을 검증한 뒤 한 트랜잭션으로 멱등 저장한다. */
@Service
public class AnalyticsEventService {

    private static final int MAX_BATCH_SIZE = 20;

    private final AnalyticsEventRepository analyticsEventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AnalyticsEventService(
            AnalyticsEventRepository analyticsEventRepository,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.analyticsEventRepository = analyticsEventRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public int collect(AuthenticatedUser principal, AnalyticsEventBatchRequest request) {
        List<AnalyticsEventRequest> events = requireValidBatch(request);

        // 저장을 시작하기 전에 전체 배치를 변환해 뒤쪽의 잘못된 이벤트로 일부만 저장되는 일을 막는다.
        List<PreparedEvent> preparedEvents = events.stream()
                .map(this::prepare)
                .toList();

        LocalDateTime receivedAt = LocalDateTime.now(clock);
        preparedEvents.forEach(event -> analyticsEventRepository.insertIdempotently(
                event.eventId(),
                principal.userId(),
                event.eventName().name(),
                event.allowedProperties(),
                event.occurredAt(),
                receivedAt
        ));

        // 같은 묶음을 재전송해도 첫 응답과 동일하게 접수한 요청 개수를 반환한다.
        return events.size();
    }

    private List<AnalyticsEventRequest> requireValidBatch(AnalyticsEventBatchRequest request) {
        if (request == null
                || !request.isAllowedFieldsOnly()
                || request.getEvents() == null
                || request.getEvents().isEmpty()
                || request.getEvents().size() > MAX_BATCH_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return request.getEvents();
    }

    private PreparedEvent prepare(AnalyticsEventRequest event) {
        if (event == null
                || event.getEventId() == null
                || event.getOccurredAt() == null
                || !event.isAllowedFieldsOnly()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        AnalyticsEventName eventName;
        try {
            eventName = AnalyticsEventName.valueOf(event.getEventName());
        } catch (IllegalArgumentException | NullPointerException invalidEventName) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        AnalyticsEventPropertiesRequest properties = event.getProperties();
        if (properties != null && (
                !properties.isAllowedFieldsOnly()
                        || !properties.isResultTypeAllowed()
                        || properties.getDurationMs() != null && properties.getDurationMs() < 0
        )) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        return new PreparedEvent(
                event.getEventId(),
                eventName,
                serialize(properties),
                event.getOccurredAt().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime()
        );
    }

    private String serialize(AnalyticsEventPropertiesRequest properties) {
        if (properties == null) {
            return null;
        }

        Map<String, Object> allowedProperties = properties.toAllowedProperties();
        try {
            return objectMapper.writeValueAsString(allowedProperties);
        } catch (JsonProcessingException impossibleForPrimitiveProperties) {
            throw new IllegalStateException("측정 이벤트 속성을 JSON으로 변환할 수 없습니다.", impossibleForPrimitiveProperties);
        }
    }

    private record PreparedEvent(
            UUID eventId,
            AnalyticsEventName eventName,
            String allowedProperties,
            LocalDateTime occurredAt
    ) {
    }
}
