package likelion.flourishing.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import org.mockito.ArgumentCaptor;
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

    /**
     * 키에 식별자 원문이 들어가면 Redis를 볼 수 있는 사람이 KEYS 한 번으로 최근 로그인한
     * 계정 목록을 얻는다. 같은 입력이 같은 키로 가되 원문은 남지 않아야 한다.
     */
    @Test
    void keyDoesNotContainTheIdentifierInClearText() {
        when(valueOperations.increment(anyString())).thenReturn(1L);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);

        rateLimiter.consume("login-email", "soldier@example.com", LIMIT, WINDOW);
        rateLimiter.consume("login-email", "soldier@example.com", LIMIT, WINDOW);

        verify(valueOperations, times(2)).increment(key.capture());
        assertThat(key.getAllValues().get(0)).startsWith("rate-limit:login-email:");
        assertThat(key.getAllValues().get(0)).doesNotContain("soldier@example.com");
        assertThat(key.getAllValues().get(0)).isEqualTo(key.getAllValues().get(1));
    }

    /** 로그인에 성공하면 그동안 쌓인 실패 기록을 지운다. */
    @Test
    void resetDeletesTheCounterKey() {
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);

        rateLimiter.reset("login-email", "soldier@example.com");

        verify(redisTemplate).delete(key.capture());
        assertThat(key.getValue()).doesNotContain("soldier@example.com");
    }

    /** 카운터를 비우지 못해도 창이 지나면 사라지므로 요청을 실패시키지 않는다. */
    @Test
    void resetSwallowsRedisOutage() {
        doThrow(new RedisConnectionFailureException("down")).when(redisTemplate).delete(anyString());

        rateLimiter.reset("login-email", "soldier@example.com");
    }

    @Test
    void firstRequestStartsWindowAndIsAllowed() {
        when(valueOperations.increment(anyString())).thenReturn(1L);

        RateLimitResult result = rateLimiter.consume(SCOPE, IDENTIFIER, LIMIT, WINDOW);

        assertThat(result.allowed()).isTrue();
        assertThat(result.remaining()).isEqualTo(2);
    }

    @Test
    void requestOverTheLimitIsRejected() {
        when(valueOperations.increment(anyString())).thenReturn(4L);

        RateLimitResult result = rateLimiter.consume(SCOPE, IDENTIFIER, LIMIT, WINDOW);

        assertThat(result.allowed()).isFalse();
        assertThat(result.remaining()).isZero();
        assertThat(result.retryAfterSeconds()).isEqualTo(600);
    }

    @Test
    void connectionFailureRejectsInsteadOfLettingRequestThrough() {
        when(valueOperations.increment(anyString())).thenThrow(new RedisConnectionFailureException("redis down"));

        assertThatThrownBy(() -> rateLimiter.consume(SCOPE, IDENTIFIER, LIMIT, WINDOW))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RATE_LIMIT_UNAVAILABLE);
    }

    @Test
    void commandTimeoutRejectsInsteadOfLettingRequestThrough() {
        when(valueOperations.increment(anyString())).thenThrow(new QueryTimeoutException("timeout"));

        assertThatThrownBy(() -> rateLimiter.consume(SCOPE, IDENTIFIER, LIMIT, WINDOW))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RATE_LIMIT_UNAVAILABLE);
    }

    @Test
    void unexpectedErrorIsNotSwallowed() {
        when(valueOperations.increment(anyString())).thenThrow(new IllegalStateException("프로그래밍 오류"));

        assertThatThrownBy(() -> rateLimiter.consume(SCOPE, IDENTIFIER, LIMIT, WINDOW))
                .isInstanceOf(IllegalStateException.class);
    }
}
