package likelion.flourishing.domain.home.dto.request;

import jakarta.validation.constraints.NotNull;
import likelion.flourishing.domain.home.entity.CheckInState;

/**
 * 명세 saveNoDiscomfortCheckIn 요청 본문. 정의되지 않은 필드는
 * spring.jackson.deserialization.fail-on-unknown-properties 설정으로 거부한다.
 *
 * <p>명세가 state를 const NO_DISCOMFORT로 못 박았다. 이 엔드포인트로 SKIN_REPORT를
 * 저장할 수는 없다. 그 상태는 피부 보고가 확정될 때 서버가 스스로 바꾸는 값이다.
 * enum에는 두 값이 다 있으므로 NO_DISCOMFORT인지는 여기서 따로 확인한다.
 */
public record SaveDailyCheckInRequest(

        @NotNull
        CheckInState state
) {

    public boolean isNoDiscomfort() {
        return state == CheckInState.NO_DISCOMFORT;
    }
}
