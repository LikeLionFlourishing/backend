package likelion.flourishing.domain.report.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 피부 보고 원문용 AES-256-GCM 인증 암호화. 형식은 version || nonce || ciphertext+tag다. */
@Component
public class ReportTextCipher {

    private static final byte FORMAT_VERSION = 1;
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int MINIMUM_CIPHERTEXT_LENGTH = 16;
    private static final String KEY_PURPOSE = "flourishing:records:text:v1";

    private final SecretKeySpec encryptionKey;
    private final SecureRandom secureRandom;

    @Autowired
    public ReportTextCipher(RecordCryptoProperties properties) {
        this(properties, new SecureRandom());
    }

    ReportTextCipher(RecordCryptoProperties properties, SecureRandom secureRandom) {
        byte[] key = RecordKeyDerivation.derive(properties.decodeMasterKey(), KEY_PURPOSE);
        this.encryptionKey = new SecretKeySpec(key, "AES");
        this.secureRandom = secureRandom;
    }

    public byte[] encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
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
            throw new IllegalStateException("피부 보고 원문을 암호화하지 못했습니다.", exception);
        }
    }

    public String decrypt(byte[] encrypted) {
        if (encrypted == null) {
            return null;
        }
        if (encrypted.length < 1 + NONCE_LENGTH + MINIMUM_CIPHERTEXT_LENGTH
                || encrypted[0] != FORMAT_VERSION) {
            throw new IllegalStateException("피부 보고 암호문의 형식이 올바르지 않습니다.");
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
            throw new IllegalStateException("피부 보고 암호문을 인증하지 못했습니다.", exception);
        }
    }
}
