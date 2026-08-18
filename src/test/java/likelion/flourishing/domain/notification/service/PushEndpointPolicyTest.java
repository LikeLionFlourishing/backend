package likelion.flourishing.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import likelion.flourishing.domain.notification.webpush.PushNotificationProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * endpoint는 사용자 입력이고 그 주소로 스케줄러가 POST하므로, 등록 시점에 대상 주소를 좁힌다.
 * 여기서 막지 못하면 인증된 사용자가 서버로 하여금 내부망을 두드리게 만들 수 있다.
 */
class PushEndpointPolicyTest {

    private final PushEndpointPolicy defaultPolicy = policy(null);
    private final PushEndpointPolicy openPolicy = policy(List.of());

    @Test
    void knownPushServiceHostIsAllowed() {
        assertThat(defaultPolicy.isAllowed("https://fcm.googleapis.com/fcm/send/abc")).isTrue();
        assertThat(defaultPolicy.isAllowed("https://updates.push.services.mozilla.com/wpush/v2/abc")).isTrue();
        assertThat(defaultPolicy.isAllowed("https://web.push.apple.com/abc")).isTrue();
        assertThat(defaultPolicy.isAllowed("https://sea1.notify.windows.com/w/?token=abc")).isTrue();
    }

    @Test
    void unknownHostIsRejectedWhenAllowlistIsConfigured() {
        assertThat(defaultPolicy.isAllowed("https://push.example.net/push/abc")).isFalse();
    }

    /** 접미사 비교가 아니라 도메인 경계로 비교해야 한다. */
    @Test
    void lookalikeHostIsRejected() {
        assertThat(defaultPolicy.isAllowed("https://fcm.googleapis.com.evil.example/abc")).isFalse();
        assertThat(defaultPolicy.isAllowed("https://evilfcm.googleapis.com.attacker.test/abc")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://127.0.0.1:8443/push",
            "https://127.1.2.3/push",
            "https://0.0.0.0/push",
            "https://10.0.0.5/push",
            "https://172.16.0.9/push",
            "https://192.168.1.10/push",
            "https://169.254.169.254/latest/meta-data/",
            "https://100.64.0.1/push",
            "https://[::1]/push",
            "https://[fe80::1]/push",
            "https://239.255.255.250/push"
    })
    void internalAddressIsRejectedEvenWithoutAllowlist(String endpoint) {
        assertThat(openPolicy.isAllowed(endpoint)).isFalse();
        assertThat(defaultPolicy.isAllowed(endpoint)).isFalse();
    }

    @Test
    void publicAddressLiteralIsAllowedWithoutAllowlist() {
        assertThat(openPolicy.isAllowed("https://203.0.113.10/push")).isTrue();
    }

    @Test
    void testHostIsAllowedWhenAllowlistIsEmpty() {
        assertThat(openPolicy.isAllowed("https://push.example.net/push/abc")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://fcm.googleapis.com/fcm/send/abc",
            "ftp://fcm.googleapis.com/abc",
            "fcm.googleapis.com/abc",
            "https://",
            "https:// fcm.googleapis.com/abc",
            "https://user:pass@fcm.googleapis.com/abc"
    })
    void malformedOrNonHttpsEndpointIsRejected(String endpoint) {
        assertThat(defaultPolicy.isAllowed(endpoint)).isFalse();
    }

    @Test
    void blankOrTooLongEndpointIsRejected() {
        assertThat(defaultPolicy.isAllowed(null)).isFalse();
        assertThat(defaultPolicy.isAllowed("  ")).isFalse();
        assertThat(defaultPolicy.isAllowed("https://fcm.googleapis.com/" + "a".repeat(2048))).isFalse();
    }

    private static PushEndpointPolicy policy(List<String> allowedHosts) {
        return new PushEndpointPolicy(new PushNotificationProperties(null, null, null, null, allowedHosts));
    }
}
