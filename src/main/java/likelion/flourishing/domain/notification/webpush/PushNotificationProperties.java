package likelion.flourishing.domain.notification.webpush;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Web Push 발송 설정.
 *
 * <p>VAPID 키가 비어 있으면 발송 기능만 멈추고 애플리케이션은 그대로 뜬다. 로컬 개발과 테스트에서
 * 키 없이도 나머지 기능을 쓸 수 있어야 하기 때문이다. 대신 스케줄러가 경고를 남기고 건너뛴다.
 */
@ConfigurationProperties(prefix = "app.notifications.push")
public record PushNotificationProperties(
        Vapid vapid,
        Duration ttl,
        Duration connectTimeout,
        Duration readTimeout,
        List<String> allowedEndpointHosts
) {

    /**
     * 등록을 허용하는 push 서비스 호스트. 목록이 비면 호스트 제한 없이 사설·loopback 대역만 막는다.
     *
     * <p>브라우저별 endpoint 호스트다. Chrome·Edge는 FCM, Firefox는 Mozilla autopush,
     * Safari는 Apple, 구형 Edge는 WNS를 쓴다.
     */
    private static final List<String> DEFAULT_ALLOWED_ENDPOINT_HOSTS = List.of(
            "fcm.googleapis.com",
            "push.services.mozilla.com",
            "web.push.apple.com",
            "notify.windows.com"
    );

    public PushNotificationProperties {
        vapid = vapid == null ? new Vapid(null, null, null) : vapid;
        ttl = ttl == null ? Duration.ofHours(1) : ttl;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(5) : readTimeout;
        allowedEndpointHosts = allowedEndpointHosts == null
                ? DEFAULT_ALLOWED_ENDPOINT_HOSTS
                : List.copyOf(allowedEndpointHosts);
    }

    public boolean vapidConfigured() {
        return vapid.isConfigured();
    }

    public long ttlSeconds() {
        return ttl.toSeconds();
    }

    /**
     * VAPID 자격 증명.
     *
     * <p>publicKey는 비압축 65바이트, privateKey는 32바이트 스칼라를 각각 패딩 없는 base64url로
     * 인코딩한 값이다. subject는 push 서비스가 연락할 수 있는 mailto: 또는 https: URI다.
     */
    public record Vapid(String publicKey, String privateKey, String subject) {

        public boolean isConfigured() {
            return hasText(publicKey) && hasText(privateKey) && hasText(subject);
        }

        private static boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }
}
