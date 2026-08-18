package likelion.flourishing.domain.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 명세 RegisterRequest. 정의되지 않은 필드는 spring.jackson.deserialization.fail-on-unknown-properties 설정으로 거부한다. */
public record RegisterRequest(

        @NotBlank
        @Email
        @Size(max = 254)
        String email,

        @NotBlank
        @Size(min = 12, max = 128)
        String password
) {
}
