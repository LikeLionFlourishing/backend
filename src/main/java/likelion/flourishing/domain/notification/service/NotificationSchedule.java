package likelion.flourishing.domain.notification.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;

/**
 * 알림 발송의 시간대와 기본 시각.
 *
 * <p>명세 v2_1에서 발송 시각이 17:30 고정이 아니라 사용자가 온보딩에서 고르는 값이 됐다.
 * 그래서 스케줄러가 하루 한 번이 아니라 매 분 돌면서 "지금이 발송 시각인 사용자"를 고른다.
 * 시간대는 여전히 Asia/Seoul 고정이다.
 *
 * <p>cron 표현식에는 상수를 넣을 수 없어 {@code @Scheduled}에 문자열을 직접 쓴다.
 * 두 값이 어긋나지 않도록 테스트가 표현식을 확인한다.
 */
public final class NotificationSchedule {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    public static final String ZONE_TEXT = "Asia/Seoul";

    /** 명세 NotificationTime의 기본값. 알림을 받지 않겠다고 한 사용자도 이 값을 가진다. */
    public static final String DEFAULT_TIME_TEXT = "17:30";

    /**
     * 매 분 0초(Asia/Seoul). JVM 기본 시간대가 UTC라 zone을 반드시 함께 지정한다.
     *
     * <p>하루 한 번이 아니라 매 분 도는 이유는 발송 시각이 사용자마다 다르기 때문이다. 한 번
     * 실행은 그 분에 해당하는 사용자만 고르므로, 대상이 없으면 조회 한 번으로 끝난다.
     */
    public static final String CRON = "0 * * * * *";

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

    /** 지금 시각의 HH:mm. notification_settings.notification_time과 같은 표기다. */
    public static String currentTimeText(Clock clock) {
        return LocalTime.now(clock.withZone(ZONE)).format(TIME_FORMAT);
    }

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
}
