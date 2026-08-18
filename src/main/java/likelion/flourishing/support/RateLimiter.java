package likelion.flourishing.support;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 고정 창(fixed window) 카운터. 키 하나가 창 하나이며 첫 요청에서 TTL을 건다.
 *
 * <p>Redis에 붙지 못하면 요청을 통과시키지 않고 503을 낸다. 통과시키면 Redis가 멈춘 동안
 * 로그인 시도 제한(FR-AUTH-006)이 통째로 사라져, 장애 시점이 그대로 무제한 대입 창이 된다.
 *
 * <p>붙지 못한 경우와 명령 시간 초과만 503으로 바꾸고 나머지 예외는 그대로 올린다.
 * 프로그래밍 오류까지 여기서 삼키면 제한이 동작하지 않는 것을 아무도 눈치채지 못한다.
 */
@Component
public class RateLimiter {

    private static final String KEY_PREFIX = "rate-limit:";

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    public RateLimiter(StringRedisTemplate redisTemplate, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.clock = clock;
    }

    public RateLimitResult consume(String scope, String identifier, int limit, Duration window) {
        String key = KEY_PREFIX + scope + ":" + identifier;
        long now = clock.instant().getEpochSecond();

        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) {
                // 파이프라인이나 트랜잭션 안에서만 나오는 값이라 여기서는 세지 못한 것으로 본다.
                throw new BusinessException(ErrorCode.RATE_LIMIT_UNAVAILABLE);
            }
            if (count == 1L) {
                redisTemplate.expire(key, window);
            }

            long ttlSeconds = resolveTtlSeconds(key, window);
            long remaining = Math.max(0L, limit - count);
            return new RateLimitResult(count <= limit, limit, remaining, ttlSeconds, now + ttlSeconds);
        } catch (RedisConnectionFailureException | QueryTimeoutException redisUnavailable) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_UNAVAILABLE);
        }
    }

    private long resolveTtlSeconds(String key, Duration window) {
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (ttl == null || ttl < 0) {
            redisTemplate.expire(key, window);
            return window.toSeconds();
        }
        return ttl;
    }
}
