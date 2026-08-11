package likelion.flourishing.global.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 인증 세션 쿠키와 요청 제한 설정.
 *
 * <p>운영에서는 명세대로 {@code __Host-session} 쿠키를 Secure로 내보낸다. {@code __Host-} 접두사는
 * Secure와 {@code Path=/}를 요구하므로 HTTP로 띄우는 로컬에서는 local 프로파일이 이름과 secure를 낮춘다.
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(Session session, RateLimit rateLimit) {

    public AuthProperties {
        session = session == null ? Session.defaults() : session;
        rateLimit = rateLimit == null ? RateLimit.defaults() : rateLimit;
    }

    public record Session(String cookieName, boolean secure, String sameSite, Duration ttl) {

        public Session {
            cookieName = cookieName == null || cookieName.isBlank() ? "__Host-session" : cookieName;
            sameSite = sameSite == null || sameSite.isBlank() ? "Lax" : sameSite;
            ttl = ttl == null ? Duration.ofDays(14) : ttl;
        }

        private static Session defaults() {
            return new Session("__Host-session", true, "Lax", Duration.ofDays(14));
        }
    }

    public record RateLimit(Rule register, Rule loginPerIp, Rule loginPerEmail) {

        public RateLimit {
            register = register == null ? new Rule(10, Duration.ofHours(1)) : register;
            loginPerIp = loginPerIp == null ? new Rule(20, Duration.ofMinutes(10)) : loginPerIp;
            loginPerEmail = loginPerEmail == null ? new Rule(10, Duration.ofMinutes(10)) : loginPerEmail;
        }

        private static RateLimit defaults() {
            return new RateLimit(null, null, null);
        }

        public record Rule(int limit, Duration window) {

            public Rule {
                limit = limit <= 0 ? 10 : limit;
                window = window == null ? Duration.ofMinutes(10) : window;
            }
        }
    }
}
