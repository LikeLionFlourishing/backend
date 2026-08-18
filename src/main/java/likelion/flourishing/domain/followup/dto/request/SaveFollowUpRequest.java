package likelion.flourishing.domain.followup.dto.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import likelion.flourishing.domain.followup.entity.ActionCompletion;
import likelion.flourishing.domain.followup.entity.FollowUpKind;
import likelion.flourishing.domain.followup.entity.SkinChange;

/**
 * 명세 SaveFollowUpRequest. kind 값에 따라 본문 모양이 갈리는 oneOf 스키마다.
 *
 * <p>kind는 어떤 하위 타입인지 고르는 값이면서 응답에도 그대로 나가는 값이라
 * EXISTING_PROPERTY와 visible = true로 설정해 역직렬화 뒤에도 필드에 남게 한다.
 *
 * <p>kind가 없거나 모르는 값이면 Jackson이 객체를 만들지 못해 400이 된다.
 * 값 자체가 틀린 경우(422)와 달리 어떤 타입인지조차 정하지 못한 단계의 실패다.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "kind",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = SelfCareFollowUpRequest.class, name = "SELF_CARE"),
        @JsonSubTypes.Type(value = ClinicianFollowUpRequest.class, name = "CLINICIAN_CHECK")
})
public sealed interface SaveFollowUpRequest permits SelfCareFollowUpRequest, ClinicianFollowUpRequest {

    FollowUpKind kind();

    SkinChange skinChange();

    /** 명세 v2_1에서 두 종류가 모두 받는 값이라 공통 계약으로 올렸다. */
    ActionCompletion actionCompletion();
}
