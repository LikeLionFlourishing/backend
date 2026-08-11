package likelion.flourishing.domain.followup.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import likelion.flourishing.domain.followup.dto.request.ClinicianFollowUpRequest;
import likelion.flourishing.domain.followup.dto.request.SaveFollowUpRequest;
import likelion.flourishing.domain.followup.dto.request.SelfCareFollowUpRequest;
import likelion.flourishing.support.UuidV7;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 보고 하나에 딸리는 다음 날 경과. report_id에 유니크 제약이 있어 보고당 한 건만 남는다.
 *
 * <p>종류에 따라 채우는 컬럼이 다르고 DDL의 CHECK가 짝을 강제한다. SELF_CARE면
 * action_completion만, CLINICIAN_CHECK면 clinician_check_status만 값이 있어야 한다.
 * 잘못된 조합이 만들어지지 않도록 생성자를 막고 종류별 정적 메서드로만 만들게 했다.
 *
 * <p>한 번 저장하면 고치지 않는다. 그래서 updated_at이 없고 BaseTimeEntity도 상속하지 않는다.
 */
@Entity
@Getter
@Table(name = "follow_ups")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FollowUp {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "report_id", nullable = false, updatable = false)
    private UUID reportId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 30)
    private FollowUpKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "skin_change", nullable = false, length = 30)
    private SkinChange skinChange;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_completion", length = 30)
    private ActionCompletion actionCompletion;

    @Enumerated(EnumType.STRING)
    @Column(name = "clinician_check_status", length = 40)
    private ClinicianCheckStatus clinicianCheckStatus;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private FollowUp(
            UUID reportId,
            UUID userId,
            FollowUpKind kind,
            SkinChange skinChange,
            ActionCompletion actionCompletion,
            ClinicianCheckStatus clinicianCheckStatus,
            LocalDateTime submittedAt
    ) {
        this.id = UuidV7.generate();
        this.reportId = reportId;
        this.userId = userId;
        this.kind = kind;
        this.skinChange = skinChange;
        this.actionCompletion = actionCompletion;
        this.clinicianCheckStatus = clinicianCheckStatus;
        this.submittedAt = submittedAt;
    }

    public static FollowUp of(UUID reportId, UUID userId, SaveFollowUpRequest request, LocalDateTime submittedAt) {
        return switch (request) {
            case SelfCareFollowUpRequest selfCare -> new FollowUp(
                    reportId,
                    userId,
                    FollowUpKind.SELF_CARE,
                    selfCare.skinChange(),
                    selfCare.actionCompletion(),
                    null,
                    submittedAt
            );
            case ClinicianFollowUpRequest clinician -> new FollowUp(
                    reportId,
                    userId,
                    FollowUpKind.CLINICIAN_CHECK,
                    clinician.skinChange(),
                    null,
                    clinician.clinicianCheckStatus(),
                    submittedAt
            );
        };
    }

    /**
     * 이미 저장된 경과가 이번 요청과 같은 내용인지.
     *
     * <p>같으면 저장된 값을 그대로 돌려주고, 다르면 덮어쓰기 시도로 보아 409를 낸다.
     * 네트워크가 끊겨 같은 요청을 다시 보낸 경우와 값을 바꾸려는 경우를 가르는 기준이다.
     */
    public boolean hasSameContentAs(SaveFollowUpRequest request) {
        if (kind != request.kind() || skinChange != request.skinChange()) {
            return false;
        }
        return switch (request) {
            case SelfCareFollowUpRequest selfCare ->
                    Objects.equals(actionCompletion, selfCare.actionCompletion());
            case ClinicianFollowUpRequest clinician ->
                    Objects.equals(clinicianCheckStatus, clinician.clinicianCheckStatus());
        };
    }
}
