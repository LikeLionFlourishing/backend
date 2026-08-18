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
        when(notificationDispatchService.dispatchNow()).thenReturn(3);

        scheduler.dispatchDueNotifications();

        verify(notificationDispatchService).dispatchNow();
    }

    @Test
    void failureDoesNotEscapeToSchedulerThread() {
        when(notificationDispatchService.dispatchNow()).thenThrow(new IllegalStateException("boom"));

        assertThatCode(scheduler::dispatchDueNotifications).doesNotThrowAnyException();
    }

    /**
     * 발송 시각이 사용자마다 달라져 cron이 매 분 실행으로 바뀌었다. 하루 한 번으로 되돌아가면
     * 기본값 17:30이 아닌 시각을 고른 사용자가 알림을 못 받으므로 여기서 고정한다.
     */
    @Test
    void cronMatchesFixedTimeAndZone() throws Exception {
        Method method = NotificationScheduler.class.getMethod("dispatchDueNotifications");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo(NotificationSchedule.CRON);
        assertThat(scheduled.zone()).isEqualTo(NotificationSchedule.ZONE_TEXT);
        assertThat(NotificationSchedule.CRON).isEqualTo("0 * * * * *");
    }
}
