package likelion.flourishing.domain.notification.entity;

/** 17:30에 보낼 수 있는 알림 종류. DDL의 CHECK가 같은 두 값만 허용한다. */
public enum NotificationType {

    /** 아직 경과를 입력하지 않은 보고가 있을 때. 피부 점호보다 우선한다. */
    FOLLOW_UP,

    /** 오늘 피부 점호를 아직 하지 않았을 때. */
    DAILY_CHECK_IN
}
