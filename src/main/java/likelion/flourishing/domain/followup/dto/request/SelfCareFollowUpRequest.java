package likelion.flourishing.domain.followup.dto.request;

import jakarta.validation.constraints.NotNull;
import likelion.flourishing.domain.followup.entity.ActionCompletion;
import likelion.flourishing.domain.followup.entity.FollowUpKind;
import likelion.flourishing.domain.followup.entity.SkinChange;

/** 명세 SelfCareFollowUpRequest. 관리 안내를 받은 보고의 경과다. */
public record SelfCareFollowUpRequest(

        @NotNull
        FollowUpKind kind,

        @NotNull
        SkinChange skinChange,

        @NotNull
        ActionCompletion actionCompletion
) implements SaveFollowUpRequest {
}
