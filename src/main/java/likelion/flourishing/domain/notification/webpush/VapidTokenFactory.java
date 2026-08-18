package likelion.flourishing.domain.notification.webpush;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * RFC 8292 VAPID Authorization 헤더를 만든다.
 *
 * <p>{@code Authorization: vapid t=<JWT>, k=<공개키>} 형식이고 JWT는 ES256으로 서명한다.
 * ES256 서명은 DER이 아니라 r || s 64바이트여야 해서 JDK의 P1363 형식 서명을 쓴다.
 *
 * <p>aud는 endpoint의 origin이다. 경로까지 넣으면 push 서비스가 토큰을 거부한다.
 */
@Component
public class VapidTokenFactory {

    /** RFC 8292는 24시간을 넘지 못하게 한다. 시계 오차를 감안해 절반만 쓴다. */
    private static final Duration TOKEN_LIFETIME = Duration.ofHours(12);

    private static final String JWT_HEADER = "{\"typ\":\"JWT\",\"alg\":\"ES256\"}";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final PushNotificationProperties properties;

    public VapidTokenFactory(PushNotificationProperties properties) {
        this.properties = properties;
    }

    /** endpoint 하나에 쓸 Authorization 헤더 값. */
    public String authorizationHeader(URI endpoint, Instant now) {
        if (!properties.vapidConfigured()) {
            throw new IllegalStateException("VAPID 키가 설정되지 않아 Web Push를 보낼 수 없습니다.");
        }

        String claims = "{\"aud\":\"%s\",\"exp\":%d,\"sub\":\"%s\"}".formatted(
                audience(endpoint),
                now.plus(TOKEN_LIFETIME).getEpochSecond(),
                escape(properties.vapid().subject())
        );
        String signingInput = encode(JWT_HEADER.getBytes(StandardCharsets.UTF_8))
                + "." + encode(claims.getBytes(StandardCharsets.UTF_8));
        String token = signingInput + "." + encode(sign(signingInput));

        return "vapid t=" + token + ", k=" + properties.vapid().publicKey();
    }

    /** endpoint의 origin. 포트가 기본값이 아니면 함께 넣는다. */
    static String audience(URI endpoint) {
        if (endpoint.getScheme() == null || endpoint.getHost() == null) {
            throw new IllegalArgumentException("Push endpoint에서 origin을 읽을 수 없습니다.");
        }
        StringBuilder audience = new StringBuilder()
                .append(endpoint.getScheme())
                .append("://")
                .append(endpoint.getHost());
        if (endpoint.getPort() != -1) {
            audience.append(':').append(endpoint.getPort());
        }
        return audience.toString();
    }

    private byte[] sign(String signingInput) {
        ECPrivateKey privateKey = P256Keys.privateKey(DECODER.decode(properties.vapid().privateKey()));
        try {
            // DER 대신 r || s 고정 길이 서명을 내보내는 형식이다. JWS ES256이 이 형식을 요구한다.
            Signature signature = Signature.getInstance("SHA256withECDSAinP1363Format");
            signature.initSign(privateKey);
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signature.sign();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("VAPID 토큰에 서명하지 못했습니다.", exception);
        }
    }

    private static String encode(byte[] value) {
        return ENCODER.encodeToString(value);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
