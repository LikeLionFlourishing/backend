package likelion.flourishing.domain.notification.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import likelion.flourishing.domain.notification.service.NotificationDispatchService;
import likelion.flourishing.domain.notification.service.NotificationSchedule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

@ExtendWith(MockitoExtension.class)
class NotificationSchedulerTest {

    @Mock
    private NotificationDispatchService notificationDispatchService;

    @InjectMocks
    private NotificationScheduler scheduler;

    @Test
    void triggerDelegatesToDispatchService() {
        when(notificationDispatchService.dispatchToday()).thenReturn(3);

        scheduler.dispatchDailyNotifications();

        verify(notificationDispatchService).dispatchToday();
    }

    @Test
    void failureDoesNotEscapeToSchedulerThread() {
        when(notificationDispatchService.dispatchToday()).thenThrow(new IllegalStateException("boom"));

        assertThatCode(scheduler::dispatchDailyNotifications).doesNotThrowAnyException();
    }

    /**
     * cron 표현식에는 상수를 넣을 수 없어 문자열을 직접 적었다.
     * 고정 시각·시간대 상수와 어긋나지 않는지 여기서 확인한다.
     */
    @Test
    void cronMatchesFixedTimeAndZone() throws Exception {
        Method method = NotificationScheduler.class.getMethod("dispatchDailyNotifications");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo(NotificationSchedule.CRON);
        assertThat(scheduled.zone()).isEqualTo(NotificationSchedule.ZONE_TEXT);
        assertThat(NotificationSchedule.CRON)
                .isEqualTo("0 %d %d * * *".formatted(
                        NotificationSchedule.NOTIFICATION_TIME.getMinute(),
                        NotificationSchedule.NOTIFICATION_TIME.getHour()
                ));
    }
}
