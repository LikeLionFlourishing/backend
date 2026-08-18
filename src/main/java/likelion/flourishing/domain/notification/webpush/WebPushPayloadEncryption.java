package likelion.flourishing.domain.notification.webpush;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import java.util.function.Supplier;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * RFC 8291 "aes128gcm" Web Push 메시지 암호화.
 *
 * <p>본문은 header || ciphertext 하나의 레코드로만 만든다(RFC 8291 4절).
 * header는 salt(16) || rs(4) || keyid 길이(1) || 발신자 공개키(65)로 86바이트다.
 *
 * <p>키 파생은 RFC 8291 3.4절 순서를 그대로 따른다.
 * <pre>
 * PRK_key = HMAC(auth_secret, ecdh_secret)
 * IKM     = HMAC(PRK_key, "WebPush: info" || 0x00 || ua_public || as_public || 0x01)
 * PRK     = HMAC(salt, IKM)
 * CEK     = HMAC(PRK, "Content-Encoding: aes128gcm" || 0x00 || 0x01)[0..15]
 * NONCE   = HMAC(PRK, "Content-Encoding: nonce" || 0x00 || 0x01)[0..11]
 * </pre>
 *
 * <p>레코드가 하나뿐이라 시퀀스 번호가 0이고, nonce에 XOR을 할 필요가 없다.
 */
@Component
public class WebPushPayloadEncryption {

    /** rs 파라미터. 평문 + 구분자 + 태그보다 커야 한다(RFC 8291 4절). */
    static final int RECORD_SIZE = 4096;

    /** header(86) + 구분자(1) + AEAD 태그(16)를 뺀 나머지가 평문에 쓸 수 있는 최대 길이다. */
    static final int MAX_PLAINTEXT_LENGTH = RECORD_SIZE - 86 - 1 - 16;

    private static final int SALT_LENGTH = 16;
    private static final int CEK_LENGTH = 16;
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final byte PADDING_DELIMITER = 0x02;
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final byte[] KEY_INFO_PREFIX = "WebPush: info".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CEK_INFO = "Content-Encoding: aes128gcm".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] NONCE_INFO = "Content-Encoding: nonce".getBytes(StandardCharsets.US_ASCII);

    private final Supplier<KeyPair> senderKeyPairSupplier;
    private final Supplier<byte[]> saltSupplier;

    @Autowired
    public WebPushPayloadEncryption() {
        this(P256Keys::generateKeyPair, WebPushPayloadEncryption::randomSalt);
    }

    /** 테스트가 RFC 8291 5절 예시 값을 그대로 넣을 수 있게 발신자 키와 salt를 주입받는다. */
    WebPushPayloadEncryption(Supplier<KeyPair> senderKeyPairSupplier, Supplier<byte[]> saltSupplier) {
        this.senderKeyPairSupplier = senderKeyPairSupplier;
        this.saltSupplier = saltSupplier;
    }

    /**
     * 구독 공개키와 auth 비밀로 평문을 암호화해 Web Push 본문을 만든다.
     *
     * @param userAgentPublicKey 구독의 p256dh(비압축 65바이트)
     * @param authSecret         구독의 auth(16바이트)
     */
    public byte[] encrypt(byte[] userAgentPublicKey, byte[] authSecret, byte[] plaintext) {
        if (authSecret == null || authSecret.length != P256Keys.AUTH_SECRET_LENGTH) {
            throw new InvalidPushKeyException("auth 비밀은 16바이트여야 합니다.");
        }
        if (plaintext.length > MAX_PLAINTEXT_LENGTH) {
            throw new IllegalArgumentException("Web Push 평문이 한 레코드에 담을 수 있는 길이를 넘었습니다.");
        }

        ECPublicKey uaPublicKey = P256Keys.publicKey(userAgentPublicKey);
        KeyPair senderKeyPair = senderKeyPairSupplier.get();
        byte[] senderPublicKey = P256Keys.uncompressed((ECPublicKey) senderKeyPair.getPublic());
        byte[] ecdhSecret = P256Keys.sharedSecret((ECPrivateKey) senderKeyPair.getPrivate(), uaPublicKey);

        byte[] salt = saltSupplier.get();
        if (salt.length != SALT_LENGTH) {
            throw new IllegalStateException("Web Push salt는 16바이트여야 합니다.");
        }

        byte[] inputKeyingMaterial = inputKeyingMaterial(
                authSecret, ecdhSecret, userAgentPublicKey, senderPublicKey
        );
        byte[] pseudoRandomKey = hmac(salt, inputKeyingMaterial);
        byte[] contentEncryptionKey = Arrays.copyOf(
                hmac(pseudoRandomKey, concat(CEK_INFO, (byte) 0x00, (byte) 0x01)), CEK_LENGTH
        );
        byte[] nonce = Arrays.copyOf(
                hmac(pseudoRandomKey, concat(NONCE_INFO, (byte) 0x00, (byte) 0x01)), NONCE_LENGTH
        );

        byte[] ciphertext = seal(contentEncryptionKey, nonce, concat(plaintext, PADDING_DELIMITER));
        return body(salt, senderPublicKey, ciphertext);
    }

    /** ECDH 비밀과 auth 비밀을 합쳐 RFC 8188이 쓰는 입력 키 재료를 만든다(RFC 8291 3.3절). */
    private byte[] inputKeyingMaterial(
            byte[] authSecret,
            byte[] ecdhSecret,
            byte[] uaPublicKey,
            byte[] senderPublicKey
    ) {
        byte[] pseudoRandomKeyForCombining = hmac(authSecret, ecdhSecret);
        return hmac(pseudoRandomKeyForCombining, concat(keyInfo(uaPublicKey, senderPublicKey), (byte) 0x01));
    }

    private byte[] keyInfo(byte[] uaPublicKey, byte[] senderPublicKey) {
        return ByteBuffer.allocate(KEY_INFO_PREFIX.length + 1 + uaPublicKey.length + senderPublicKey.length)
                .put(KEY_INFO_PREFIX)
                .put((byte) 0x00)
                .put(uaPublicKey)
                .put(senderPublicKey)
                .array();
    }

    private byte[] body(byte[] salt, byte[] senderPublicKey, byte[] ciphertext) {
        return ByteBuffer.allocate(SALT_LENGTH + 4 + 1 + senderPublicKey.length + ciphertext.length)
                .put(salt)
                .putInt(RECORD_SIZE)
                .put((byte) senderPublicKey.length)
                .put(senderPublicKey)
                .put(ciphertext)
                .array();
    }

    private byte[] seal(byte[] key, byte[] nonce, byte[] record) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_LENGTH_BITS, nonce)
            );
            return cipher.doFinal(record);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Web Push 본문을 암호화하지 못했습니다.", exception);
        }
    }

    private byte[] hmac(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            return mac.doFinal(message);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Web Push 키를 파생하지 못했습니다.", exception);
        }
    }

    private static byte[] concat(byte[] head, byte... tail) {
        byte[] result = Arrays.copyOf(head, head.length + tail.length);
        System.arraycopy(tail, 0, result, head.length, tail.length);
        return result;
    }

    private static byte[] randomSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        new SecureRandom().nextBytes(salt);
        return salt;
    }
}
