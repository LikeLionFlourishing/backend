package likelion.flourishing.domain.notification.webpush;

/**
 * 구독 키가 형식이나 곡선 검증을 통과하지 못했을 때.
 *
 * <p>사용자 입력에서 오는 값이라 서버 오류가 아니다. 서비스 계층이 이 예외를 잡아
 * VALIDATION_ERROR로 바꾼다.
 */
public class InvalidPushKeyException extends RuntimeException {

    public InvalidPushKeyException(String message) {
        super(message);
    }
}
