package likelion.flourishing.domain.report.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class RecordKeyDerivation {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private RecordKeyDerivation() {
    }

    public static byte[] derive(byte[] masterKey, String purpose) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(masterKey, HMAC_ALGORITHM));
            return mac.doFinal(purpose.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("기록 보호 키를 초기화하지 못했습니다.", exception);
        }
    }
}
