package likelion.flourishing.domain.followup.dto.request;

import jakarta.validation.constraints.NotNull;
import likelion.flourishing.domain.followup.entity.ActionCompletion;
import likelion.flourishing.domain.followup.entity.ClinicianCheckStatus;
import likelion.flourishing.domain.followup.entity.FollowUpKind;
import likelion.flourishing.domain.followup.entity.SkinChange;

/**
 * 명세 ClinicianFollowUpRequest. 진료 확인 안내를 받은 보고의 경과다.
 *
 * <p>진단명, 처방, 소견 같은 의료진의 확인 내용은 받지 않는다. 확인을 했는지만 묻는다.
 *
 * <p>명세 v2_1에서 actionCompletion이 필수로 들어왔다. 의료진 확인 여부와 행동 실행 여부는
 * 다른 질문이라 둘 다 받는다.
 */
public record ClinicianFollowUpRequest(

        @NotNull
        FollowUpKind kind,

        @NotNull
        SkinChange skinChange,

        @NotNull
        ActionCompletion actionCompletion,

        @NotNull
        ClinicianCheckStatus clinicianCheckStatus
) implements SaveFollowUpRequest {
}
