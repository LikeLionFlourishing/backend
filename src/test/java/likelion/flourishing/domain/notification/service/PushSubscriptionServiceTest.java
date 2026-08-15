package likelion.flourishing.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.notification.crypto.EndpointFingerprint;
import likelion.flourishing.domain.notification.crypto.NotificationCryptoProperties;
import likelion.flourishing.domain.notification.crypto.PushSecretCipher;
import likelion.flourishing.domain.notification.dto.request.RegisterPushSubscriptionRequest;
import likelion.flourishing.domain.notification.dto.request.RegisterPushSubscriptionRequest.PushSubscriptionKeysRequest;
import likelion.flourishing.domain.notification.entity.PushSubscription;
import likelion.flourishing.domain.notification.repository.PushSubscriptionRepository;
import likelion.flourishing.domain.notification.webpush.P256Keys;
import likelion.flourishing.domain.notification.webpush.PushNotificationProperties;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PushSubscriptionServiceTest {

    private static final String TEST_KEY = "OTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTBmZWRjYmE=";
    private static final UUID USER_ID = UUID.fromString("2c56fe08-ea1f-45fc-915d-c35b7c0bca39");
    private static final UUID SESSION_ID = UUID.fromString("5ecb88d8-6a21-4a54-8967-72599f078963");
    private static final UUID SUBSCRIPTION_ID = UUID.fromString("0198a31f-f33f-7000-8000-0000000000a1");
    private static final String ENDPOINT = "https://push.example.net/push/JzLQ3raZJfFBR0aqvOMsLrt54w4rJUsV";
    private static final String AUTH = encode("0123456789abcdef".getBytes(StandardCharsets.US_ASCII));
    private static final String USER_AGENT = "Mozilla/5.0 (iPhone) AppleWebKit/605.1.15";

    @Mock
    private PushSubscriptionRepository pushSubscriptionRepository;

    private PushSecretCipher pushSecretCipher;
    private EndpointFingerprint endpointFingerprint;
    private PushSubscriptionService service;
    private String p256dh;

    @BeforeEach
    void setUp() {
        NotificationCryptoProperties properties = new NotificationCryptoProperties(TEST_KEY);
        pushSecretCipher = new PushSecretCipher(properties);
        endpointFingerprint = new EndpointFingerprint(properties);
        // 호스트 allowlist를 비워 두면 사설·loopback 대역만 막는다. 이 스위트는 저장 규칙을 보므로
        // 테스트용 호스트를 쓰고, allowlist 동작은 PushEndpointPolicyTest가 따로 검증한다.
        service = new PushSubscriptionService(
                pushSubscriptionRepository,
                pushSecretCipher,
                endpointFingerprint,
                new PushEndpointPolicy(new PushNotificationProperties(null, null, null, null, List.of()))
        );

        KeyPair browserKeyPair = P256Keys.generateKeyPair();
        p256dh = encode(P256Keys.uncompressed((ECPublicKey) browserKeyPair.getPublic()));

        when(pushSubscriptionRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void newSubscriptionIsCreatedWithEncryptedSecretsAndFingerprint() {
        when(pushSubscriptionRepository.findByUserIdAndEndpointFingerprint(eq(USER_ID), any()))
                .thenReturn(Optional.empty());

        SavedPushSubscription saved = service.register(principal(), request(null), USER_AGENT);

        ArgumentCaptor<PushSubscription> captor = ArgumentCaptor.forClass(PushSubscription.class);
        verify(pushSubscriptionRepository).saveAndFlush(captor.capture());
        PushSubscription stored = captor.getValue();

        assertThat(saved.created()).isTrue();
        assertThat(stored.getEndpointFingerprint()).isEqualTo(endpointFingerprint.of(ENDPOINT));
        assertThat(pushSecretCipher.decryptText(stored.getEndpointCiphertext())).isEqualTo(ENDPOINT);
        assertThat(pushSecretCipher.decrypt(stored.getP256dhCiphertext())).isEqualTo(decode(p256dh));
        assertThat(pushSecretCipher.decrypt(stored.getAuthCiphertext())).isEqualTo(decode(AUTH));
        assertThat(stored.isActive()).isTrue();
    }

    @Test
    void responseExposesOnlyFingerprint() {
        when(pushSubscriptionRepository.findByUserIdAndEndpointFingerprint(eq(USER_ID), any()))
                .thenReturn(Optional.empty());

        SavedPushSubscription saved = service.register(principal(), request(null), USER_AGENT);

        assertThat(saved.response().getEndpointFingerprint())
                .isEqualTo(EndpointFingerprint.toHex(endpointFingerprint.of(ENDPOINT)));
        assertThat(saved.response().getEndpointFingerprint()).doesNotContain("push.example.net");
    }

    @Test
    void sameEndpointIsRenewedInsteadOfDuplicated() {
        PushSubscription existing = existingSubscription();
        when(pushSubscriptionRepository.findByUserIdAndEndpointFingerprint(eq(USER_ID), any()))
                .thenReturn(Optional.of(existing));

        SavedPushSubscription saved = service.register(principal(), request(null), "Chrome/130");

        assertThat(saved.created()).isFalse();
        assertThat(existing.getUserAgent()).isEqualTo("Chrome/130");
        assertThat(existing.isActive()).isTrue();
        verify(pushSubscriptionRepository).saveAndFlush(existing);
    }

    @Test
    void renewalReactivatesSubscriptionThatWasDeactivated() {
        PushSubscription existing = existingSubscription();
        existing.deactivate();
        when(pushSubscriptionRepository.findByUserIdAndEndpointFingerprint(eq(USER_ID), any()))
                .thenReturn(Optional.of(existing));

        service.register(principal(), request(null), USER_AGENT);

        assertThat(existing.isActive()).isTrue();
    }

    @Test
    void expirationTimeIsStoredAsUtc() {
        when(pushSubscriptionRepository.findByUserIdAndEndpointFingerprint(eq(USER_ID), any()))
                .thenReturn(Optional.empty());

        long expirationTime = Instant.parse("2026-09-01T00:00:00Z").toEpochMilli();

        service.register(principal(), request(expirationTime), USER_AGENT);

        ArgumentCaptor<PushSubscription> captor = ArgumentCaptor.forClass(PushSubscription.class);
        verify(pushSubscriptionRepository).saveAndFlush(captor.capture());

        assertThat(captor.getValue().getExpiresAt())
                .isEqualTo(LocalDateTime.of(2026, 9, 1, 0, 0));
    }

    @Test
    void longUserAgentIsTruncatedToColumnLength() {
        when(pushSubscriptionRepository.findByUserIdAndEndpointFingerprint(eq(USER_ID), any()))
                .thenReturn(Optional.empty());

        service.register(principal(), request(null), "x".repeat(1000));

        ArgumentCaptor<PushSubscription> captor = ArgumentCaptor.forClass(PushSubscription.class);
        verify(pushSubscriptionRepository).saveAndFlush(captor.capture());

        assertThat(captor.getValue().getUserAgent()).hasSize(512);
    }

    @Test
    void missingUserAgentFallsBackToPlaceholder() {
        when(pushSubscriptionRepository.findByUserIdAndEndpointFingerprint(eq(USER_ID), any()))
                .thenReturn(Optional.empty());

        service.register(principal(), request(null), null);

        ArgumentCaptor<PushSubscription> captor = ArgumentCaptor.forClass(PushSubscription.class);
        verify(pushSubscriptionRepository).saveAndFlush(captor.capture());

        assertThat(captor.getValue().getUserAgent()).isEqualTo("unknown");
    }

    @Test
    void nonHttpsEndpointIsRejected() {
        RegisterPushSubscriptionRequest request = new RegisterPushSubscriptionRequest(
                "http://push.example.net/push/abc", null, new PushSubscriptionKeysRequest(p256dh, AUTH)
        );

        assertThatThrownBy(() -> service.register(principal(), request, USER_AGENT))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(pushSubscriptionRepository, never()).saveAndFlush(any());
    }

    @Test
    void publicKeyOffTheCurveIsRejected() {
        byte[] tampered = decode(p256dh);
        tampered[64] ^= 1;
        RegisterPushSubscriptionRequest request = new RegisterPushSubscriptionRequest(
                ENDPOINT, null, new PushSubscriptionKeysRequest(encode(tampered), AUTH)
        );

        assertThatThrownBy(() -> service.register(principal(), request, USER_AGENT))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void authSecretWithWrongLengthIsRejected() {
        RegisterPushSubscriptionRequest request = new RegisterPushSubscriptionRequest(
                ENDPOINT, null, new PushSubscriptionKeysRequest(p256dh, encode(new byte[8]))
        );

        assertThatThrownBy(() -> service.register(principal(), request, USER_AGENT))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void keysThatAreNotBase64AreRejected() {
        RegisterPushSubscriptionRequest request = new RegisterPushSubscriptionRequest(
                ENDPOINT, null, new PushSubscriptionKeysRequest("이건 base64가 아닙니다", AUTH)
        );

        assertThatThrownBy(() -> service.register(principal(), request, USER_AGENT))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void standardBase64KeysAreAccepted() {
        when(pushSubscriptionRepository.findByUserIdAndEndpointFingerprint(eq(USER_ID), any()))
                .thenReturn(Optional.empty());
        String standardBase64 = Base64.getEncoder().encodeToString(decode(p256dh));

        SavedPushSubscription saved = service.register(
                principal(),
                new RegisterPushSubscriptionRequest(
                        ENDPOINT, null, new PushSubscriptionKeysRequest(standardBase64, AUTH)
                ),
                USER_AGENT
        );

        assertThat(saved.created()).isTrue();
    }

    @Test
    void unregisterDeletesOwnSubscription() {
        PushSubscription existing = existingSubscription();
        when(pushSubscriptionRepository.findByIdAndUserId(SUBSCRIPTION_ID, USER_ID))
                .thenReturn(Optional.of(existing));

        service.unregister(principal(), SUBSCRIPTION_ID);

        verify(pushSubscriptionRepository).delete(existing);
    }

    @Test
    void unregisterHidesOtherUsersSubscriptionAsNotFound() {
        when(pushSubscriptionRepository.findByIdAndUserId(SUBSCRIPTION_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unregister(principal(), SUBSCRIPTION_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        verify(pushSubscriptionRepository, never()).delete(any());
    }

    private PushSubscription existingSubscription() {
        return PushSubscription.register(
                USER_ID,
                endpointFingerprint.of(ENDPOINT),
                pushSecretCipher.encryptText(ENDPOINT),
                pushSecretCipher.encrypt(decode(p256dh)),
                pushSecretCipher.encrypt(decode(AUTH)),
                USER_AGENT,
                null
        );
    }

    private RegisterPushSubscriptionRequest request(Long expirationTime) {
        return new RegisterPushSubscriptionRequest(
                ENDPOINT, expirationTime, new PushSubscriptionKeysRequest(p256dh, AUTH)
        );
    }

    private AuthenticatedUser principal() {
        return new AuthenticatedUser(
                USER_ID, SESSION_ID, LocalDateTime.of(2026, 8, 24, 0, 0), "csrf-token-value-that-is-long-enough"
        );
    }

    private static byte[] decode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
