package likelion.flourishing.domain.notification.service;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import likelion.flourishing.domain.notification.webpush.PushNotificationProperties;
import org.springframework.stereotype.Component;

/**
 * 등록하려는 Push endpoint가 우리가 실제로 요청을 보낼 수 있는 주소인지 검사한다.
 *
 * <p>endpoint는 사용자가 보내는 값이고, 그 주소로 매일 17:30에 서버가 POST한다. 스킴만 확인하면
 * 인증된 사용자가 서버로 하여금 내부망 주소를 두드리게 만들 수 있다(SSRF). 응답 본문을 사용자에게
 * 돌려주지 않아 blind이지만, 내부 서비스의 상태를 바꾸는 요청은 그것만으로도 성립한다.
 *
 * <p>그래서 두 겹으로 막는다. 첫째, 알려진 push 서비스 호스트만 허용한다. 둘째, 호스트가 IP
 * 리터럴이면 loopback·사설·link-local 대역을 거부한다. allowlist가 비어 있으면(사설 push 서비스
 * 테스트 등) 두 번째 검사만 적용한다.
 *
 * <p>DNS 재바인딩까지는 막지 못한다. 등록 시점에 통과한 호스트가 발송 시점에 내부 IP로 해석될 수
 * 있다. 완전히 막으려면 발송 직전에 해석한 IP를 다시 확인하고 그 IP로 연결해야 하는데, 그 부분은
 * allowlist를 유지하는 편이 비용이 낮다.
 */
@Component
public class PushEndpointPolicy {

    private static final String HTTPS = "https";
    private static final int MAX_ENDPOINT_LENGTH = 2048;

    private final List<String> allowedHostSuffixes;

    public PushEndpointPolicy(PushNotificationProperties properties) {
        this.allowedHostSuffixes = properties.allowedEndpointHosts().stream()
                .map(host -> host.toLowerCase(Locale.ROOT))
                .filter(host -> !host.isBlank())
                .toList();
    }

    /** 형식과 대상 주소가 모두 허용 범위인지. 거부 이유는 호출자가 VALIDATION_ERROR로 바꾼다. */
    public boolean isAllowed(String endpoint) {
        if (endpoint == null || endpoint.isBlank() || endpoint.length() > MAX_ENDPOINT_LENGTH) {
            return false;
        }

        URI uri;
        try {
            uri = new URI(endpoint);
        } catch (URISyntaxException exception) {
            return false;
        }
        if (!HTTPS.equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
            return false;
        }

        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return !isBlockedAddress(host) && matchesAllowlist(host);
    }

    private boolean matchesAllowlist(String host) {
        if (allowedHostSuffixes.isEmpty()) {
            return true;
        }
        return allowedHostSuffixes.stream()
                .anyMatch(suffix -> host.equals(suffix) || host.endsWith("." + suffix));
    }

    /**
     * 호스트가 IP 리터럴일 때만 대역을 본다.
     *
     * <p>이름은 여기서 해석하지 않는다. 등록 요청마다 DNS를 물으면 사용자 입력으로 우리 서버가
     * 이름 해석을 하게 되고, 해석 결과는 발송 시점에 또 달라질 수 있어 검사 효과도 제한적이다.
     */
    private boolean isBlockedAddress(String host) {
        if (!looksLikeIpLiteral(host)) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(host);
            return address.isLoopbackAddress()
                    || address.isAnyLocalAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress()
                    || isSharedAddressSpace(address);
        } catch (UnknownHostException exception) {
            return true;
        }
    }

    /** RFC 6598 100.64.0.0/10. 통신사 NAT 구간이라 외부 push 서비스가 쓰지 않는다. */
    private boolean isSharedAddressSpace(InetAddress address) {
        if (!(address instanceof Inet4Address)) {
            return false;
        }
        byte[] octets = address.getAddress();
        int first = octets[0] & 0xFF;
        int second = octets[1] & 0xFF;
        return first == 100 && second >= 64 && second <= 127;
    }

    private boolean looksLikeIpLiteral(String host) {
        if (host.indexOf(':') >= 0) {
            return true;
        }
        return host.matches("\\d{1,3}(\\.\\d{1,3}){3}");
    }
}
