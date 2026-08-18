package likelion.flourishing.domain.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import likelion.flourishing.global.validation.MaxByteLength;

/** 명세 LoginRequest. 로그인은 가입 규칙이 바뀌어도 열려 있어야 하므로 길이 하한을 두지 않는다. */
public record LoginRequest(

        @NotBlank
        @Email
        @Size(max = 254)
        String email,

        /*
         * 가입에서 72바이트를 넘는 비밀번호를 막으므로 그 길이로 만들어진 계정은 없다. 로그인에서도
         * 같이 막아야 BCrypt가 잘라 읽은 앞 72바이트만 맞아도 통과하는 길이 남지 않는다.
         */
        @NotBlank
        @Size(max = 128)
        @MaxByteLength(72)
        String password
) {
}
