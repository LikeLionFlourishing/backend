package likelion.flourishing.domain.notification.service;

import java.util.UUID;

/**
 * 발송 한 건에 필요한 복호화된 구독 정보. 트랜잭션 밖으로 나가는 값이라 메모리에만 존재한다.
 *
 * <p>endpoint와 키가 로그에 찍히면 그것만으로 알림을 보낼 수 있으므로 toString을 막는다.
 */
public record PushTarget(UUID subscriptionId, String endpoint, byte[] userAgentPublicKey, byte[] authSecret) {

    @Override
    public String toString() {
        return "PushTarget(subscriptionId=" + subscriptionId + ")";
    }
}
