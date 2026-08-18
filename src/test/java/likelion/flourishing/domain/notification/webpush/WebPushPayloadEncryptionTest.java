package likelion.flourishing.domain.notification.webpush;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * RFC 8291 5절과 부록 A의 예시 값으로 검증한다.
 *
 * <p>발신자 키와 salt를 무작위로 만들면 결과가 매번 달라져 정답과 비교할 수 없다. 그래서
 * 두 값을 주입할 수 있게 만들고 RFC의 값을 그대로 넣는다. 이 테스트가 통과하면 키 파생 순서,
 * 헤더 구성, 패딩 구분자, AES-GCM 사용법이 모두 표준과 같다는 뜻이다.
 */
class WebPushPayloadEncryptionTest {

    private static final String PLAINTEXT = "When I grow up, I want to be a watermelon";
    private static final String UA_PUBLIC =
            "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String AS_PUBLIC =
            "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    private static final String AS_PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
    private static final String AUTH_SECRET = "BTBZMqHH6r4Tts7J_aSIgg";
    private static final String SALT = "DGv6ra1nlYgDCS1FRnbzlw";
    private static final String EXPECTED_BODY =
            "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27ml"
                    + "mlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPT"
                    + "pK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN";

    @Test
    void matchesRfc8291Example() {
        WebPushPayloadEncryption encryption = new WebPushPayloadEncryption(
                () -> new KeyPair(P256Keys.publicKey(decode(AS_PUBLIC)), P256Keys.privateKey(decode(AS_PRIVATE))),
                () -> decode(SALT)
        );

        byte[] body = encryption.encrypt(
                decode(UA_PUBLIC), decode(AUTH_SECRET), PLAINTEXT.getBytes(StandardCharsets.US_ASCII)
        );

        assertThat(encode(body)).isEqualTo(EXPECTED_BODY);
    }

    @Test
    void headerCarriesSaltRecordSizeAndSenderKey() {
        WebPushPayloadEncryption encryption = new WebPushPayloadEncryption(
                () -> new KeyPair(P256Keys.publicKey(decode(AS_PUBLIC)), P256Keys.privateKey(decode(AS_PRIVATE))),
                () -> decode(SALT)
        );

        byte[] body = encryption.encrypt(
                decode(UA_PUBLIC), decode(AUTH_SECRET), PLAINTEXT.getBytes(StandardCharsets.US_ASCII)
        );

        assertThat(Arrays.copyOf(body, 16)).isEqualTo(decode(SALT));
        assertThat(Arrays.copyOfRange(body, 16, 20)).isEqualTo(new byte[]{0x00, 0x00, 0x10, 0x00});
        assertThat(body[20]).isEqualTo((byte) 65);
        assertThat(Arrays.copyOfRange(body, 21, 86)).isEqualTo(decode(AS_PUBLIC));
    }

    @Test
    void randomSenderKeyProducesDifferentBodyEachTime() {
        WebPushPayloadEncryption encryption = new WebPushPayloadEncryption();
        byte[] plaintext = PLAINTEXT.getBytes(StandardCharsets.US_ASCII);

        byte[] first = encryption.encrypt(decode(UA_PUBLIC), decode(AUTH_SECRET), plaintext);
        byte[] second = encryption.encrypt(decode(UA_PUBLIC), decode(AUTH_SECRET), plaintext);

        assertThat(first).isNotEqualTo(second);
        assertThat(first).hasSameSizeAs(second);
    }

    @Test
    void authSecretMustBe16Bytes() {
        WebPushPayloadEncryption encryption = new WebPushPayloadEncryption();

        assertThatThrownBy(() -> encryption.encrypt(decode(UA_PUBLIC), new byte[8], new byte[]{1}))
                .isInstanceOf(InvalidPushKeyException.class);
    }

    @Test
    void plaintextLongerThanOneRecordIsRejected() {
        WebPushPayloadEncryption encryption = new WebPushPayloadEncryption();
        byte[] tooLong = new byte[WebPushPayloadEncryption.MAX_PLAINTEXT_LENGTH + 1];

        assertThatThrownBy(() -> encryption.encrypt(decode(UA_PUBLIC), decode(AUTH_SECRET), tooLong))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publicKeyOffTheCurveIsRejected() {
        WebPushPayloadEncryption encryption = new WebPushPayloadEncryption();
        byte[] tampered = decode(UA_PUBLIC);
        tampered[64] ^= 1;

        assertThatThrownBy(() -> encryption.encrypt(tampered, decode(AUTH_SECRET), new byte[]{1}))
                .isInstanceOf(InvalidPushKeyException.class);
    }

    private static byte[] decode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
