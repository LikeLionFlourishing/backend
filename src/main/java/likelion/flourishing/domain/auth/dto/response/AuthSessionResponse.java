package likelion.flourishing.domain.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.LocalDateTime;
import likelion.flourishing.domain.auth.entity.User;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 명세 AuthSession 스키마. csrfToken은 상태 변경 요청의 X-CSRF-Token 값이다. */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AuthSessionResponse {

    private final UserResponse user;
    private final String csrfToken;
    private final OffsetDateTime expiresAt;

    public static AuthSessionResponse of(UserResponse user, String csrfToken, OffsetDateTime expiresAt) {
        return new AuthSessionResponse(user, csrfToken, expiresAt);
    }

    public static AuthSessionResponse from(User user, String csrfToken, LocalDateTime expiresAt) {
        return new AuthSessionResponse(
                UserResponse.from(user),
                csrfToken,
                expiresAt.atOffset(ZoneOffset.UTC)
        );
    }
}
