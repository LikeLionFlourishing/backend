package likelion.flourishing.support;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * DDL 규약대로 모든 BINARY(16) 기본키에 쓰는 UUIDv7을 만든다.
 * 앞 48비트가 Unix epoch 밀리초라서 생성 순서대로 정렬되고 인덱스 지역성이 좋다.
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {
    }

    public static UUID generate() {
        byte[] value = new byte[16];
        RANDOM.nextBytes(value);

        long timestamp = System.currentTimeMillis();
        value[0] = (byte) (timestamp >>> 40);
        value[1] = (byte) (timestamp >>> 32);
        value[2] = (byte) (timestamp >>> 24);
        value[3] = (byte) (timestamp >>> 16);
        value[4] = (byte) (timestamp >>> 8);
        value[5] = (byte) timestamp;

        // 버전 7과 IETF variant 비트를 덮어쓴다.
        value[6] = (byte) ((value[6] & 0x0F) | 0x70);
        value[8] = (byte) ((value[8] & 0x3F) | 0x80);

        long mostSignificantBits = 0;
        long leastSignificantBits = 0;
        for (int index = 0; index < 8; index++) {
            mostSignificantBits = (mostSignificantBits << 8) | (value[index] & 0xFF);
        }
        for (int index = 8; index < 16; index++) {
            leastSignificantBits = (leastSignificantBits << 8) | (value[index] & 0xFF);
        }
        return new UUID(mostSignificantBits, leastSignificantBits);
    }
}
