package likelion.flourishing.domain.report.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.CancellationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * OpenAI Responses API로 JSON Schema 구조화 응답을 요청하는 저수준 호출부.
 *
 * <p>{@code text.format.type = json_schema}와 {@code strict = true}를 써서 모델이 우리가 준
 * 스키마를 벗어난 JSON을 만들지 못하게 한다. 그래도 서버에서 값을 다시 검증한다. 스키마는
 * 모델 쪽 제약이고, 우리 도메인 규칙까지 보장해 주지는 않기 때문이다.
 *
 * <p>{@code store = false}를 보내 대화를 OpenAI에 남기지 않는다. 원문은 사용자의 피부 상태라
 * 우리 DB 밖에 사본을 만들지 않는다.
 *
 * <p>로그에 남기는 것은 실패 코드와 HTTP 상태뿐이다. 프롬프트, 원문, 모델 응답, API 키는 어떤
 * 경로로도 로그에 들어가지 않는다. 예외 메시지에 요청 본문이 섞여 나올 수 있어 예외 메시지도
 * 남기지 않고 예외 타입 이름만 남긴다.
 */
@Component
public class OpenAiResponsesClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiResponsesClient.class);

    private static final String ROLE_SYSTEM = "system";
    private static final String ROLE_USER = "user";
    private static final String TYPE_MESSAGE = "message";
    private static final String TYPE_OUTPUT_TEXT = "output_text";
    private static final String TYPE_REFUSAL = "refusal";
    private static final String STATUS_COMPLETED = "completed";

    private final ObjectMapper objectMapper;
    private final OpenAiProperties properties;
    private final RestClient restClient;

    public OpenAiResponsesClient(ObjectMapper objectMapper, OpenAiProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory(properties))
                .build();
    }

    /**
     * 스키마를 만족하는 JSON 하나를 받아 온다.
     *
     * @param schemaName    모델에 주는 스키마 이름. 로깅이나 캐시 식별에만 쓰이고 응답에는 없다.
     * @param schema        strict 구조화 출력용 JSON Schema. 최상위는 object여야 한다.
     * @param instructions  모델이 지켜야 할 규칙. 사용자 원문을 넣지 않는다.
     * @param userContent   모델이 읽어야 할 입력.
     */
    public OpenAiJsonOutcome requestStructuredJson(
            String schemaName,
            ObjectNode schema,
            String instructions,
            String userContent
    ) {
        if (!properties.configured()) {
            log.warn("OpenAI 설정이 없어 호출을 건너뜁니다. failureCode={}", AiFailureCode.AI_NOT_CONFIGURED);
            return OpenAiJsonOutcome.failed(AiFailureCode.AI_NOT_CONFIGURED);
        }

        ObjectNode requestBody = buildRequestBody(schemaName, schema, instructions, userContent);
        JsonNode responseBody;
        try {
            responseBody = restClient.post()
                    .uri(properties.responsesEndpoint())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            log.warn(
                                    "OpenAI 호출이 실패 상태로 돌아왔습니다. status={} failureCode={}",
                                    response.getStatusCode().value(),
                                    AiFailureCode.AI_HTTP_ERROR
                            );
                            return null;
                        }
                        return objectMapper.readTree(response.getBody());
                    });
        } catch (Exception exception) {
            AiFailureCode failureCode = isTimeout(exception)
                    ? AiFailureCode.AI_TIMEOUT
                    : AiFailureCode.AI_UNREACHABLE;
            log.warn(
                    "OpenAI에 닿지 못했습니다. failureCode={} exceptionType={}",
                    failureCode,
                    exception.getClass().getName()
            );
            return OpenAiJsonOutcome.failed(failureCode);
        }

        if (responseBody == null) {
            return OpenAiJsonOutcome.failed(AiFailureCode.AI_HTTP_ERROR);
        }
        return extractJsonPayload(responseBody);
    }

    private ObjectNode buildRequestBody(
            String schemaName,
            ObjectNode schema,
            String instructions,
            String userContent
    ) {
        ObjectNode format = objectMapper.createObjectNode()
                .put("type", "json_schema")
                .put("name", schemaName)
                .put("strict", true);
        format.set("schema", schema);

        ObjectNode text = objectMapper.createObjectNode();
        text.set("format", format);

        ArrayNode input = objectMapper.createArrayNode();
        input.add(message(ROLE_SYSTEM, instructions));
        input.add(message(ROLE_USER, userContent));

        ObjectNode body = objectMapper.createObjectNode()
                .put("model", properties.model())
                .put("max_output_tokens", properties.maxOutputTokens())
                .put("store", false);
        body.set("input", input);
        body.set("text", text);
        return body;
    }

    private ObjectNode message(String role, String content) {
        return objectMapper.createObjectNode()
                .put("role", role)
                .put("content", content);
    }

    /**
     * 응답에서 구조화 JSON을 꺼낸다.
     *
     * <p>output 배열에는 추론 항목처럼 메시지가 아닌 것도 섞여 들어오므로 type이 message인 것만
     * 찾는다. 그 안의 content에는 output_text 대신 refusal이 올 수 있어 따로 구분한다.
     *
     * <p>status가 completed가 아니면 토큰 한도나 콘텐츠 필터로 끊긴 경우다. 이때 JSON은 조각만
     * 남아 있어 파싱이 되더라도 신뢰할 수 없으므로 먼저 실패로 처리한다.
     */
    private OpenAiJsonOutcome extractJsonPayload(JsonNode responseBody) {
        String status = responseBody.path("status").asText("");
        if (!STATUS_COMPLETED.equals(status)) {
            log.warn("OpenAI 응답이 끝나지 않았습니다. failureCode={}", AiFailureCode.AI_INCOMPLETE);
            return OpenAiJsonOutcome.failed(AiFailureCode.AI_INCOMPLETE);
        }

        for (JsonNode output : responseBody.path("output")) {
            if (!TYPE_MESSAGE.equals(output.path("type").asText())) {
                continue;
            }
            for (JsonNode content : output.path("content")) {
                String contentType = content.path("type").asText();
                if (TYPE_REFUSAL.equals(contentType)) {
                    log.warn("OpenAI가 응답을 거부했습니다. failureCode={}", AiFailureCode.AI_REFUSED);
                    return OpenAiJsonOutcome.failed(AiFailureCode.AI_REFUSED);
                }
                if (TYPE_OUTPUT_TEXT.equals(contentType)) {
                    return parsePayload(content.path("text").asText(""));
                }
            }
        }

        log.warn("OpenAI 응답에서 결과를 찾지 못했습니다. failureCode={}", AiFailureCode.AI_MALFORMED_OUTPUT);
        return OpenAiJsonOutcome.failed(AiFailureCode.AI_MALFORMED_OUTPUT);
    }

    private OpenAiJsonOutcome parsePayload(String text) {
        try {
            JsonNode payload = objectMapper.readTree(text);
            if (payload == null || !payload.isObject()) {
                log.warn("OpenAI 결과가 객체가 아닙니다. failureCode={}", AiFailureCode.AI_MALFORMED_OUTPUT);
                return OpenAiJsonOutcome.failed(AiFailureCode.AI_MALFORMED_OUTPUT);
            }
            return OpenAiJsonOutcome.succeeded(payload);
        } catch (Exception exception) {
            log.warn(
                    "OpenAI 결과를 JSON으로 읽지 못했습니다. failureCode={} exceptionType={}",
                    AiFailureCode.AI_MALFORMED_OUTPUT,
                    exception.getClass().getName()
            );
            return OpenAiJsonOutcome.failed(AiFailureCode.AI_MALFORMED_OUTPUT);
        }
    }

    /**
     * 제한 시간 초과와 그 밖의 연결 실패를 가른다. 명세가 시간 초과를 따로 다루기 때문이다.
     *
     * <p>{@link CancellationException}도 시간 초과로 본다. JDK HttpClient는 요청 제한 시간이 지나면
     * 내부 future를 취소하는데, 취소가 {@link HttpTimeoutException}보다 먼저 전파되면 호출부에는
     * 취소 예외만 올라온다. 이 클라이언트는 제한 시간 말고는 요청을 취소하지 않으므로 취소는 곧
     * 시간 초과다.
     */
    private boolean isTimeout(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException
                    || cause instanceof SocketTimeoutException
                    || cause instanceof CancellationException) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    private static JdkClientHttpRequestFactory requestFactory(OpenAiProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }
}
