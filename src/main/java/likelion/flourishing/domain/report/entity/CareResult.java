package likelion.flourishing.domain.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import likelion.flourishing.global.entity.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 피부 보고 당시 사용자에게 실제로 표시한 관리 결과. */
@Entity
@Getter
@Table(name = "care_results")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CareResult extends BaseTimeEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "report_id", nullable = false, updatable = false)
    private UUID reportId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "rule_set_id", nullable = false, updatable = false)
    private UUID ruleSetId;

    @Column(name = "similar_report_id")
    private UUID similarReportId;

    @Column(name = "similarity_score")
    private Integer similarityScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_type", nullable = false, length = 30)
    private ResultType resultType;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_generation_status", nullable = false, length = 30)
    private AiGenerationStatus aiGenerationStatus;

    @Column(name = "summary", nullable = false, length = 500)
    private String summary;

    @Column(name = "clinician_message", length = 1000)
    private String clinicianMessage;

    @Column(name = "retry_used", nullable = false)
    private boolean retryUsed;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;
}
