package likelion.flourishing.domain.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * 세션 토큰과 CSRF 토큰을 만들고 검증한다.
 *
 * <p>쿠키로 나가는 세션 토큰은 32바이트 난수이고, DB에는 SHA-256 해시만 남긴다.
 * CSRF 토큰은 세션 토큰을 키로 쓰는 HMAC 결과라 요청마다 다시 계산할 수 있고,
 * 반대로 CSRF 토큰에서 세션 토큰을 되찾을 수는 없다.
 */
@Component
public class SessionTokenFactory {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String DIGEST_ALGORITHM = "SHA-256";
    private static final byte[] CSRF_DERIVATION_INFO = "csrf-token".getBytes(StandardCharsets.UTF_8);
    private static final int SESSION_TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

    public String createSessionToken() {
        byte[] token = new byte[SESSION_TOKEN_BYTES];
        random.nextBytes(token);
        return encoder.encodeToString(token);
    }

    public byte[] hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
            return digest.digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException(DIGEST_ALGORITHM + " 알고리즘을 사용할 수 없습니다.", unavailable);
        }
    }

    /** 같은 세션 토큰에서는 항상 같은 CSRF 토큰이 나오므로 세션 조회 응답이 값을 다시 돌려줄 수 있다. */
    public String deriveCsrfToken(String sessionToken) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(sessionToken.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return encoder.encodeToString(mac.doFinal(CSRF_DERIVATION_INFO));
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException unavailable) {
            throw new IllegalStateException(HMAC_ALGORITHM + " 알고리즘을 사용할 수 없습니다.", unavailable);
        }
    }

    /** 타이밍 공격을 피하려고 길이가 달라도 상수 시간에 가깝게 비교한다. */
    public boolean matches(byte[] storedHash, String presentedToken) {
        if (presentedToken == null || presentedToken.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(storedHash, hash(presentedToken));
    }
}
