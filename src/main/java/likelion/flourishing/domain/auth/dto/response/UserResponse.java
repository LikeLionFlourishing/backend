package likelion.flourishing.domain.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import likelion.flourishing.domain.auth.entity.User;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 명세 User 스키마. */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UserResponse {

    private final UUID id;

    private final String email;

    /** 명세가 소문자 한 단어(signupcompleted)로 정의한 필드라 직렬화 이름을 고정한다. */
    @JsonProperty("signupcompleted")
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
