package likelion.flourishing.domain.analytics.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import likelion.flourishing.domain.analytics.dto.request.AnalyticsEventBatchRequest;
import likelion.flourishing.domain.analytics.dto.request.AnalyticsEventPropertiesRequest;
import likelion.flourishing.domain.analytics.dto.request.AnalyticsEventRequest;
import likelion.flourishing.domain.analytics.entity.AnalyticsEventName;
import likelion.flourishing.domain.analytics.repository.AnalyticsEventRepository;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 이벤트 허용 목록을 검증한 뒤 한 트랜잭션으로 멱등 저장한다. */
@Service
public class AnalyticsEventService {

    private static final int MAX_BATCH_SIZE = 20;

    /**
     * occurredAt이 놓일 수 있는 범위. 클라이언트가 정하는 값이라 상·하한이 없으면 두 가지가 깨진다.
     *
     * <p>하나는 지표 오염이고, 다른 하나는 MySQL DATETIME 범위를 벗어난 값(예: +10000-01-01)이
     * OffsetDateTime으로는 정상 파싱돼 JDBC 단계에서 터지는 것이다. 그러면 422가 아니라 500이
     * 나가고 배치 전체가 롤백된다. 여기서 걸러 다른 검증 실패와 같은 422로 떨어뜨린다.
     *
     * <p>미래 하루는 클라이언트 시계 오차를, 과거 30일은 오프라인 상태로 쌓아 둔 이벤트를 감안한 값이다.
     */
    private static final Duration MAX_CLOCK_SKEW = Duration.ofDays(1);
    private static final Duration MAX_BACKFILL = Duration.ofDays(30);

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
        LocalDateTime receivedAt = LocalDateTime.now(clock);

        // 저장을 시작하기 전에 전체 배치를 변환해 뒤쪽의 잘못된 이벤트로 일부만 저장되는 일을 막는다.
        List<PreparedEvent> preparedEvents = new ArrayList<>();
        for (AnalyticsEventRequest event : events) {
            preparedEvents.add(prepare(event, receivedAt));
        }

        // 한 배치 안에 같은 eventId가 두 번 오면 행은 하나만 생긴다. 응답이 사실과 맞도록 미리 접는다.
        List<PreparedEvent> distinctEvents = distinctByEventId(preparedEvents);

        distinctEvents.forEach(event -> analyticsEventRepository.insertIdempotently(
                event.eventId(),
                principal.userId(),
                event.eventName().name(),
                event.allowedProperties(),
                event.occurredAt(),
                receivedAt
        ));

        // 저장된 행 수가 아니라 접수한 이벤트 수다. 같은 묶음을 재전송해도 첫 응답과 값이 같아야 하고,
        // 명세가 accepted에 minimum 1을 걸어 두어 영향 행 수를 세면 재전송에서 0이 나가기 때문이다.
        return distinctEvents.size();
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

    /** 먼저 온 것을 남긴다. 같은 eventId면 뒤엣것은 어차피 저장되지 않는다. */
    private List<PreparedEvent> distinctByEventId(List<PreparedEvent> preparedEvents) {
        Map<UUID, PreparedEvent> byEventId = new LinkedHashMap<>();
        preparedEvents.forEach(event -> byEventId.putIfAbsent(event.eventId(), event));
        return List.copyOf(byEventId.values());
    }

    private PreparedEvent prepare(AnalyticsEventRequest event, LocalDateTime receivedAt) {
        if (event == null
                || event.getEventId() == null
                || event.getOccurredAt() == null
                || !event.isAllowedFieldsOnly()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        AnalyticsEventName eventName;
        try {
            eventName = AnalyticsEventName.valueOf(event.getName());
        } catch (IllegalArgumentException | NullPointerException invalidEventName) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        AnalyticsEventPropertiesRequest properties = event.getProperties();
        if (properties != null && (
                !properties.isAllowedFieldsOnly()
                        || !properties.isResultTypeAllowed()
                        || !properties.isWithinDeclaredRanges()
        )) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        LocalDateTime occurredAt = event.getOccurredAt()
                .withOffsetSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
        requireWithinAcceptedWindow(occurredAt, receivedAt);

        return new PreparedEvent(event.getEventId(), eventName, serialize(properties), occurredAt);
    }

    private void requireWithinAcceptedWindow(LocalDateTime occurredAt, LocalDateTime receivedAt) {
        if (occurredAt.isAfter(receivedAt.plus(MAX_CLOCK_SKEW))
                || occurredAt.isBefore(receivedAt.minus(MAX_BACKFILL))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
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
