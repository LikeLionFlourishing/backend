package likelion.flourishing.domain.notification.webpush;

/** Push 서비스 응답을 우리 처리 기준으로 줄인 결과. */
public enum WebPushOutcome {

    /** 2xx. Push 서비스가 전달을 받아들였다. */
    SUCCESS,

    /** 404 또는 410. 구독이 영구히 사라졌으므로 비활성으로 내린다. */
    EXPIRED,

    /** 그 밖의 실패. 구독은 살려 두고 이력에만 오류 코드를 남긴다. */
    FAILED
}
