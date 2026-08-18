package likelion.flourishing.domain.report.idempotency;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import likelion.flourishing.domain.report.crypto.RecordCryptoProperties;
import likelion.flourishing.domain.report.crypto.RecordKeyDerivation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 저장하는 응답 본문용 AES-256-GCM 인증 암호화. 형식은 version || nonce || ciphertext+tag다.
 *
 * <p>기록 마스터 키에서 파생하지만 목적 문자열이 달라 원문 암호화 키와 다른 키가 된다. 한 키를
 * 여러 용도로 쓰면 한쪽이 새면 다른 쪽도 함께 새기 때문이다.
 */
@Component
public class IdempotencyPayloadCipher {

    private static final byte FORMAT_VERSION = 1;
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int MINIMUM_CIPHERTEXT_LENGTH = 16;
    private static final String KEY_PURPOSE = "flourishing:idempotency:response:v1";

    private final SecretKeySpec encryptionKey;
    private final SecureRandom secureRandom;

    @Autowired
    public IdempotencyPayloadCipher(RecordCryptoProperties properties) {
        this(properties, new SecureRandom());
    }

    IdempotencyPayloadCipher(RecordCryptoProperties properties, SecureRandom secureRandom) {
        byte[] key = RecordKeyDerivation.derive(properties.decodeMasterKey(), KEY_PURPOSE);
        this.encryptionKey = new SecretKeySpec(key, "AES");
        this.secureRandom = secureRandom;
    }

    public byte[] encrypt(String plaintext) {
        byte[] nonce = new byte[NONCE_LENGTH];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(1 + NONCE_LENGTH + ciphertext.length)
                    .put(FORMAT_VERSION)
                    .put(nonce)
                    .put(ciphertext)
                    .array();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("저장할 응답 본문을 암호화하지 못했습니다.", exception);
        }
    }

    public String decrypt(byte[] encrypted) {
        if (encrypted == null
                || encrypted.length < 1 + NONCE_LENGTH + MINIMUM_CIPHERTEXT_LENGTH
                || encrypted[0] != FORMAT_VERSION) {
            throw new IllegalStateException("저장된 응답 암호문의 형식이 올바르지 않습니다.");
        }

        ByteBuffer buffer = ByteBuffer.wrap(encrypted);
        buffer.get();
        byte[] nonce = new byte[NONCE_LENGTH];
        buffer.get(nonce);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("저장된 응답 암호문을 인증하지 못했습니다.", exception);
        }
    }
}
