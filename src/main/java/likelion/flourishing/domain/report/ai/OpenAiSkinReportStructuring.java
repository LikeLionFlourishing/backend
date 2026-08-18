package likelion.flourishing.domain.report.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;
import likelion.flourishing.domain.report.entity.Appearance;
import likelion.flourishing.domain.report.entity.BodyArea;
import likelion.flourishing.domain.report.entity.CareAvailability;
import likelion.flourishing.domain.report.entity.Sensation;
import likelion.flourishing.domain.report.entity.Situation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * OpenAI Responses API로 원문을 선택값으로 옮기는 구현.
 *
 * <p>스키마를 enum 상수에서 만들기 때문에 선택값이 추가되면 프롬프트도 함께 따라온다. 스키마
 * 문자열을 따로 관리하면 코드와 어긋나 모델이 없는 값을 내놓게 된다.
 *
 * <p>strict 구조화 출력이 있어도 서버에서 값을 다시 확인한다. 모델이 스키마를 지켰다는 사실이
 * 우리 도메인 규칙까지 만족한다는 뜻은 아니다. 모르는 값이 하나라도 있으면 전체를 실패로
 * 처리해서 잘못 읽은 값이 사용자 화면에 올라가지 않게 한다.
 */
@Component
public class OpenAiSkinReportStructuring implements SkinReportStructuringPort {

    private static final Logger log = LoggerFactory.getLogger(OpenAiSkinReportStructuring.class);

    private static final String SCHEMA_NAME = "skin_report_structuring";

    private static final String INSTRUCTIONS = """
            너는 사용자가 쓴 피부 상태 문장에서 정해진 선택값만 골라내는 분류기다.
            아래 규칙을 지켜라.
            1. 문장에 근거가 있는 값만 고른다. 추측하거나 보충하지 않는다.
            2. 근거가 없으면 단일 선택은 null, 다중 선택은 빈 배열로 둔다.
            3. 진단명, 병명, 중증도, 원인, 치료법, 제품을 만들어 내지 않는다.
            4. appearances에 UNSURE를 고르면 다른 값을 함께 고르지 않는다.
            5. sensations에 NONE을 고르면 다른 값을 함께 고르지 않는다.
            6. situations에 NONE_RECALLED를 고르면 다른 값을 함께 고르지 않는다.
            7. 스키마에 없는 값은 절대 쓰지 않는다.
            """;

    private final ObjectMapper objectMapper;
    private final OpenAiResponsesClient client;

    public OpenAiSkinReportStructuring(ObjectMapper objectMapper, OpenAiResponsesClient client) {
        this.objectMapper = objectMapper;
        this.client = client;
    }

    @Override
    public StructuringOutcome structure(String rawText) {
        OpenAiJsonOutcome outcome = client.requestStructuredJson(
                SCHEMA_NAME, buildSchema(), INSTRUCTIONS, rawText
        );
        if (!outcome.isSucceeded()) {
            return StructuringOutcome.failed(outcome.failureCode());
        }

        try {
            return StructuringOutcome.succeeded(readSelections(outcome.payload()));
        } catch (UnknownSelectionException exception) {
            log.warn(
                    "구조화 결과에 허용하지 않는 값이 있습니다. failureCode={} field={}",
                    AiFailureCode.AI_SCHEMA_VIOLATION,
                    exception.getField()
            );
            return StructuringOutcome.failed(AiFailureCode.AI_SCHEMA_VIOLATION);
        }
    }

    /**
     * 응답을 도메인 값으로 옮긴다.
     *
     * <p>배타 선택 규칙을 어긴 조합은 실패로 만들지 않고 배타 값을 뺀다. UNSURE와 REDNESS가 함께
     * 오면 "붉음을 봤지만 확신은 없다"로 읽는 편이 자연스럽고, 사용자가 확인 화면에서 고칠 수
     * 있는 값이다. 반면 모르는 코드는 고칠 방법이 없어 실패로 돌린다.
     */
    private ExtractedSelections readSelections(JsonNode payload) {
        BodyArea primaryArea = readSingle(payload, "primaryArea", BodyArea::valueOf);
        CareAvailability careAvailability = readSingle(payload, "careAvailability", CareAvailability::valueOf);
        // 겉모습과 느껴지는 불편에는 단독 선택 값이 없다. 명세 v2_1에서 두 그룹의 "모름/없음"이
        // 사라져 모순 조합 자체가 생기지 않으므로 그대로 받는다.
        Set<Appearance> appearances = readMultiple(payload, "appearances", Appearance::valueOf);
        Set<Sensation> sensations = readMultiple(payload, "sensations", Sensation::valueOf);
        Set<Situation> situations = withoutExclusiveConflict(
                readMultiple(payload, "situations", Situation::valueOf), Situation.NONE_RECALLED
        );
        return new ExtractedSelections(primaryArea, appearances, sensations, situations, careAvailability);
    }

