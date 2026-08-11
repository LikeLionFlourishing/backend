package likelion.flourishing;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@ConfigurationPropertiesScan
@SpringBootApplication
public class FlourishingApplication {

    public static void main(String[] args) {
        // MySQL 드라이버는 java.time 값을 JVM 기본 시간대 기준으로 세션 시간대(UTC)에 맞춰 변환한다.
        // 기본 시간대가 Asia/Seoul이면 UTC 시계로 만든 값이 9시간 어긋나 저장되므로 UTC로 맞춘다.
        // 컨테이너는 Dockerfile의 TZ, -Duser.timezone으로 이미 UTC이고, 로컬 실행도 같게 만든다.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(FlourishingApplication.class, args);
    }
}
