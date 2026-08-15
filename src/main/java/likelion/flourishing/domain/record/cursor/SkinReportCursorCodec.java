package likelion.flourishing.domain.record.cursor;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import likelion.flourishing.domain.report.crypto.RecordCryptoProperties;
import likelion.flourishing.domain.report.crypto.RecordKeyDerivation;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

/** createdAt과 UUID를 노출하지 않고 변조도 탐지하는 고정 길이 서명 커서. */
@Component
public class SkinReportCursorCodec {

    private static final byte FORMAT_VERSION = 1;
    private static final int PAYLOAD_LENGTH = 29;
    private static final int SIGNATURE_LENGTH = 32;
    private static final int ENCODED_MAX_LENGTH = 512;
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String KEY_PURPOSE = "flourishing:records:cursor:v1";

    private final SecretKeySpec signingKey;

    public SkinReportCursorCodec(RecordCryptoProperties properties) {
        byte[] key = RecordKeyDerivation.derive(properties.decodeMasterKey(), KEY_PURPOSE);
        this.signingKey = new SecretKeySpec(key, HMAC_ALGORITHM);
    }

    public String encode(SkinReportCursor cursor) {
        Instant instant = cursor.createdAt().toInstant(ZoneOffset.UTC);
        byte[] payload = ByteBuffer.allocate(PAYLOAD_LENGTH)
                .put(FORMAT_VERSION)
                .putLong(instant.getEpochSecond())
                .putInt(instant.getNano())
                .putLong(cursor.id().getMostSignificantBits())
                .putLong(cursor.id().getLeastSignificantBits())
                .array();
        byte[] signature = sign(payload);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(ByteBuffer.allocate(payload.length + signature.length)
                        .put(payload)
                        .put(signature)
                        .array());
    }

    public SkinReportCursor decode(String encoded) {
        if (encoded == null) {
            return null;
        }
        if (encoded.isBlank() || encoded.length() > ENCODED_MAX_LENGTH) {
            throw invalidCursor();
        }

        try {
            byte[] decoded = Base64.getUrlDecoder().decode(encoded);
            if (decoded.length != PAYLOAD_LENGTH + SIGNATURE_LENGTH) {
                throw invalidCursor();
            }

            byte[] payload = Arrays.copyOfRange(decoded, 0, PAYLOAD_LENGTH);
            byte[] signature = Arrays.copyOfRange(decoded, PAYLOAD_LENGTH, decoded.length);
            if (!MessageDigest.isEqual(signature, sign(payload))) {
                throw invalidCursor();
            }

            ByteBuffer buffer = ByteBuffer.wrap(payload);
            if (buffer.get() != FORMAT_VERSION) {
                throw invalidCursor();
            }
            long epochSecond = buffer.getLong();
            int nano = buffer.getInt();
            UUID id = new UUID(buffer.getLong(), buffer.getLong());
            LocalDateTime createdAt = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(epochSecond, nano), ZoneOffset.UTC
            );
            return new SkinReportCursor(createdAt, id);
        } catch (IllegalArgumentException | java.time.DateTimeException exception) {
            throw invalidCursor();
        }
    }

    private byte[] sign(byte[] payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(signingKey);
            return mac.doFinal(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("기록 커서를 서명하지 못했습니다.", exception);
        }
    }

    private BusinessException invalidCursor() {
        return new BusinessException(ErrorCode.INVALID_CURSOR);
    }
}
