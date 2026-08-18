package likelion.flourishing.domain.notification.webpush;

/**
 * 외부 Push 서비스로 나가는 경계.
 *
 * <p>발송 서비스가 HTTP를 모르게 하려고 인터페이스로 끊는다. 테스트에서는 이 인터페이스만
 * 대신 넣으면 되고, 실제 HTTP 동작은 구현체 테스트에서 따로 확인한다.
 */
public interface WebPushGateway {

    WebPushResult send(WebPushMessage message);
}
