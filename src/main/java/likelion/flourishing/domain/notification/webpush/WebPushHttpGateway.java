package likelion.flourishing.domain.notification.webpush;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * RFC 8030 Web Push HTTP 호출.
 *
 * <p>본문은 RFC 8291 aes128gcm 암호문이고 헤더에 VAPID 토큰을 실어 보낸다.
 * 응답 상태만 보고 결과를 나눈다. 404와 410은 구독이 영구히 사라진 신호라 따로 구분한다.
 *
 * <p>예외를 밖으로 던지지 않는다. 한 사용자에게 실패해도 나머지 사용자 발송은 계속돼야 하고,
 * 실패 사유는 이력 테이블에 코드로 남는다. 로그에는 endpoint를 남기지 않는다.
 */
@Component
public class WebPushHttpGateway implements WebPushGateway {

    private static final Logger log = LoggerFactory.getLogger(WebPushHttpGateway.class);

    private static final String CONTENT_ENCODING_AES128GCM = "aes128gcm";
    private static final String TTL_HEADER = "TTL";
    private static final String URGENCY_HEADER = "Urgency";
    private static final String URGENCY_NORMAL = "normal";

    private final RestClient restClient;
    private final WebPushPayloadEncryption payloadEncryption;
    private final VapidTokenFactory vapidTokenFactory;
    private final PushNotificationProperties properties;
    private final Clock clock;

    public WebPushHttpGateway(
            WebPushPayloadEncryption payloadEncryption,
            VapidTokenFactory vapidTokenFactory,
            PushNotificationProperties properties,
            Clock clock
    ) {
        this.payloadEncryption = payloadEncryption;
        this.vapidTokenFactory = vapidTokenFactory;
        this.properties = properties;
        this.clock = clock;
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory(properties))
                .build();
    }

    @Override
    public WebPushResult send(WebPushMessage message) {
        URI endpoint;
        byte[] body;
        String authorization;
        try {
            endpoint = URI.create(message.endpoint());
            body = payloadEncryption.encrypt(
                    message.userAgentPublicKey(), message.authSecret(), message.payload()
            );
            authorization = vapidTokenFactory.authorizationHeader(endpoint, clock.instant());
        } catch (InvalidPushKeyException exception) {
            log.warn("Push 구독 키가 올바르지 않아 발송을 건너뜁니다. reason={}", exception.getMessage());
            return WebPushResult.failed("INVALID_SUBSCRIPTION_KEY");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            log.warn("Push 요청을 만들지 못했습니다. reason={}", exception.getMessage());
            return WebPushResult.failed("PUSH_REQUEST_NOT_BUILT");
        }

        try {
            return restClient.post()
                    .uri(endpoint)
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .header(HttpHeaders.CONTENT_ENCODING, CONTENT_ENCODING_AES128GCM)
                    .header(TTL_HEADER, String.valueOf(properties.ttlSeconds()))
                    .header(URGENCY_HEADER, URGENCY_NORMAL)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(body)
                    .exchange((request, response) -> classify(response.getStatusCode().value()));
        } catch (Exception exception) {
            log.warn("Push 서비스에 닿지 못했습니다. reason={}", exception.getMessage());
            return WebPushResult.failed("PUSH_SERVICE_UNREACHABLE");
        }
    }

    /**
     * 상태 코드를 결과로 바꾼다.
     *
     * <p>404와 410만 구독 만료로 본다. 그 밖의 4xx는 우리 요청이 잘못됐을 가능성이 있어
     * 구독을 지우지 않고 오류만 남긴다.
     */
    private WebPushResult classify(int statusCode) {
        if (statusCode >= 200 && statusCode < 300) {
            return WebPushResult.success();
        }
        String errorCode = "HTTP_" + statusCode;
        if (statusCode == 404 || statusCode == 410) {
            return WebPushResult.expired(errorCode);
        }
        return WebPushResult.failed(errorCode);
    }

    private static JdkClientHttpRequestFactory requestFactory(PushNotificationProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }
}
