package likelion.flourishing.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * 고정 창 카운터 테스트.
 *
 * <p>확인하는 것: 창 안의 요청 수를 세어 한도를 넘는 순간부터 막는지, 첫 요청에서 TTL을 거는지,
 * Redis에 붙지 못하면 통과시키지 않고 503을 내는지, 그 밖의 예외는 삼키지 않고 그대로 올리는지.
 *
 * <p>마지막 두 가지가 이 테스트의 핵심이다. Redis 장애를 통과 처리하면 장애 시간 동안
 * 로그인 시도 제한이 사라져 무제한 대입이 가능해진다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RateLimiterTest {

    private static final String SCOPE = "login-ip";
    private static final String IDENTIFIER = "127.0.0.1";
    private static final String KEY = "rate-limit:login-ip:127.0.0.1";
    private static final int LIMIT = 3;
    private static final Duration WINDOW = Duration.ofMinutes(10);

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC);
        rateLimiter = new RateLimiter(redisTemplate, clock);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.getExpire(anyString(), any(TimeUnit.class))).thenReturn(600L);
    }

    @Test
    void firstRequestStartsWindowAndIsAllowed() {
        when(valueOperations.increment(KEY)).thenReturn(1L);

        RateLimitResult result = rateLimiter.consume(SCOPE, IDENTIFIER, LIMIT, WINDOW);

        assertThat(result.allowed()).isTrue();
        assertThat(result.remaining()).isEqualTo(2);
    }

    @Test
    void requestOverTheLimitIsRejected() {
        when(valueOperations.increment(KEY)).thenReturn(4L);

        RateLimitResult result = rateLimiter.consume(SCOPE, IDENTIFIER, LIMIT, WINDOW);

        assertThat(result.allowed()).isFalse();
        assertThat(result.remaining()).isZero();
        assertThat(result.retryAfterSeconds()).isEqualTo(600);
    }

    @Test
    void connectionFailureRejectsInsteadOfLettingRequestThrough() {
        when(valueOperations.increment(KEY)).thenThrow(new RedisConnectionFailureException("redis down"));

        assertThatThrownBy(() -> rateLimiter.consume(SCOPE, IDENTIFIER, LIMIT, WINDOW))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RATE_LIMIT_UNAVAILABLE);
    }

    @Test
    void commandTimeoutRejectsInsteadOfLettingRequestThrough() {
        when(valueOperations.increment(KEY)).thenThrow(new QueryTimeoutException("timeout"));

        assertThatThrownBy(() -> rateLimiter.consume(SCOPE, IDENTIFIER, LIMIT, WINDOW))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RATE_LIMIT_UNAVAILABLE);
    }

    @Test
    void unexpectedErrorIsNotSwallowed() {
        when(valueOperations.increment(KEY)).thenThrow(new IllegalStateException("프로그래밍 오류"));

        assertThatThrownBy(() -> rateLimiter.consume(SCOPE, IDENTIFIER, LIMIT, WINDOW))
                .isInstanceOf(IllegalStateException.class);
    }
}
