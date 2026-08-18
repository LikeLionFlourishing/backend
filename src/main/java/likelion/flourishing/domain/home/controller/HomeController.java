package likelion.flourishing.domain.home.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.home.dto.request.SaveDailyCheckInRequest;
import likelion.flourishing.domain.home.dto.response.DailyCheckInResponse;
import likelion.flourishing.domain.home.dto.response.HomeResponse;
import likelion.flourishing.domain.home.service.HomeService;
import likelion.flourishing.domain.home.service.SavedDailyCheckIn;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 홈 엔드포인트. 명세 Home 태그의 두 개를 담당한다.
 *
 * <p>GET /v1/home은 홈 화면이 필요한 것을 한 번에 준다. PUT /v1/daily-check-ins/{date}는
 * "오늘 불편 없음"을 저장한다.
 *
 * <p>저장은 새로 만들었으면 201, 같은 값이 이미 있으면 200으로 나눈다. 명세가 두 경우를
 * 구분해 두어서, 프론트가 방금 저장된 것인지 원래 있던 것인지 알 수 있다.
 */
@Tag(name = "Home", description = "홈 화면 집계 정보와 오늘 상태")
@RestController
@RequestMapping("/v1")
public class HomeController {

    private final HomeService homeService;

    public HomeController(HomeService homeService) {
        this.homeService = homeService;
    }

    @Operation(summary = "홈 화면 정보 조회")
    @GetMapping("/home")
    public ResponseEntity<HomeResponse> getHome(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(homeService.getHome(principal));
    }

    @Operation(summary = "오늘 불편 없음 저장")
    @PutMapping("/daily-check-ins/{date}")
    public ResponseEntity<DailyCheckInResponse> saveNoDiscomfortCheckIn(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody SaveDailyCheckInRequest request
    ) {
        SavedDailyCheckIn saved = homeService.saveNoDiscomfort(principal, date, request);
        return ResponseEntity.status(saved.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(saved.response());
    }
}
