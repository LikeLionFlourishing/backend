package likelion.flourishing.global.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * DB에 저장하는 시각은 UTC 기준이므로 시계도 UTC로 고정한다.
 * 일일 정책(피부 점호, 알림)만 Asia/Seoul로 계산한다.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
