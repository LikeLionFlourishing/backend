package likelion.flourishing.domain.followup.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import likelion.flourishing.domain.followup.entity.ActionCompletion;
import likelion.flourishing.domain.followup.entity.ClinicianCheckStatus;
import likelion.flourishing.domain.followup.entity.FollowUp;
import likelion.flourishing.domain.followup.entity.FollowUpKind;
import likelion.flourishing.domain.followup.entity.SkinChange;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 명세 FollowUp 스키마. kind에 따라 두 가지 모양이 되는 oneOf라, 쓰지 않는 필드는
 * NON_NULL로 응답에서 아예 빠지게 한다. 그래서 SELF_CARE에는 actionCompletion만,
 * CLINICIAN_CHECK에는 clinicianCheckStatus만 담긴다.
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class FollowUpResponse {

    private final UUID reportId;

    private final FollowUpKind kind;

    private final SkinChange skinChange;

    private final ActionCompletion actionCompletion;

    private final ClinicianCheckStatus clinicianCheckStatus;

    private final OffsetDateTime submittedAt;

    public static FollowUpResponse of(
            UUID reportId,
            FollowUpKind kind,
            SkinChange skinChange,
            ActionCompletion actionCompletion,
            ClinicianCheckStatus clinicianCheckStatus,
            OffsetDateTime submittedAt
    ) {
        return new FollowUpResponse(reportId, kind, skinChange, actionCompletion, clinicianCheckStatus, submittedAt);
    }

    public static FollowUpResponse from(FollowUp followUp) {
        return new FollowUpResponse(
                followUp.getReportId(),
                followUp.getKind(),
                followUp.getSkinChange(),
                followUp.getActionCompletion(),
                followUp.getClinicianCheckStatus(),
                followUp.getSubmittedAt().atOffset(ZoneOffset.UTC)
        );
    }
}
