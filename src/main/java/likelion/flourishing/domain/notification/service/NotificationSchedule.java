package likelion.flourishing.domain.notification.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * P0에서 고정인 알림 시각과 시간대.
 *
 * <p>DDL의 CHECK도 notification_time = '17:30', timezone = 'Asia/Seoul'만 허용한다.
 * 값을 한곳에 모아 두고 설정 응답과 스케줄러가 같은 상수를 쓰게 한다.
 *
 * <p>cron 표현식에는 상수를 넣을 수 없어 {@code @Scheduled}에 문자열을 직접 쓴다.
 * 두 값이 어긋나지 않도록 테스트가 표현식을 확인한다.
 */
public final class NotificationSchedule {

    public static final LocalTime NOTIFICATION_TIME = LocalTime.of(17, 30);
    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    public static final String NOTIFICATION_TIME_TEXT = "17:30";
    public static final String ZONE_TEXT = "Asia/Seoul";

    /** 매일 17시 30분(Asia/Seoul). JVM 기본 시간대가 UTC라 zone을 반드시 함께 지정한다. */
    public static final String CRON = "0 30 17 * * *";

    private NotificationSchedule() {
    }

    /**
     * 알림 기준 날짜.
     *
     * <p>저장하는 시각은 UTC지만 "오늘"은 사용자가 사는 시간대 기준이어야 한다.
     * UTC로 날짜를 끊으면 한국 시간 오전 9시 이전이 전날로 잡힌다.
     */
    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(ZONE));
    }
}
