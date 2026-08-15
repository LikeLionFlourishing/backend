package likelion.flourishing.domain.notification.webpush;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import likelion.flourishing.domain.notification.webpush.PushNotificationProperties.Vapid;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 실제 HTTP 왕복으로 헤더와 본문, 상태 코드 처리를 확인한다.
 *
 * <p>JDK에 들어 있는 {@link HttpServer}를 쓴다. 외부 목 서버 의존성을 추가하지 않고도
 * 요청을 그대로 받아 볼 수 있다.
 */
class WebPushHttpGatewayTest {

    private static final String UA_PUBLIC =
            "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String AUTH_SECRET = "BTBZMqHH6r4Tts7J_aSIgg";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-15T08:30:00Z"), ZoneOffset.UTC);

    private HttpServer server;
    private String endpoint;
    private final AtomicInteger responseStatus = new AtomicInteger(201);
    private final AtomicReference<RecordedRequest> recorded = new AtomicReference<>();

    private WebPushHttpGateway gateway;
    private String vapidPublicKey;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/push", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            recorded.set(new RecordedRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("Content-Encoding"),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    exchange.getRequestHeaders().getFirst("TTL"),
                    body
            ));
            exchange.sendResponseHeaders(responseStatus.get(), -1);
            exchange.close();
        });
        server.start();
        endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/push/abc";

        KeyPair vapidKeyPair = P256Keys.generateKeyPair();
        vapidPublicKey = encode(P256Keys.uncompressed((ECPublicKey) vapidKeyPair.getPublic()));
        gateway = newGateway(vapidKeyPair, Duration.ofSeconds(2));
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void successfulPushCarriesVapidHeaderAndEncryptedBody() {
        responseStatus.set(201);

        WebPushResult result = gateway.send(message());

        assertThat(result.isSuccess()).isTrue();
        RecordedRequest request = recorded.get();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.authorization()).startsWith("vapid t=").contains(", k=" + vapidPublicKey);
        assertThat(request.contentEncoding()).isEqualTo("aes128gcm");
        assertThat(request.contentType()).isEqualTo("application/octet-stream");
        assertThat(request.ttl()).isEqualTo("3600");
    }

    @Test
    void bodyIsRfc8291HeaderPlusCiphertextAndNotPlaintext() {
        responseStatus.set(201);

        gateway.send(message());

        byte[] body = recorded.get().body();
        assertThat(body.length).isGreaterThan(86);
        assertThat(body[20]).isEqualTo((byte) 65);
        assertThat(new String(body, StandardCharsets.ISO_8859_1)).doesNotContain("FOLLOW_UP");
    }

    @Test
    void gonePushMarksSubscriptionExpired() {
        responseStatus.set(410);

        WebPushResult result = gateway.send(message());

        assertThat(result.isExpired()).isTrue();
        assertThat(result.errorCode()).isEqualTo("HTTP_410");
    }

    @Test
    void notFoundPushMarksSubscriptionExpired() {
        responseStatus.set(404);

        assertThat(gateway.send(message()).isExpired()).isTrue();
    }

    @Test
    void serverErrorIsTemporaryFailure() {
        responseStatus.set(500);

        WebPushResult result = gateway.send(message());

        assertThat(result.outcome()).isEqualTo(WebPushOutcome.FAILED);
        assertThat(result.errorCode()).isEqualTo("HTTP_500");
    }

    @Test
    void tooManyRequestsIsTemporaryFailure() {
        responseStatus.set(429);

        WebPushResult result = gateway.send(message());

        assertThat(result.outcome()).isEqualTo(WebPushOutcome.FAILED);
        assertThat(result.errorCode()).isEqualTo("HTTP_429");
    }

    @Test
    void unreachableServiceIsFailureWithoutException() {
        server.stop(0);

        WebPushResult result = gateway.send(message());

        assertThat(result.outcome()).isEqualTo(WebPushOutcome.FAILED);
        assertThat(result.errorCode()).isEqualTo("PUSH_SERVICE_UNREACHABLE");
    }

    @Test
    void brokenSubscriptionKeyIsFailureWithoutException() {
        WebPushResult result = gateway.send(new WebPushMessage(
                endpoint, new byte[]{0x04}, decode(AUTH_SECRET), payload()
        ));

        assertThat(result.outcome()).isEqualTo(WebPushOutcome.FAILED);
        assertThat(result.errorCode()).isEqualTo("INVALID_SUBSCRIPTION_KEY");
    }

    @Test
    void missingVapidConfigurationIsFailureWithoutException() {
        WebPushHttpGateway unconfigured = new WebPushHttpGateway(
                new WebPushPayloadEncryption(),
                new VapidTokenFactory(properties(null, null)),
                properties(null, null),
                CLOCK
        );

        WebPushResult result = unconfigured.send(message());

        assertThat(result.outcome()).isEqualTo(WebPushOutcome.FAILED);
        assertThat(result.errorCode()).isEqualTo("PUSH_REQUEST_NOT_BUILT");
    }

    private WebPushMessage message() {
        return new WebPushMessage(endpoint, decode(UA_PUBLIC), decode(AUTH_SECRET), payload());
    }

    private byte[] payload() {
        return "{\"type\":\"FOLLOW_UP\",\"title\":\"어제 피부는 어땠나요?\"}".getBytes(StandardCharsets.UTF_8);
    }

    private WebPushHttpGateway newGateway(KeyPair vapidKeyPair, Duration timeout) {
        PushNotificationProperties properties = properties(
                encode(P256Keys.uncompressed((ECPublicKey) vapidKeyPair.getPublic())),
                encode(scalar((ECPrivateKey) vapidKeyPair.getPrivate()))
        );
        return new WebPushHttpGateway(
                new WebPushPayloadEncryption(),
                new VapidTokenFactory(properties),
                properties,
                CLOCK
        );
    }

    private PushNotificationProperties properties(String publicKey, String privateKey) {
        return new PushNotificationProperties(
                new Vapid(publicKey, privateKey, "mailto:ops@example.invalid"),
                Duration.ofHours(1),
                Duration.ofSeconds(2),
                Duration.ofSeconds(2)
        );
    }

    private static byte[] scalar(ECPrivateKey privateKey) {
        byte[] value = privateKey.getS().toByteArray();
        byte[] scalar = new byte[32];
        if (value.length > 32) {
            System.arraycopy(value, value.length - 32, scalar, 0, 32);
            return scalar;
        }
        System.arraycopy(value, 0, scalar, 32 - value.length, value.length);
        return scalar;
    }

    private static byte[] decode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private record RecordedRequest(
            String method,
            String authorization,
            String contentEncoding,
            String contentType,
            String ttl,
            byte[] body
    ) {
    }
}
