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
 * 발급 쿠키는 Max-Age 없이 내보내 브라우저 세션 쿠키로 두고, 만료 쿠키만 Max-Age=0으로 내린다.
 */
@Component
public class SessionCookieFactory {

    private final AuthProperties authProperties;

    public SessionCookieFactory(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    public ResponseCookie create(String sessionToken) {
        return baseCookie(sessionToken).build();
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
