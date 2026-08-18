package likelion.flourishing.domain.report.crypto;

import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 피부 원문 암호화와 기록 커서 서명에 쓰는 32바이트 마스터 키 설정. */
@ConfigurationProperties(prefix = "app.records.crypto")
public record RecordCryptoProperties(String masterKey) {

    public byte[] decodeMasterKey() {
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalStateException("RECORD_DATA_ENCRYPTION_KEY must be configured");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(masterKey);
            if (decoded.length != 32) {
                throw new IllegalStateException("RECORD_DATA_ENCRYPTION_KEY must decode to 32 bytes");
            }
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("RECORD_DATA_ENCRYPTION_KEY must be valid Base64", exception);
        }
    }
}
