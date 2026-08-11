package likelion.flourishing.support;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 고정 창(fixed window) 카운터. 키 하나가 창 하나이며 첫 요청에서 TTL을 건다.
 * Redis를 쓸 수 없으면 요청을 막지 않는다. 부가 기능 때문에 로그인 자체가 실패하면 안 되기 때문이다.
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
                return allowedWithoutCounting(limit, window, now);
            }
            if (count == 1L) {
                redisTemplate.expire(key, window);
            }

            long ttlSeconds = resolveTtlSeconds(key, window);
            long remaining = Math.max(0L, limit - count);
            return new RateLimitResult(count <= limit, limit, remaining, ttlSeconds, now + ttlSeconds);
        } catch (RuntimeException redisUnavailable) {
            return allowedWithoutCounting(limit, window, now);
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

    private RateLimitResult allowedWithoutCounting(int limit, Duration window, long now) {
        return new RateLimitResult(true, limit, limit, window.toSeconds(), now + window.toSeconds());
    }
}
