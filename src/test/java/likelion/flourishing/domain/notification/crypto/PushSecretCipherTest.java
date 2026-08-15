package likelion.flourishing.domain.notification.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PushSecretCipherTest {

    private static final String TEST_KEY = "OTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTBmZWRjYmE=";
    private static final String ENDPOINT = "https://push.example.net/push/JzLQ3raZJfFBR0aqvOMsLrt54w4rJUsV";

    private final PushSecretCipher cipher = new PushSecretCipher(new NotificationCryptoProperties(TEST_KEY));

    @Test
    void endpointRoundTrips() {
        byte[] encrypted = cipher.encryptText(ENDPOINT);

        assertThat(cipher.decryptText(encrypted)).isEqualTo(ENDPOINT);
    }

    @Test
    void binaryKeyRoundTrips() {
        byte[] authSecret = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);

        assertThat(cipher.decrypt(cipher.encrypt(authSecret))).isEqualTo(authSecret);
    }

    @Test
    void ciphertextDoesNotContainPlaintext() {
        byte[] encrypted = cipher.encryptText(ENDPOINT);

        assertThat(new String(encrypted, StandardCharsets.ISO_8859_1)).doesNotContain("push.example.net");
    }

    @Test
    void samePlaintextUsesDifferentNonce() {
        assertThat(cipher.encryptText(ENDPOINT)).isNotEqualTo(cipher.encryptText(ENDPOINT));
    }

    @Test
    void tamperedCiphertextIsRejected() {
        byte[] encrypted = cipher.encryptText(ENDPOINT);
        encrypted[encrypted.length - 1] ^= 1;

        assertThatThrownBy(() -> cipher.decryptText(encrypted))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void invalidMasterKeyLengthIsRejected() {
        NotificationCryptoProperties invalid = new NotificationCryptoProperties("c2hvcnQ=");

        assertThatThrownBy(() -> new PushSecretCipher(invalid))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void missingMasterKeyIsRejected() {
        NotificationCryptoProperties missing = new NotificationCryptoProperties("");

        assertThatThrownBy(() -> new PushSecretCipher(missing))
                .isInstanceOf(IllegalStateException.class);
    }
}
