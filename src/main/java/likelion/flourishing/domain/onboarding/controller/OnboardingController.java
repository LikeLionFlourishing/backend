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
