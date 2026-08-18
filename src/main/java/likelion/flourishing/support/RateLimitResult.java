package likelion.flourishing.support;

/**
 * 요청 제한 확인 결과. 명세의 429 응답 헤더 값을 그대로 담는다.
 *
 * @param allowed            이번 요청을 허용할지 여부
 * @param limit              창(window)당 허용 횟수
 * @param remaining          남은 허용 횟수
 * @param retryAfterSeconds  재시도까지 기다릴 초
 * @param resetEpochSecond   제한이 풀리는 Unix timestamp
 */
public record RateLimitResult(
        boolean allowed,
        int limit,
        long remaining,
        long retryAfterSeconds,
        long resetEpochSecond
) {
}
