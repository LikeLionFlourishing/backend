package likelion.flourishing.domain.notification.webpush;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECFieldFp;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import javax.crypto.KeyAgreement;

/**
 * Web Push가 쓰는 P-256(secp256r1) 키를 JDK 표준 API로만 다룬다.
 *
 * <p>브라우저와 VAPID 설정은 키를 X9.62 비압축 형식(0x04 || X || Y, 65바이트)과 32바이트
 * 스칼라로 준다. JDK는 이 형식을 바로 받지 않아 좌표를 직접 꺼내 변환한다.
 *
 * <p>받은 공개키가 정말 곡선 위의 점인지 반드시 확인한다(RFC 8291 7절). 곡선 밖의 점으로
 * ECDH를 하면 우리 비밀키가 추출될 수 있다.
 */
public final class P256Keys {

    /** 0x04 접두사 + X(32) + Y(32). */
    public static final int UNCOMPRESSED_LENGTH = 65;

    /** 브라우저가 주는 auth 비밀의 길이(RFC 8291 3.2절). */
    public static final int AUTH_SECRET_LENGTH = 16;

    private static final String CURVE_NAME = "secp256r1";
    private static final byte UNCOMPRESSED_PREFIX = 0x04;
    private static final int COORDINATE_LENGTH = 32;
    private static final ECParameterSpec PARAMETERS = loadParameters();

    private P256Keys() {
    }

    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec(CURVE_NAME));
            return generator.generateKeyPair();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Web Push 임시 키를 만들지 못했습니다.", exception);
        }
    }

    /**
     * 비압축 형식 공개키를 읽는다. 형식이 틀리거나 곡선 위의 점이 아니면
     * {@link InvalidPushKeyException}을 던진다.
     */
    public static ECPublicKey publicKey(byte[] uncompressed) {
        if (uncompressed == null
                || uncompressed.length != UNCOMPRESSED_LENGTH
                || uncompressed[0] != UNCOMPRESSED_PREFIX) {
            throw new InvalidPushKeyException("공개키는 65바이트 비압축 형식이어야 합니다.");
        }

        BigInteger x = new BigInteger(1, uncompressed, 1, COORDINATE_LENGTH);
        BigInteger y = new BigInteger(1, uncompressed, 1 + COORDINATE_LENGTH, COORDINATE_LENGTH);
        requireOnCurve(x, y);

        try {
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            return (ECPublicKey) keyFactory.generatePublic(new ECPublicKeySpec(new ECPoint(x, y), PARAMETERS));
        } catch (GeneralSecurityException exception) {
            throw new InvalidPushKeyException("공개키를 해석하지 못했습니다.");
        }
    }

    /** 32바이트 스칼라로 표현된 비밀키를 읽는다. VAPID 설정이 이 형식을 쓴다. */
    public static ECPrivateKey privateKey(byte[] scalar) {
        if (scalar == null || scalar.length == 0 || scalar.length > COORDINATE_LENGTH) {
            throw new InvalidPushKeyException("비밀키는 32바이트 이하 스칼라여야 합니다.");
        }

        BigInteger value = new BigInteger(1, scalar);
        if (value.signum() <= 0 || value.compareTo(PARAMETERS.getOrder()) >= 0) {
            throw new InvalidPushKeyException("비밀키가 곡선 차수 범위를 벗어났습니다.");
        }

        try {
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            return (ECPrivateKey) keyFactory.generatePrivate(new ECPrivateKeySpec(value, PARAMETERS));
        } catch (GeneralSecurityException exception) {
            throw new InvalidPushKeyException("비밀키를 해석하지 못했습니다.");
        }
    }

    /** 공개키를 다시 비압축 65바이트로 만든다. 좌표는 항상 32바이트로 채운다. */
    public static byte[] uncompressed(ECPublicKey publicKey) {
        ECPoint point = publicKey.getW();
        byte[] encoded = new byte[UNCOMPRESSED_LENGTH];
        encoded[0] = UNCOMPRESSED_PREFIX;
        writeCoordinate(point.getAffineX(), encoded, 1);
        writeCoordinate(point.getAffineY(), encoded, 1 + COORDINATE_LENGTH);
        return encoded;
    }

    /** ECDH 공유 비밀 32바이트. */
    public static byte[] sharedSecret(ECPrivateKey privateKey, ECPublicKey publicKey) {
        try {
            KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
            agreement.init(privateKey);
            agreement.doPhase(publicKey, true);
            return agreement.generateSecret();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Web Push 공유 비밀을 계산하지 못했습니다.", exception);
        }
    }

    private static void requireOnCurve(BigInteger x, BigInteger y) {
        BigInteger p = ((ECFieldFp) PARAMETERS.getCurve().getField()).getP();
        if (x.signum() < 0 || x.compareTo(p) >= 0 || y.signum() < 0 || y.compareTo(p) >= 0) {
            throw new InvalidPushKeyException("공개키 좌표가 유한체 범위를 벗어났습니다.");
        }
        if (x.signum() == 0 && y.signum() == 0) {
            throw new InvalidPushKeyException("공개키가 무한원점입니다.");
        }

        BigInteger a = PARAMETERS.getCurve().getA();
        BigInteger b = PARAMETERS.getCurve().getB();
        BigInteger left = y.modPow(BigInteger.TWO, p);
        BigInteger right = x.modPow(BigInteger.valueOf(3), p).add(a.multiply(x)).add(b).mod(p);
        if (!left.equals(right)) {
            throw new InvalidPushKeyException("공개키가 P-256 곡선 위의 점이 아닙니다.");
        }
    }

    private static void writeCoordinate(BigInteger coordinate, byte[] target, int offset) {
        byte[] value = coordinate.toByteArray();
        if (value.length > COORDINATE_LENGTH) {
            // BigInteger는 최상위 비트가 1이면 부호 바이트 0x00을 앞에 붙인다.
            System.arraycopy(value, value.length - COORDINATE_LENGTH, target, offset, COORDINATE_LENGTH);
            return;
        }
        System.arraycopy(value, 0, target, offset + COORDINATE_LENGTH - value.length, value.length);
    }

    private static ECParameterSpec loadParameters() {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec(CURVE_NAME));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("P-256 곡선 파라미터를 읽지 못했습니다.", exception);
        }
    }
}
