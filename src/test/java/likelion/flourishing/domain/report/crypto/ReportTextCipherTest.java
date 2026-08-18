package likelion.flourishing.domain.report.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ReportTextCipherTest {

    private static final String TEST_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private final ReportTextCipher cipher = new ReportTextCipher(new RecordCryptoProperties(TEST_KEY));

    @Test
    void encryptAndDecryptRoundTrip() {
        byte[] encrypted = cipher.encrypt("오른쪽 턱이 빨갛고 따가워요.");

        assertThat(cipher.decrypt(encrypted)).isEqualTo("오른쪽 턱이 빨갛고 따가워요.");
    }

    @Test
    void samePlaintextUsesDifferentNonce() {
        byte[] first = cipher.encrypt("같은 원문");
        byte[] second = cipher.encrypt("같은 원문");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void tamperedCiphertextIsRejected() {
        byte[] encrypted = cipher.encrypt("민감한 원문");
        encrypted[encrypted.length - 1] ^= 1;

        assertThatThrownBy(() -> cipher.decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void invalidMasterKeyLengthIsRejected() {
        RecordCryptoProperties invalid = new RecordCryptoProperties("c2hvcnQ=");

        assertThatThrownBy(() -> new ReportTextCipher(invalid))
                .isInstanceOf(IllegalStateException.class);
    }
}
