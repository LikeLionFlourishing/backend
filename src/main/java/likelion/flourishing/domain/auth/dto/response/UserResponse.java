package likelion.flourishing.domain.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import likelion.flourishing.domain.auth.entity.User;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 명세 User 스키마.
 *
 * <p>명세 원문은 이 필드를 signupcompleted(전부 소문자)로 적었는데, 다른 모든 필드가 camelCase라
 * 오타로 보고 signupCompleted로 내보낸다. 명세도 같은 방향으로 고친다.
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UserResponse {

    private final UUID id;

    private final String email;

    private final boolean signupCompleted;

    private final OffsetDateTime createdAt;

    public static UserResponse of(UUID id, String email, boolean signupCompleted, OffsetDateTime createdAt) {
        return new UserResponse(id, email, signupCompleted, createdAt);
    }

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.isSignupCompleted(),
                user.getCreatedAt().atOffset(ZoneOffset.UTC)
        );
    }
}
