package likelion.flourishing.domain.onboarding.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.onboarding.dto.request.OnboardingRequest;
import likelion.flourishing.domain.onboarding.dto.response.OnboardingResponse;
import likelion.flourishing.domain.onboarding.service.OnboardingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 온보딩 엔드포인트. 명세 Onboarding 태그의 PUT /v1/me/onboarding 하나를 담당한다.
 *
 * <p>가입 직후 한 번 거치는 화면으로, 필수 동의와 알림 선택을 함께 저장하고 가입을 완료 처리한다.
 * 이 요청이 성공해야 GET /v1/me의 signupcompleted가 true가 된다.
 *
 * <p>POST가 아니라 PUT인 이유는 같은 요청을 여러 번 보내도 결과가 같아야 하기 때문이다.
 * 사용자가 완료 버튼을 두 번 누르거나 화면을 다시 열어도 이력이 중복되지 않는다.
 */
@Tag(name = "Onboarding", description = "이용범위 동의와 최초 설정")
@RestController
@RequestMapping("/v1")
public class OnboardingController {

    private final OnboardingService onboardingService;

    public OnboardingController(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @Operation(summary = "온보딩 완료")
    @PutMapping("/me/onboarding")
    public ResponseEntity<OnboardingResponse> completeOnboarding(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody OnboardingRequest request
    ) {
        return ResponseEntity.ok(onboardingService.complete(principal, request));
    }
}
