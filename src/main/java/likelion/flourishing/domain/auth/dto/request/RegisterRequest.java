package likelion.flourishing.domain.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import likelion.flourishing.global.validation.MaxByteLength;

/** 명세 RegisterRequest. 정의되지 않은 필드는 spring.jackson.deserialization.fail-on-unknown-properties 설정으로 거부한다. */
public record RegisterRequest(

        @NotBlank
        @Email
        @Size(max = 254)
        String email,

        /*
         * 문자 수 상한 128은 명세 값이고, 바이트 상한 72는 BCrypt가 그 뒤를 아예 읽지 않아서 더 건다.
         * 없으면 앞 72바이트가 같은 서로 다른 비밀번호가 같은 해시를 만들어, 가입할 때 정한 뒷부분이
         * 로그인에서 검사되지 않는다.
         */
        @NotBlank
        @Size(min = 12, max = 128)
        @MaxByteLength(72)
        String password
) {
}
