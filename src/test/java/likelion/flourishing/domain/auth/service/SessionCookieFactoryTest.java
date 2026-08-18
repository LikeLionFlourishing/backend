package likelion.flourishing.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import likelion.flourishing.global.config.AuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * 세션 쿠키의 속성 테스트.
 *
 * <p>확인하는 것: 발급 쿠키의 Max-Age가 세션 TTL과 같은지, 만료 쿠키가 Max-Age=0인지,
 * 두 쿠키 모두 HttpOnly·Secure·SameSite·Path를 갖추는지, 쿠키에서 토큰을 읽어내는지.
 *
 * <p>Max-Age를 검증하는 이유는 이 값이 빠지면 브라우저 세션 쿠키가 되어, DB 세션이 14일 남아도
 * 브라우저를 닫는 순간 로그인이 풀리기 때문이다.
 */
class SessionCookieFactoryTest {

    private static final String COOKIE_NAME = "__Host-session";

    private final SessionCookieFactory sessionCookieFactory =
            new SessionCookieFactory(new AuthProperties(null, null));

    @Test
    void createdCookieLivesAsLongAsTheSession() {
        ResponseCookie cookie = sessionCookieFactory.create("opaque-session-token");

        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(14));
        assertThat(cookie.toString()).contains("Max-Age=1209600");
    }

    @Test
    void createdCookieCarriesSecurityAttributes() {
        ResponseCookie cookie = sessionCookieFactory.create("opaque-session-token");

        assertThat(cookie.getName()).isEqualTo(COOKIE_NAME);
        assertThat(cookie.getValue()).isEqualTo("opaque-session-token");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getPath()).isEqualTo("/");
    }

    @Test
    void clearedCookieExpiresImmediately() {
        ResponseCookie cookie = sessionCookieFactory.clear();

        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.isHttpOnly()).isTrue();
    }

    @Test
    void readsTokenFromRequestCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("other", "x"), new Cookie(COOKIE_NAME, "opaque-session-token"));

        assertThat(sessionCookieFactory.readToken(request)).contains("opaque-session-token");
    }

    @Test
    void readsNothingWhenCookieIsMissingOrBlank() {
        MockHttpServletRequest noCookies = new MockHttpServletRequest();
        assertThat(sessionCookieFactory.readToken(noCookies)).isEmpty();

        MockHttpServletRequest blank = new MockHttpServletRequest();
        blank.setCookies(new Cookie(COOKIE_NAME, ""));
        assertThat(sessionCookieFactory.readToken(blank)).isEmpty();
    }
}
