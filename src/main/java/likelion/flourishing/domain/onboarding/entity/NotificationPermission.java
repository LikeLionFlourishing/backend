package likelion.flourishing.domain.onboarding.entity;

/**
 * 브라우저 Notification 권한 상태. 명세 NotificationPermission 스키마와 값이 같다.
 *
 * <p>알림 설정 조회·변경(Notifications 태그)도 같은 값을 쓰므로 담당자가 정해지면
 * 공용 위치로 옮겨야 한다.
 */
public enum NotificationPermission {
    DEFAULT,
    GRANTED,
    DENIED,
    UNSUPPORTED
}
