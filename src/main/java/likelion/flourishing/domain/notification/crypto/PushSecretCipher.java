package likelion.flourishing.domain.notification.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Push 구독 endpoint와 키를 저장할 때 쓰는 AES-256-GCM 인증 암호화.
 * 형식은 version || nonce || ciphertext+tag다.
 *
 * <p>구독 endpoint는 그 자체가 사용자에게 알림을 보낼 수 있는 권한(capability)이다.
 * DB만 읽어도 알림을 보낼 수 있으면 안 되므로 평문으로 두지 않는다.
 */
@Component
public class PushSecretCipher {

    private static final byte FORMAT_VERSION = 1;
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int MINIMUM_CIPHERTEXT_LENGTH = 16;
    private static final String KEY_PURPOSE = "flourishing:push:secret:v1";

    private final SecretKeySpec encryptionKey;
    private final SecureRandom secureRandom;

    @Autowired
    public PushSecretCipher(NotificationCryptoProperties properties) {
        this(properties, new SecureRandom());
    }

    PushSecretCipher(NotificationCryptoProperties properties, SecureRandom secureRandom) {
        byte[] key = NotificationKeyDerivation.derive(properties.decodeMasterKey(), KEY_PURPOSE);
        this.encryptionKey = new SecretKeySpec(key, "AES");
        this.secureRandom = secureRandom;
    }

    public byte[] encryptText(String plaintext) {
        return encrypt(plaintext.getBytes(StandardCharsets.UTF_8));
    }

    public String decryptText(byte[] encrypted) {
        return new String(decrypt(encrypted), StandardCharsets.UTF_8);
    }

    public byte[] encrypt(byte[] plaintext) {
        byte[] nonce = new byte[NONCE_LENGTH];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext);
            return ByteBuffer.allocate(1 + NONCE_LENGTH + ciphertext.length)
                    .put(FORMAT_VERSION)
                    .put(nonce)
                    .put(ciphertext)
                    .array();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Push 구독 비밀값을 암호화하지 못했습니다.", exception);
        }
    }

    public byte[] decrypt(byte[] encrypted) {
        if (encrypted == null
                || encrypted.length < 1 + NONCE_LENGTH + MINIMUM_CIPHERTEXT_LENGTH
                || encrypted[0] != FORMAT_VERSION) {
            throw new IllegalStateException("Push 구독 암호문의 형식이 올바르지 않습니다.");
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
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Push 구독 암호문을 인증하지 못했습니다.", exception);
        }
    }
}