    private <E extends Enum<E>> E readSingle(JsonNode payload, String field, Function<String, E> parser) {
        JsonNode node = payload.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        return parse(field, node.asText(), parser);
    }

    private <E extends Enum<E>> Set<E> readMultiple(
            JsonNode payload,
            String field,
            Function<String, E> parser
    ) {
        JsonNode node = payload.get(field);
        if (node == null || node.isNull()) {
            return Set.of();
        }
        if (!node.isArray()) {
            throw new UnknownSelectionException(field);
        }
        Set<E> values = new LinkedHashSet<>();
        for (JsonNode element : node) {
            values.add(parse(field, element.asText(), parser));
        }
        return values;
    }

    private <E extends Enum<E>> E parse(String field, String value, Function<String, E> parser) {
        try {
            return parser.apply(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new UnknownSelectionException(field);
        }
    }

    private <E extends Enum<E>> Set<E> withoutExclusiveConflict(Set<E> values, E exclusiveValue) {
        if (values.size() <= 1 || !values.contains(exclusiveValue)) {
            return values;
        }
        Set<E> narrowed = new LinkedHashSet<>(values);
        narrowed.remove(exclusiveValue);
        return narrowed;
    }

    /**
     * 선택값 enum에서 strict 구조화 출력용 스키마를 만든다.
     *
     * <p>단일 선택은 근거가 없을 때 null을 쓸 수 있어야 해서 {@code ["string", "null"]}로 둔다.
     * strict 모드는 모든 속성을 required로 요구하므로 선택 여부는 타입으로만 표현할 수 있다.
     */
    private ObjectNode buildSchema() {
        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("primaryArea", nullableEnum(BodyArea.values(), "가장 신경 쓰이는 한 부위"));
        properties.set("appearances", enumArray(Appearance.values(), "눈으로 보이는 상태"));
        properties.set("sensations", enumArray(Sensation.values(), "느껴지는 불편"));
        properties.set("situations", enumArray(Situation.values(), "직전에 있었던 상황"));
        properties.set("careAvailability", nullableEnum(CareAvailability.values(), "지금 할 수 있는 관리"));

        ArrayNode required = objectMapper.createArrayNode();
        required.add("primaryArea");
        required.add("appearances");
        required.add("sensations");
        required.add("situations");
        required.add("careAvailability");

        ObjectNode schema = objectMapper.createObjectNode().put("type", "object");
        schema.set("properties", properties);
        schema.set("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    /**
     * 값이 없을 수도 있는 단일 선택 필드.
     *
     * <p>enum 목록에 null을 함께 넣는다. JSON Schema에서 enum은 값 전체를 그 목록으로 제한하므로,
     * type이 null을 허용해도 enum에 없으면 null은 유효하지 않다. 그러면 근거가 없을 때 null로 두라는
     * 지시를 모델이 지킬 방법이 없어져, 문장에 부위 이야기가 없어도 13개 중 하나를 고르게 된다.
     * 그 값이 그대로 사용자 화면의 기본 선택으로 올라간다.
     */
    private ObjectNode nullableEnum(Enum<?>[] values, String description) {
        ArrayNode types = objectMapper.createArrayNode();
        types.add("string");
        types.add("null");

        ArrayNode allowed = enumValues(values);
        allowed.addNull();

        ObjectNode node = objectMapper.createObjectNode();
        node.set("type", types);
        node.put("description", description);
        node.set("enum", allowed);
        return node;
    }

    private ObjectNode enumArray(Enum<?>[] values, String description) {
        ObjectNode items = objectMapper.createObjectNode().put("type", "string");
        items.set("enum", enumValues(values));

        ObjectNode node = objectMapper.createObjectNode().put("type", "array");
        node.put("description", description);
        node.set("items", items);
        return node;
    }

    private ArrayNode enumValues(Enum<?>[] values) {
        ArrayNode node = objectMapper.createArrayNode();
        for (Enum<?> value : values) {
            node.add(value.name());
        }
        return node;
    }

    /** 스키마에 없는 코드가 왔다는 내부 신호. 밖으로 나가지 않고 실패 코드로 바뀐다. */
    private static final class UnknownSelectionException extends RuntimeException {

        private final String field;

        private UnknownSelectionException(String field) {
            super(null, null, false, false);
            this.field = field;
        }

        private String getField() {
            return field;
        }
    }
}
