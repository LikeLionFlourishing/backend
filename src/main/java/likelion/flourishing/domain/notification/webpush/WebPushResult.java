package likelion.flourishing.domain.notification.webpush;

/**
 * 발송 한 건의 결과. errorCode는 성공일 때 null이고, 실패일 때 이력 테이블에 남길 값이다.
 *
 * <p>공급자 응답 본문은 담지 않는다. 본문에 endpoint나 토큰이 섞여 나오는 경우가 있어
 * 저장하거나 로그로 남기면 곤란하다.
 */
public record WebPushResult(WebPushOutcome outcome, String errorCode) {

    public static WebPushResult success() {
        return new WebPushResult(WebPushOutcome.SUCCESS, null);
    }

    public static WebPushResult expired(String errorCode) {
        return new WebPushResult(WebPushOutcome.EXPIRED, errorCode);
    }

    public static WebPushResult failed(String errorCode) {
        return new WebPushResult(WebPushOutcome.FAILED, errorCode);
    }

    public boolean isSuccess() {
        return outcome == WebPushOutcome.SUCCESS;
    }

    public boolean isExpired() {
        return outcome == WebPushOutcome.EXPIRED;
    }
}
