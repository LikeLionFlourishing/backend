package likelion.flourishing.domain.notification.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EndpointFingerprintTest {

    private static final String TEST_KEY = "OTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTBmZWRjYmE=";
    private static final String OTHER_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String ENDPOINT = "https://push.example.net/push/JzLQ3raZJfFBR0aqvOMsLrt54w4rJUsV";

    private final EndpointFingerprint fingerprint =
            new EndpointFingerprint(new NotificationCryptoProperties(TEST_KEY));

    @Test
    void sameEndpointProducesSameFingerprint() {
        assertThat(fingerprint.of(ENDPOINT)).isEqualTo(fingerprint.of(ENDPOINT));
    }

    @Test
    void fingerprintIs32BytesForUniqueConstraint() {
        assertThat(fingerprint.of(ENDPOINT)).hasSize(32);
    }

    @Test
    void differentEndpointProducesDifferentFingerprint() {
        assertThat(fingerprint.of(ENDPOINT)).isNotEqualTo(fingerprint.of(ENDPOINT + "x"));
    }

    @Test
    void differentMasterKeyProducesDifferentFingerprint() {
        EndpointFingerprint other = new EndpointFingerprint(new NotificationCryptoProperties(OTHER_KEY));

        assertThat(fingerprint.of(ENDPOINT)).isNotEqualTo(other.of(ENDPOINT));
    }

    @Test
    void hexIsReadableAndDoesNotRevealEndpoint() {
        String hex = EndpointFingerprint.toHex(fingerprint.of(ENDPOINT));

        assertThat(hex).hasSize(64).matches("[0-9a-f]{64}");
    }
}
