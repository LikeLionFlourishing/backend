package likelion.flourishing.domain.notification.crypto;

import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Push 구독 비밀값 암호화와 endpoint 지문 계산에 쓰는 32바이트 마스터 키 설정.
 *
 * <p>피부 원문 키(app.records.crypto)와 분리한다. 알림 구독은 capability 정보라
 * 유출 영향 범위가 다르고, 키를 따로 돌려야 하기 때문이다.
 */
@ConfigurationProperties(prefix = "app.notifications.crypto")
public record NotificationCryptoProperties(String masterKey) {

    public byte[] decodeMasterKey() {
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalStateException("PUSH_DATA_ENCRYPTION_KEY must be configured");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(masterKey);
            if (decoded.length != 32) {
                throw new IllegalStateException("PUSH_DATA_ENCRYPTION_KEY must decode to 32 bytes");
            }
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("PUSH_DATA_ENCRYPTION_KEY must be valid Base64", exception);
        }
    }
}
