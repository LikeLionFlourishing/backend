package likelion.flourishing.domain.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 명세 LoginRequest. 로그인은 가입 규칙이 바뀌어도 열려 있어야 하므로 길이 하한을 두지 않는다. */
public record LoginRequest(

        @NotBlank
        @Email
        @Size(max = 254)
        String email,

        @NotBlank
        @Size(max = 128)
        String password
) {
}
