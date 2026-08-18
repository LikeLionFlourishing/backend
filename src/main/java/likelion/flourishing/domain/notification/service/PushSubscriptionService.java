package likelion.flourishing.domain.notification.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.notification.crypto.EndpointFingerprint;
import likelion.flourishing.domain.notification.crypto.PushSecretCipher;
import likelion.flourishing.domain.notification.dto.request.RegisterPushSubscriptionRequest;
import likelion.flourishing.domain.notification.dto.response.PushSubscriptionResponse;
import likelion.flourishing.domain.notification.entity.PushSubscription;
import likelion.flourishing.domain.notification.repository.PushSubscriptionRepository;
import likelion.flourishing.domain.notification.webpush.InvalidPushKeyException;
import likelion.flourishing.domain.notification.webpush.P256Keys;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Push 구독 등록과 해제.
 *
 * <p>endpoint와 두 키는 암호화해 저장하고 응답에는 지문만 준다. 같은 사용자가 같은 endpoint를
 * 다시 보내면 행을 늘리지 않고 갱신한다. 브라우저가 구독을 다시 만들 때마다 행이 쌓이면
 * 한 사용자에게 같은 알림이 여러 번 도착한다.
 *
 * <p>키 형식 검증은 등록 시점에 끝낸다. 발송 시점에야 잘못된 키를 발견하면 알림이 조용히
 * 사라지고 원인을 추적하기 어렵다.
 */
@Service
public class PushSubscriptionService {

    private static final int MAX_USER_AGENT_LENGTH = 512;
    private static final String UNKNOWN_USER_AGENT = "unknown";

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final PushSecretCipher pushSecretCipher;
    private final EndpointFingerprint endpointFingerprint;
    private final PushEndpointPolicy pushEndpointPolicy;

    public PushSubscriptionService(
            PushSubscriptionRepository pushSubscriptionRepository,
            PushSecretCipher pushSecretCipher,
            EndpointFingerprint endpointFingerprint,
            PushEndpointPolicy pushEndpointPolicy
    ) {
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.pushSecretCipher = pushSecretCipher;
        this.endpointFingerprint = endpointFingerprint;
        this.pushEndpointPolicy = pushEndpointPolicy;
    }

    @Transactional
    public SavedPushSubscription register(AuthenticatedUser principal, RegisterPushSubscriptionRequest request) {
        String endpoint = requireAllowedEndpoint(request.endpoint());
        byte[] userAgentPublicKey = requirePublicKey(request.keys().p256dh());
        byte[] authSecret = requireAuthSecret(request.keys().auth());

        UUID userId = principal.userId();
        byte[] fingerprint = endpointFingerprint.of(endpoint);
        byte[] endpointCiphertext = pushSecretCipher.encryptText(endpoint);
        byte[] publicKeyCiphertext = pushSecretCipher.encrypt(userAgentPublicKey);
        byte[] authCiphertext = pushSecretCipher.encrypt(authSecret);
        String storedUserAgent = normalizeUserAgent(request.userAgent());
        LocalDateTime expiresAt = toExpiresAt(request.expirationTime());

        Optional<PushSubscription> existing =
                pushSubscriptionRepository.findByUserIdAndEndpointFingerprint(userId, fingerprint);
        if (existing.isPresent()) {
            PushSubscription subscription = existing.get();
            subscription.renew(endpointCiphertext, publicKeyCiphertext, authCiphertext, storedUserAgent, expiresAt);
            PushSubscription saved = pushSubscriptionRepository.saveAndFlush(subscription);
            return new SavedPushSubscription(PushSubscriptionResponse.from(saved), false);
        }

        PushSubscription saved = pushSubscriptionRepository.saveAndFlush(PushSubscription.register(
                userId,
                fingerprint,
                endpointCiphertext,
                publicKeyCiphertext,
                authCiphertext,
                storedUserAgent,
                expiresAt
        ));
        return new SavedPushSubscription(PushSubscriptionResponse.from(saved), true);
    }

    /**
     * 구독을 지운다.
     *
     * <p>다른 사용자의 구독 번호는 존재 여부를 알리지 않고 404로 답한다. 조회 조건에 userId를
     * 함께 넣어 소유권 검증이 SQL 단계에서 끝난다.
     */
    @Transactional
    public void unregister(AuthenticatedUser principal, UUID subscriptionId) {
        PushSubscription subscription = pushSubscriptionRepository
                .findByIdAndUserId(subscriptionId, principal.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        pushSubscriptionRepository.delete(subscription);
    }

    /**
     * endpoint가 우리가 실제로 요청을 보낼 수 있는 주소인지 확인한다.
     *
     * <p>스킴만 보면 사용자가 내부망 주소를 등록해 스케줄러로 하여금 그곳에 POST하게 만들 수 있다.
     * 판단 기준은 {@link PushEndpointPolicy}에 모아 두었다.
     */
    private String requireAllowedEndpoint(String endpoint) {
        if (!pushEndpointPolicy.isAllowed(endpoint)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return endpoint;
    }

    /** 브라우저가 준 p256dh는 비압축 65바이트여야 하고 P-256 곡선 위의 점이어야 한다. */
    private byte[] requirePublicKey(String encoded) {
        byte[] decoded = decodeBase64Url(encoded);
        try {
            P256Keys.publicKey(decoded);
        } catch (InvalidPushKeyException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return decoded;
    }

    private byte[] requireAuthSecret(String encoded) {
        byte[] decoded = decodeBase64Url(encoded);
        if (decoded.length != P256Keys.AUTH_SECRET_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return decoded;
    }

    /**
     * base64url 디코딩.
     *
     * <p>브라우저는 패딩 없는 base64url을 주지만, 클라이언트 구현에 따라 표준 base64가 오기도 한다.
     * 두 표기를 모두 받아들이고 그 밖의 문자는 형식 오류로 본다.
     */
    private byte[] decodeBase64Url(String encoded) {
        String normalized = encoded.trim().replace('+', '-').replace('/', '_');
        int padding = normalized.indexOf('=');
        if (padding >= 0) {
            normalized = normalized.substring(0, padding);
        }
        try {
            return Base64.getUrlDecoder().decode(normalized);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    /** 길이는 @Size가 이미 걸렀지만, 컬럼 길이를 넘기지 않도록 저장 직전에 한 번 더 자른다. */
    private String normalizeUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return UNKNOWN_USER_AGENT;
        }
        return userAgent.length() <= MAX_USER_AGENT_LENGTH
                ? userAgent
                : userAgent.substring(0, MAX_USER_AGENT_LENGTH);
    }

    /**
     * 브라우저가 알려 준 만료 시각을 UTC로 바꿔 저장한다. 대부분 값이 없다.
     *
     * <p>명세가 date-time 문자열이라 여기서 파싱한다. 브라우저 구독 JSON의 숫자 타임스탬프를
     * 그대로 보내면 형식 오류로 거부해, 잘못된 연도가 조용히 저장되지 않게 한다.
     */
    private LocalDateTime toExpiresAt(String expirationTime) {
        if (expirationTime == null || expirationTime.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(expirationTime).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (DateTimeParseException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }
}
