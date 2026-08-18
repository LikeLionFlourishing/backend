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
import likelion.flourishing.support.UuidV7;
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

    private CareResult(
            UUID reportId,
            UUID userId,
            UUID ruleSetId,
            UUID similarReportId,
            Integer similarityScore,
            ResultType resultType,
            AiGenerationStatus aiGenerationStatus,
            String summary,
            String clinicianMessage,
            LocalDateTime generatedAt
    ) {
        this.id = UuidV7.generate();
        this.reportId = reportId;
        this.userId = userId;
        this.ruleSetId = ruleSetId;
        this.similarReportId = similarReportId;
        this.similarityScore = similarityScore;
        this.resultType = resultType;
        this.aiGenerationStatus = aiGenerationStatus;
        this.summary = summary;
        this.clinicianMessage = clinicianMessage;
        this.retryUsed = false;
        this.generatedAt = generatedAt;
    }

    /**
     * 일반 관리 결과.
     *
     * <p>aiGenerationStatus는 GENERATED이거나 FALLBACK이다. DDL의 CHECK가 이 유형에
     * NOT_APPLICABLE과 clinicianMessage를 함께 허용하지 않는다.
     */
    public static CareResult selfCareGuide(
            UUID reportId,
            UUID userId,
            UUID ruleSetId,
            UUID similarReportId,
            Integer similarityScore,
            AiGenerationStatus aiGenerationStatus,
            String summary,
            LocalDateTime generatedAt
    ) {
        if (aiGenerationStatus == AiGenerationStatus.NOT_APPLICABLE) {
            throw new IllegalArgumentException("일반 관리 결과에는 AI 생성 상태가 있어야 합니다.");
        }
        assertSimilarExperiencePair(similarReportId, similarityScore);
        return new CareResult(
                reportId,
                userId,
                ruleSetId,
                similarReportId,
                similarityScore,
                ResultType.SELF_CARE_GUIDE,
                aiGenerationStatus,
                summary,
                null,
                generatedAt
        );
    }

    /**
     * 의료진 확인 결과.
     *
     * <p>여기서는 AI 설명을 만들지 않는다. 의료진 확인이 필요한 상황의 안내 문구는 승인된 규칙
     * 문구를 그대로 쓴다. 그래서 상태가 NOT_APPLICABLE이고 재생성도 열리지 않는다.
     */
    public static CareResult clinicianCheck(
            UUID reportId,
            UUID userId,
            UUID ruleSetId,
            UUID similarReportId,
            Integer similarityScore,
            String summary,
            String clinicianMessage,
            LocalDateTime generatedAt
    ) {
        if (clinicianMessage == null || clinicianMessage.isBlank()) {
            throw new IllegalArgumentException("의료진 확인 결과에는 안내 문구가 있어야 합니다.");
        }
        assertSimilarExperiencePair(similarReportId, similarityScore);
        return new CareResult(
                reportId,
                userId,
                ruleSetId,
                similarReportId,
                similarityScore,
                ResultType.CLINICIAN_CHECK,
                AiGenerationStatus.NOT_APPLICABLE,
                summary,
                clinicianMessage,
                generatedAt
        );
    }

    /** 관리 설명을 다시 만들 수 있는 결과인지. 대체 문구로 저장된 일반 관리 결과만 해당한다. */
    public boolean isRegenerable() {
        return resultType == ResultType.SELF_CARE_GUIDE && aiGenerationStatus == AiGenerationStatus.FALLBACK;
    }

    /**
     * 다시 만든 설명을 반영하고 재생성 기회를 쓴 것으로 표시한다.
     *
     * <p>성공했든 또 실패했든 retryUsed는 참이 된다. 한 번만 허용한다는 제한을 결과가 아니라 시도
     * 기준으로 잡아야 실패를 반복해 계속 호출하는 경로가 막힌다.
     */
    public void applyRegeneratedGuide(
            AiGenerationStatus aiGenerationStatus,
            String summary,
            LocalDateTime generatedAt
    ) {
        if (resultType != ResultType.SELF_CARE_GUIDE) {
            throw new IllegalStateException("일반 관리 결과만 설명을 다시 만들 수 있습니다.");
        }
        if (aiGenerationStatus == AiGenerationStatus.NOT_APPLICABLE) {
            throw new IllegalArgumentException("일반 관리 결과에는 AI 생성 상태가 있어야 합니다.");
        }
        this.aiGenerationStatus = aiGenerationStatus;
        this.summary = summary;
        this.generatedAt = generatedAt;
        this.retryUsed = true;
    }

    /**
     * 유사 경험 ID와 점수는 항상 함께 있거나 함께 없어야 한다.
     *
     * <p>MySQL이 ON DELETE SET NULL 대상 컬럼을 CHECK에 쓸 수 없어 DB가 짝을 강제하지 못한다.
     * 조회 쪽은 짝이 깨지면 불변식 위반으로 보므로 만들 때 막는다.
     */
    private static void assertSimilarExperiencePair(UUID similarReportId, Integer similarityScore) {
        if ((similarReportId == null) != (similarityScore == null)) {
            throw new IllegalArgumentException("유사 경험 ID와 점수는 함께 있어야 합니다.");
        }
    }
}
