package likelion.flourishing.domain.record.cursor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.UUID;
import likelion.flourishing.domain.report.crypto.RecordCryptoProperties;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

class SkinReportCursorCodecTest {

    private static final String TEST_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private final SkinReportCursorCodec codec = new SkinReportCursorCodec(new RecordCryptoProperties(TEST_KEY));

    @Test
    void cursorRoundTripPreservesTimestampAndId() {
        SkinReportCursor cursor = new SkinReportCursor(
                LocalDateTime.of(2026, 8, 15, 3, 10, 20, 123_456_000),
                UUID.fromString("0198a31f-f33f-7000-8000-000000000001")
        );

        assertThat(codec.decode(codec.encode(cursor))).isEqualTo(cursor);
    }

    @Test
    void tamperedCursorIsRejected() {
        String encoded = codec.encode(new SkinReportCursor(
                LocalDateTime.of(2026, 8, 15, 3, 10),
                UUID.fromString("0198a31f-f33f-7000-8000-000000000001")
        ));
        char replacement = encoded.charAt(encoded.length() - 1) == 'A' ? 'B' : 'A';
        String tampered = encoded.substring(0, encoded.length() - 1) + replacement;

        assertInvalidCursor(tampered);
    }

    @Test
    void malformedCursorIsRejected() {
        assertInvalidCursor("not-a-valid-cursor");
    }

    private void assertInvalidCursor(String cursor) {
        assertThatThrownBy(() -> codec.decode(cursor))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CURSOR);
    }
}
