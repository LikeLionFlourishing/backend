package likelion.flourishing.domain.auth.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Optional;
import likelion.flourishing.global.config.AuthProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 명세의 세션 쿠키를 만들고 읽는다.
 * 발급 쿠키의 Max-Age는 세션 TTL과 같게 두어 브라우저를 닫아도 로그인이 유지되게 하고,
 * 만료 쿠키만 Max-Age=0으로 내린다.
 */
@Component
public class SessionCookieFactory {

    private final AuthProperties authProperties;

    public SessionCookieFactory(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    /**
     * 세션 TTL과 같은 Max-Age를 실어 발급한다. DB 세션은 14일 남아 있는데 쿠키가 브라우저 종료로
     * 사라지면 로그인 유지 계약이 깨지므로 두 만료를 같은 값으로 맞춘다.
     */
    public ResponseCookie create(String sessionToken) {
        return baseCookie(sessionToken)
                .maxAge(authProperties.session().ttl())
                .build();
    }

    public ResponseCookie clear() {
        return baseCookie("").maxAge(0).build();
    }

    public Optional<String> readToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> authProperties.session().cookieName().equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        AuthProperties.Session session = authProperties.session();
        return ResponseCookie.from(session.cookieName(), value)
                .path("/")
                .httpOnly(true)
                .secure(session.secure())
                .sameSite(session.sameSite());
    }
}
