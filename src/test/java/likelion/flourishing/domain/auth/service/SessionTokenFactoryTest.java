package likelion.flourishing.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 세션 토큰과 CSRF 토큰을 만드는 규칙 테스트. 보안 성질을 직접 확인하는 자리다.
 *
 * <p>확인하는 것: 매번 다른 토큰이 나오는지(예측 가능하면 남의 세션을 만들 수 있다),
 * 해시가 BINARY(32) 컬럼에 맞는 32바이트인지, 같은 세션 토큰에서는 같은 CSRF 토큰이
 * 다시 계산되는지(그래서 CSRF 토큰을 따로 저장하지 않아도 된다), 명세가 요구하는 32자 이상인지,
 * 그리고 원본 토큰에만 일치 판정이 나는지.
 */
class SessionTokenFactoryTest {

    private final SessionTokenFactory sessionTokenFactory = new SessionTokenFactory();

    @Test
    void createsDifferentTokenEveryTime() {
        assertThat(sessionTokenFactory.createSessionToken())
                .isNotEqualTo(sessionTokenFactory.createSessionToken());
    }

    @Test
    void hashFitsBinary32Column() {
        assertThat(sessionTokenFactory.hash(sessionTokenFactory.createSessionToken())).hasSize(32);
    }

    @Test
    void derivesSameCsrfTokenForSameSessionToken() {
        String sessionToken = sessionTokenFactory.createSessionToken();

        assertThat(sessionTokenFactory.deriveCsrfToken(sessionToken))
                .isEqualTo(sessionTokenFactory.deriveCsrfToken(sessionToken))
                .isNotEqualTo(sessionToken);
    }

    @Test
    void csrfTokenIsLongEnoughForSpec() {
        String csrfToken = sessionTokenFactory.deriveCsrfToken(sessionTokenFactory.createSessionToken());

        assertThat(csrfToken.length()).isGreaterThanOrEqualTo(32);
    }

    @Test
    void matchesOnlyTheOriginalToken() {
        String sessionToken = sessionTokenFactory.createSessionToken();
        String csrfToken = sessionTokenFactory.deriveCsrfToken(sessionToken);
        byte[] storedHash = sessionTokenFactory.hash(csrfToken);

        assertThat(sessionTokenFactory.matches(storedHash, csrfToken)).isTrue();
        assertThat(sessionTokenFactory.matches(storedHash, "다른-토큰")).isFalse();
        assertThat(sessionTokenFactory.matches(storedHash, null)).isFalse();
        assertThat(sessionTokenFactory.matches(storedHash, " ")).isFalse();
    }
}
