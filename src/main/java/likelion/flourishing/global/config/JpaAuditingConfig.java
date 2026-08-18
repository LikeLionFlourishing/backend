package likelion.flourishing.global.config;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * 감사 시각도 UTC 시계를 쓴다.
 * 기본 제공자는 JVM 기본 시간대의 {@code LocalDateTime.now()}를 쓰기 때문에
 * 서버 시간대가 Asia/Seoul이면 created_at만 KST로 저장되어 다른 시각 컬럼과 어긋난다.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "utcDateTimeProvider")
public class JpaAuditingConfig {

    @Bean
    public DateTimeProvider utcDateTimeProvider(Clock clock) {
        return () -> Optional.of(LocalDateTime.now(clock));
    }
}
