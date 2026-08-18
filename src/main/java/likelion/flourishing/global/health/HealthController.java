package likelion.flourishing.global.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Health", description = "배포 상태 확인용 공개 엔드포인트")
@RestController
public class HealthController {

    /**
     * 인증 없이 부르는 경로다. 전역 보안 요구사항이 걸려 있으면 문서에 잠금으로 표시되므로 비운다.
     */
    @Operation(summary = "서버 상태 확인", description = "정상이면 본문 OK 를 반환한다. Caddy 와 컨테이너 헬스체크가 이 경로를 쓴다.")
    @SecurityRequirements
    @GetMapping(value = "/health", produces = MediaType.TEXT_PLAIN_VALUE)
    public String health() {
        return "OK";
    }
}
