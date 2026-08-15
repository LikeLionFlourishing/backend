package likelion.flourishing.domain.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import likelion.flourishing.global.entity.BaseTimeEntity;
import likelion.flourishing.support.UuidV7;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 이메일·비밀번호 사용자 계정. */
@Entity
@Getter
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "normalized_email", nullable = false, length = 254)
    private String normalizedEmail;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "signup_completed_at")
    private LocalDateTime signupCompletedAt;

    private User(UUID id, String email, String normalizedEmail, String passwordHash) {
        this.id = id;
        this.email = email;
        this.normalizedEmail = normalizedEmail;
        this.passwordHash = passwordHash;
    }

    public static User register(String email, String passwordHash) {
        String trimmedEmail = email.trim();
        return new User(UuidV7.generate(), trimmedEmail, normalizeEmail(trimmedEmail), passwordHash);
    }

    /** 대소문자와 앞뒤 공백 차이로 같은 사람이 두 번 가입하지 못하게 한다. */
    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** 명세의 User.signupCompleted 값. 온보딩까지 마쳐야 true가 된다. */
    public boolean isSignupCompleted() {
        return signupCompletedAt != null;
    }

    /** 온보딩 기능에서 최초 설정을 마쳤을 때 호출한다. 두 번째 호출은 최초 시각을 유지한다. */
    public void completeSignup(LocalDateTime completedAt) {
        if (signupCompletedAt == null) {
            signupCompletedAt = completedAt;
        }
    }
}
