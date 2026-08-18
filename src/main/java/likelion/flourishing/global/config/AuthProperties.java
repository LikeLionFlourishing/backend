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

        /**
         * {@code __Host-} 접두사는 브라우저가 Secure와 {@code Path=/}를 함께 요구한다. 둘이 각각 다른
         * 환경변수라 운영에서 secure만 내리면 이름은 {@code __Host-session}인데 Secure가 빠진 쿠키가
         * 나가고 브라우저가 조용히 버린다. 오류 없이 로그인만 안 되는 형태라 기동 때 막는다.
         *
         * <p>SameSite=None도 브라우저가 Secure를 요구하므로 같은 자리에서 확인한다.
         */
        public Session {
            cookieName = cookieName == null || cookieName.isBlank() ? "__Host-session" : cookieName;
            sameSite = sameSite == null || sameSite.isBlank() ? "Lax" : sameSite;
            ttl = ttl == null ? Duration.ofDays(14) : ttl;

            if (cookieName.startsWith("__Host-") && !secure) {
                throw new IllegalStateException(
                        "__Host- 접두사 쿠키는 Secure여야 합니다. SESSION_COOKIE_SECURE를 내렸다면 "
                                + "SESSION_COOKIE_NAME도 접두사 없는 이름으로 바꿔야 합니다."
                );
            }
            if ("None".equalsIgnoreCase(sameSite) && !secure) {
                throw new IllegalStateException("SameSite=None 쿠키는 Secure여야 합니다.");
            }
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
