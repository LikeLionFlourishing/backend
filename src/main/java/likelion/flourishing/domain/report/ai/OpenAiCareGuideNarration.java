package likelion.flourishing.domain.report.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * OpenAI Responses API로 관리 설명을 만드는 구현.
 *
 * <p>행동 문구는 모델이 쓰지 않는다. 규칙이 승인한 문구만 스키마의 enum에 넣어 그 안에서 고르게
 * 하고, 응답을 받은 뒤 서버가 다시 허용 목록과 대조한다. 스키마만 믿지 않는 이유는 모델이 아니라
 * 우리가 사용자에게 보이는 문구를 책임지기 때문이다.
 *
 * <p>모델이 새로 쓰는 것은 요약 한 문장뿐이고, 그 문장도 금지 표현이 섞이면 실패로 돌린다.
 * 실패하면 호출한 쪽이 승인된 fallbackText를 쓴다.
 */
@Component
public class OpenAiCareGuideNarration implements CareGuideNarrationPort {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCareGuideNarration.class);

    private static final String SCHEMA_NAME = "care_guide_narration";
    private static final int SUMMARY_MAX_LENGTH = 500;

    private static final String INSTRUCTIONS = """
            너는 검토를 마친 관리 규칙이 허용한 문구만 골라 오늘의 관리 안내를 정리하는 편집자다.
            아래 규칙을 지켜라.
            1. doToday, avoidToday, checkNext에는 주어진 후보 문구를 글자 그대로만 쓴다.
            2. 후보에 없는 문구를 새로 만들거나 고쳐 쓰지 않는다.
            3. 각 항목은 최대 개수를 넘기지 않고, 근거가 약하면 더 적게 골라도 된다.
            4. summary는 고른 항목을 잇는 두 문장 이내의 안내문으로 쓴다.
            5. 진단명, 병명, 중증도, 원인 확정, 제품 추천, 의약품 이야기를 쓰지 않는다.
            6. 금지 표현으로 주어진 말은 쓰지 않는다.
            7. 낫는다거나 반드시 좋아진다는 단정을 하지 않는다.
            """;

    private final ObjectMapper objectMapper;
    private final OpenAiResponsesClient client;

    public OpenAiCareGuideNarration(ObjectMapper objectMapper, OpenAiResponsesClient client) {
        this.objectMapper = objectMapper;
        this.client = client;
    }

    @Override
    public NarrationOutcome narrate(NarrationCommand command) {
        if (!command.hasAllowedActions()) {
            log.warn(
                    "규칙이 허용한 문구가 없어 설명 생성을 건너뜁니다. failureCode={}",
                    AiFailureCode.AI_SCHEMA_VIOLATION
            );
            return NarrationOutcome.failed(AiFailureCode.AI_SCHEMA_VIOLATION);
        }

        OpenAiJsonOutcome outcome = client.requestStructuredJson(
                SCHEMA_NAME, buildSchema(command), INSTRUCTIONS, buildUserContent(command)
        );
        if (!outcome.isSucceeded()) {
            return NarrationOutcome.failed(outcome.failureCode());
        }
        return validate(outcome.payload(), command);
    }

    /**
     * 응답을 허용 목록과 대조한다.
     *
     * <p>허용 목록을 벗어난 문구가 하나라도 있으면 그 항목만 빼지 않고 전체를 실패로 만든다.
     * 모델이 목록을 지키지 못한 응답은 요약 문장도 신뢰할 수 없기 때문이다.
     */
    private NarrationOutcome validate(JsonNode payload, NarrationCommand command) {
        String summary = payload.path("summary").asText("").trim();
        if (summary.isEmpty() || summary.length() > SUMMARY_MAX_LENGTH) {
            return violation("summary");
        }
        if (containsForbiddenExpression(summary, command.forbiddenExpressions())) {
            return violation("forbiddenExpression");
        }

        List<String> doToday = readAllowed(payload, "doToday", command.allowedDoToday(), command);
        List<String> avoidToday = readAllowed(payload, "avoidToday", command.allowedAvoidToday(), command);
        List<String> checkNext = readAllowed(payload, "checkNext", command.allowedCheckNext(), command);
        if (doToday == null || avoidToday == null || checkNext == null) {
            return violation("actionAllowList");
        }
        if (doToday.isEmpty() && avoidToday.isEmpty() && checkNext.isEmpty()) {
            return violation("emptySelection");
        }
        return NarrationOutcome.succeeded(summary, doToday, avoidToday, checkNext);
    }

    /** 허용 목록을 벗어나거나 개수를 넘기면 null을 돌려 상위에서 실패로 잇는다. */
    private List<String> readAllowed(
            JsonNode payload,
            String field,
            List<String> allowed,
            NarrationCommand command
    ) {
        JsonNode node = payload.get(field);
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            return null;
        }

        Set<String> selected = new LinkedHashSet<>();
        for (JsonNode element : node) {
            String content = element.asText("");
            if (!allowed.contains(content)) {
                return null;
            }
            selected.add(content);
        }
        if (selected.size() > command.maxItemsPerType()) {
            return null;
        }
        return List.copyOf(selected);
    }

    private boolean containsForbiddenExpression(String summary, List<String> forbiddenExpressions) {
        String normalized = summary.toLowerCase(Locale.ROOT);
        return forbiddenExpressions.stream()
                .filter(expression -> !expression.isBlank())
                .anyMatch(expression -> normalized.contains(expression.trim().toLowerCase(Locale.ROOT)));
    }

    private NarrationOutcome violation(String reason) {
        log.warn(
                "설명 생성 결과가 허용 범위를 벗어났습니다. failureCode={} reason={}",
                AiFailureCode.AI_SCHEMA_VIOLATION,
                reason
        );
        return NarrationOutcome.failed(AiFailureCode.AI_SCHEMA_VIOLATION);
    }

    /**
     * 확정 선택값과 규칙 요약만 넣는다.
     *
     * <p>선택값은 코드로만 보내서 문장으로 풀어 쓴 개인 상태가 밖으로 나가지 않게 한다.
     */
    private String buildUserContent(NarrationCommand command) {
        List<String> lines = new ArrayList<>();
        lines.add("부위: " + nullSafe(command.primaryArea()));
        lines.add("겉모습: " + codes(command.appearances()));
        lines.add("불편: " + codes(command.sensations()));
        lines.add("직전 상황: " + codes(command.situations()));
        lines.add("가능한 관리: " + nullSafe(command.careAvailability()));
        lines.add("항목별 최대 개수: " + command.maxItemsPerType());
        lines.add("적용 규칙 요약:");
        command.ruleSummaries().forEach(summary -> lines.add("- " + summary));
        if (!command.forbiddenExpressions().isEmpty()) {
            lines.add("금지 표현:");
            command.forbiddenExpressions().forEach(expression -> lines.add("- " + expression));
        }
        return String.join("\n", lines);
    }

    private String nullSafe(Enum<?> value) {
        return value == null ? "없음" : value.name();
    }

    private String codes(Set<? extends Enum<?>> values) {
        if (values.isEmpty()) {
            return "없음";
        }
        return values.stream().map(Enum::name).sorted().reduce((left, right) -> left + ", " + right).orElse("없음");
    }

    /**
     * 허용 문구를 그대로 enum에 넣은 스키마.
     *
     * <p>후보가 없는 유형은 maxItems를 0으로 둔다. 빈 enum은 스키마로 성립하지 않기 때문이다.
     */
    private ObjectNode buildSchema(NarrationCommand command) {
        ObjectNode summary = objectMapper.createObjectNode()
                .put("type", "string")
                .put("description", "고른 항목을 잇는 두 문장 이내의 안내문");

        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("summary", summary);
        properties.set("doToday", allowedArray(command.allowedDoToday(), command.maxItemsPerType()));
        properties.set("avoidToday", allowedArray(command.allowedAvoidToday(), command.maxItemsPerType()));
        properties.set("checkNext", allowedArray(command.allowedCheckNext(), command.maxItemsPerType()));

        ArrayNode required = objectMapper.createArrayNode();
        required.add("summary");
        required.add("doToday");
        required.add("avoidToday");
        required.add("checkNext");

        ObjectNode schema = objectMapper.createObjectNode().put("type", "object");
        schema.set("properties", properties);
        schema.set("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    private ObjectNode allowedArray(List<String> allowed, int maxItems) {
        ObjectNode items = objectMapper.createObjectNode().put("type", "string");
        ObjectNode node = objectMapper.createObjectNode().put("type", "array");
        node.set("items", items);

        if (allowed.isEmpty()) {
            node.put("maxItems", 0);
            return node;
        }

        ArrayNode enumValues = objectMapper.createArrayNode();
        allowed.forEach(enumValues::add);
        items.set("enum", enumValues);
        node.put("maxItems", Math.min(maxItems, allowed.size()));
        return node;
    }
}
