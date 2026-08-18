package likelion.flourishing.domain.notification.webpush;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.Base64;
import likelion.flourishing.domain.notification.webpush.PushNotificationProperties.Vapid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** RFC 8292 Authorization 헤더 형식과 ES256 서명이 공개키로 검증되는지 확인한다. */
class VapidTokenFactoryTest {

    private static final Instant NOW = Instant.parse("2026-08-15T08:30:00Z");
    private static final String SUBJECT = "mailto:ops@example.invalid";

    private KeyPair keyPair;
    private String publicKey;
    private VapidTokenFactory factory;

    @BeforeEach
    void setUp() {
        keyPair = P256Keys.generateKeyPair();
        publicKey = encode(P256Keys.uncompressed((ECPublicKey) keyPair.getPublic()));
        String privateKey = encode(scalar((ECPrivateKey) keyPair.getPrivate()));
        factory = new VapidTokenFactory(properties(publicKey, privateKey, SUBJECT));
    }

    @Test
    void headerCarriesTokenAndPublicKey() {
        String header = factory.authorizationHeader(URI.create("https://push.example.net/push/abc"), NOW);

        assertThat(header).startsWith("vapid t=").contains(", k=" + publicKey);
    }

    @Test
    void claimsUseEndpointOriginAndBoundedExpiry() {
        String header = factory.authorizationHeader(URI.create("https://push.example.net/push/abc"), NOW);
        String claims = new String(
                Base64.getUrlDecoder().decode(token(header).split("\\.")[1]), StandardCharsets.UTF_8
        );

        assertThat(claims).contains("\"aud\":\"https://push.example.net\"");
        assertThat(claims).contains("\"sub\":\"" + SUBJECT + "\"");
        assertThat(claims).contains("\"exp\":" + NOW.plusSeconds(12 * 3600).getEpochSecond());
    }

    @Test
    void headerUsesEs256Algorithm() {
        String header = factory.authorizationHeader(URI.create("https://push.example.net/push/abc"), NOW);
        String jwtHeader = new String(
                Base64.getUrlDecoder().decode(token(header).split("\\.")[0]), StandardCharsets.UTF_8
        );

        assertThat(jwtHeader).isEqualTo("{\"typ\":\"JWT\",\"alg\":\"ES256\"}");
    }

    @Test
    void signatureVerifiesWithPublicKey() throws Exception {
        String header = factory.authorizationHeader(URI.create("https://push.example.net/push/abc"), NOW);
        String[] parts = token(header).split("\\.");

        Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        verifier.initVerify(keyPair.getPublic());
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));

        assertThat(parts[2]).isNotBlank();
        assertThat(verifier.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
    }

    @Test
    void audienceKeepsNonDefaultPort() {
        assertThat(VapidTokenFactory.audience(URI.create("https://localhost:8443/push/abc")))
                .isEqualTo("https://localhost:8443");
    }

    @Test
    void missingVapidConfigurationIsRejected() {
        VapidTokenFactory unconfigured = new VapidTokenFactory(properties(null, null, null));

        assertThatThrownBy(() -> unconfigured.authorizationHeader(URI.create("https://push.example.net/p"), NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    private static String token(String header) {
        return header.substring("vapid t=".length(), header.indexOf(", k="));
    }

    private static PushNotificationProperties properties(String publicKey, String privateKey, String subject) {
        return new PushNotificationProperties(new Vapid(publicKey, privateKey, subject), null, null, null, null);
    }

    /** ECPrivateKey의 스칼라를 32바이트 고정 길이로 만든다. VAPID 설정이 쓰는 표현이다. */
    private static byte[] scalar(ECPrivateKey privateKey) {
        byte[] value = privateKey.getS().toByteArray();
        byte[] scalar = new byte[32];
        if (value.length > 32) {
            System.arraycopy(value, value.length - 32, scalar, 0, 32);
            return scalar;
        }
        System.arraycopy(value, 0, scalar, 32 - value.length, value.length);
        return scalar;
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
