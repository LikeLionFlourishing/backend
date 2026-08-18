package likelion.flourishing.domain.report.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 실제 HTTP 왕복으로 요청 본문과 응답 처리를 확인한다.
 *
 * <p>JDK에 들어 있는 {@link HttpServer}를 쓴다. 외부 목 서버 의존성을 늘리지 않고도 우리가 무엇을
 * 보내는지 그대로 받아 볼 수 있다.
 *
 * <p>확인하는 것: strict json_schema와 store=false를 보내는지, 거부·중단·오류 상태·시간 초과를
 * 각각 다른 실패 코드로 나누는지, 키가 없으면 호출하지 않는지.
 */
class OpenAiResponsesClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger responseStatus = new AtomicInteger(200);
    private final AtomicReference<String> responseBody = new AtomicReference<>("{}");
    private final AtomicReference<String> recordedBody = new AtomicReference<>();
    private final AtomicReference<String> recordedAuthorization = new AtomicReference<>();
    private final AtomicInteger responseDelayMillis = new AtomicInteger(0);
    private final CountDownLatch teardown = new CountDownLatch(1);

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/responses", exchange -> {
            recordedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            recordedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            awaitUntilTeardown(responseDelayMillis.get());
            byte[] body = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus.get(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    @AfterEach
    void stopServer() {
        teardown.countDown();
        server.stop(0);
    }

    @Test
    void completedResponseYieldsParsedJsonAndSendsStrictSchema() {
        responseBody.set(completedResponse("{\\\"answer\\\":\\\"ok\\\"}"));

        OpenAiJsonOutcome outcome = client(Duration.ofSeconds(3)).requestStructuredJson(
                "test_schema", schema(), "규칙", "입력"
        );

        assertThat(outcome.isSucceeded()).isTrue();
        assertThat(outcome.payload().path("answer").asText()).isEqualTo("ok");
        assertThat(recordedAuthorization.get()).isEqualTo("Bearer test-key");

        JsonNode request = readRecordedBody();
        assertThat(request.path("model").asText()).isEqualTo("test-model");
        assertThat(request.path("store").asBoolean(true)).isFalse();
        assertThat(request.path("text").path("format").path("type").asText()).isEqualTo("json_schema");
        assertThat(request.path("text").path("format").path("strict").asBoolean(false)).isTrue();
        assertThat(request.path("text").path("format").path("name").asText()).isEqualTo("test_schema");
        assertThat(request.path("input")).hasSize(2);
        assertThat(request.path("input").get(1).path("content").asText()).isEqualTo("입력");
    }

    @Test
    void refusalIsReportedSeparately() {
        responseBody.set("""
                {"status":"completed","output":[{"type":"message","content":[
                  {"type":"refusal","refusal":"도와드릴 수 없습니다."}]}]}
                """);

        OpenAiJsonOutcome outcome = client(Duration.ofSeconds(3)).requestStructuredJson(
                "test_schema", schema(), "규칙", "입력"
        );

        assertThat(outcome.failureCode()).isEqualTo(AiFailureCode.AI_REFUSED);
    }

    @Test
    void incompleteResponseIsNotParsed() {
        responseBody.set("""
                {"status":"incomplete","incomplete_details":{"reason":"max_output_tokens"},
                 "output":[{"type":"message","content":[{"type":"output_text","text":"{\\"answer\\":"}]}]}
                """);

        OpenAiJsonOutcome outcome = client(Duration.ofSeconds(3)).requestStructuredJson(
                "test_schema", schema(), "규칙", "입력"
        );

        assertThat(outcome.failureCode()).isEqualTo(AiFailureCode.AI_INCOMPLETE);
    }

    @Test
    void nonSuccessStatusBecomesHttpError() {
        responseStatus.set(429);
        responseBody.set("{\"error\":{\"message\":\"rate limit\"}}");

        OpenAiJsonOutcome outcome = client(Duration.ofSeconds(3)).requestStructuredJson(
                "test_schema", schema(), "규칙", "입력"
        );

        assertThat(outcome.failureCode()).isEqualTo(AiFailureCode.AI_HTTP_ERROR);
    }

    @Test
    void textThatIsNotJsonBecomesMalformedOutput() {
        responseBody.set("""
                {"status":"completed","output":[{"type":"reasoning"},{"type":"message","content":[
                  {"type":"output_text","text":"json이 아닙니다"}]}]}
                """);

        OpenAiJsonOutcome outcome = client(Duration.ofSeconds(3)).requestStructuredJson(
                "test_schema", schema(), "규칙", "입력"
        );

        assertThat(outcome.failureCode()).isEqualTo(AiFailureCode.AI_MALFORMED_OUTPUT);
    }

    /**
     * 지연을 제한 시간의 30배로 벌린다.
     *
     * <p>이전에는 지연 400ms에 제한 시간 100ms였다. 여유가 4배뿐이어서 CPU 경합이 심한 CI에서는 응답
     * 헤더가 제한 시간 경계에 걸쳤고, 헤더를 받은 뒤 본문에서 끊기면 {@code HttpTimeoutException}이
     * 아니라 EOF {@code IOException}이 올라와 AI_UNREACHABLE로 분류됐다. 여유를 벌리면 헤더가 창
     * 안에 들어올 일이 없어 제한 시간 초과 경로만 남는다.
     */
    @Test
    void readTimeoutIsReportedAsTimeout() {
        responseDelayMillis.set(3_000);
        responseBody.set(completedResponse("{}"));

        OpenAiJsonOutcome outcome = client(Duration.ofMillis(100)).requestStructuredJson(
                "test_schema", schema(), "규칙", "입력"
        );

        assertThat(outcome.failureCode()).isEqualTo(AiFailureCode.AI_TIMEOUT);
    }

    @Test
    void missingApiKeySkipsTheCall() {
        OpenAiProperties notConfigured = new OpenAiProperties(
                baseUrl, "", "test-model", Duration.ofSeconds(1), Duration.ofSeconds(1), 100, null
        );

        OpenAiJsonOutcome outcome = new OpenAiResponsesClient(objectMapper, notConfigured)
                .requestStructuredJson("test_schema", schema(), "규칙", "입력");

        assertThat(outcome.failureCode()).isEqualTo(AiFailureCode.AI_NOT_CONFIGURED);
        assertThat(recordedBody.get()).isNull();
    }

    private OpenAiResponsesClient client(Duration readTimeout) {
        return new OpenAiResponsesClient(objectMapper, new OpenAiProperties(
                baseUrl, "test-key", "test-model", Duration.ofSeconds(2), readTimeout, 200, null
        ));
    }

    private ObjectNode schema() {
        ObjectNode schema = objectMapper.createObjectNode().put("type", "object");
        schema.set("properties", objectMapper.createObjectNode());
        schema.set("required", objectMapper.createArrayNode());
        schema.put("additionalProperties", false);
        return schema;
    }

    private String completedResponse(String escapedJsonText) {
        return "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":["
                + "{\"type\":\"output_text\",\"text\":\"" + escapedJsonText + "\"}]}]}";
    }

    private JsonNode readRecordedBody() {
        try {
            return objectMapper.readTree(recordedBody.get());
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    /**
     * 응답을 늦춘다. 잠드는 대신 teardown 래치를 기다린다.
     *
     * <p>고정 수면으로 늦추면 테스트가 끝난 뒤에도 핸들러 스레드가 남는다. 래치를 쓰면 대기 시간을
     * 넉넉하게 잡아도 {@link #stopServer()}에서 즉시 깨어난다.
     */
    private void awaitUntilTeardown(int millis) {
        if (millis <= 0) {
            return;
        }
        try {
            teardown.await(millis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
