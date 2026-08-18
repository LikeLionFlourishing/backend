package likelion.flourishing.domain.notification.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * endpoint의 32바이트 지문. push_subscriptions의 (user_id, endpoint_fingerprint) 유니크 제약이
 * 같은 endpoint 재등록을 갱신으로 처리할 수 있게 해 준다.
 *
 * <p>암호문은 매번 nonce가 달라 같은 endpoint인지 비교할 수 없다. 그래서 결정적인 지문을 따로 둔다.
 * 지문은 SHA-256을 그대로 쓰지 않고 마스터 키에서 파생한 키로 HMAC-SHA256을 계산한다.
 * endpoint는 후보 공간이 좁아(고정 호스트 + 토큰) 키 없는 해시는 사전 공격으로 되짚을 수 있기 때문이다.
 */
@Component
public class EndpointFingerprint {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String KEY_PURPOSE = "flourishing:push:fingerprint:v1";

    private final SecretKeySpec fingerprintKey;

    public EndpointFingerprint(NotificationCryptoProperties properties) {
        byte[] key = NotificationKeyDerivation.derive(properties.decodeMasterKey(), KEY_PURPOSE);
        this.fingerprintKey = new SecretKeySpec(key, HMAC_ALGORITHM);
    }

    public byte[] of(String endpoint) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(fingerprintKey);
            return mac.doFinal(endpoint.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Push endpoint 지문을 계산하지 못했습니다.", exception);
        }
    }

    /** 응답과 로그에 쓰는 표기. 원문 endpoint를 복원할 수 없는 값이라 노출해도 된다. */
    public static String toHex(byte[] fingerprint) {
        return HexFormat.of().formatHex(fingerprint);
    }
}
