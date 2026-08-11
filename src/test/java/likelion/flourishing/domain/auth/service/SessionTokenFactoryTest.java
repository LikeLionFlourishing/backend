package likelion.flourishing.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

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
