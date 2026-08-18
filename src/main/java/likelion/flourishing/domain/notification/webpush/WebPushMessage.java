package likelion.flourishing.domain.notification.webpush;

/**
 * 구독 하나에 보낼 Web Push 한 건.
 *
 * <p>endpoint와 키는 DB에서 꺼내 복호화한 값이라 로그에 남기지 않는다. 그래서 toString을
 * 재정의해 값이 실수로 찍히지 않게 막는다.
 */
public record WebPushMessage(String endpoint, byte[] userAgentPublicKey, byte[] authSecret, byte[] payload) {

    @Override
    public String toString() {
        return "WebPushMessage(redacted)";
    }
}
