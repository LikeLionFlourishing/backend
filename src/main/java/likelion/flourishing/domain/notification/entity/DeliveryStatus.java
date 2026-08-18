package likelion.flourishing.domain.notification.entity;

/** 발송 이력의 상태. DDL의 CHECK가 상태와 sent_at·error_code의 짝을 강제한다. */
public enum DeliveryStatus {

    /** 발송 대상으로 잡아 두기만 한 상태. sent_at과 error_code가 모두 비어 있어야 한다. */
    PENDING,

    /** 구독 하나 이상에 성공했다. sent_at이 반드시 있어야 한다. */
    SENT,

    /** 모든 구독에 실패했다. error_code가 반드시 있어야 한다. */
    FAILED,

    /** 보낼 곳이 없어 건너뛴 날. 같은 날 다시 시도하지 않기 위해 기록만 남긴다. */
    SKIPPED
}
