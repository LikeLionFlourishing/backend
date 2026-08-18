package likelion.flourishing.domain.notification.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 하나의 마스터 키에서 용도별 키를 갈라 쓰기 위한 파생 함수.
 *
 * <p>같은 키를 암호화와 지문 계산에 함께 쓰면 한쪽이 깨질 때 다른 쪽도 함께 무너진다.
 * 목적 문자열을 HMAC 입력으로 넣어 서로 무관한 키를 만든다.
 */
public final class NotificationKeyDerivation {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private NotificationKeyDerivation() {
    }

    public static byte[] derive(byte[] masterKey, String purpose) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(masterKey, HMAC_ALGORITHM));
            return mac.doFinal(purpose.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("알림 보호 키를 초기화하지 못했습니다.", exception);
        }
    }
}
