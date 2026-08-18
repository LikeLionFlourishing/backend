package likelion.flourishing.domain.report.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import likelion.flourishing.global.entity.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사용자가 확정한 피부 보고와 다중 선택값. 민감 원문은 인증 암호문으로만 보관한다. */
@Entity
@Getter
@Table(name = "skin_reports")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SkinReport extends BaseTimeEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "report_date", nullable = false, updatable = false)
    private LocalDate reportDate;

    @Column(name = "raw_text_encrypted", nullable = false, updatable = false, length = 4096)
    private byte[] rawTextEncrypted;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_area", nullable = false, length = 40)
    private BodyArea primaryArea;

    @Column(name = "other_areas_note_encrypted", length = 4096)
    private byte[] otherAreasNoteEncrypted;

    @Enumerated(EnumType.STRING)
    @Column(name = "care_availability", nullable = false, length = 50)
    private CareAvailability careAvailability;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_type", nullable = false, length = 30)
    private ResultType resultType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ReportStatus status;

    @Column(name = "follow_up_available_at", nullable = false)
    private LocalDateTime followUpAvailableAt;

    @Column(name = "follow_up_expires_at", nullable = false)
    private LocalDateTime followUpExpiresAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "report_appearances", joinColumns = @JoinColumn(name = "report_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "appearance_code", nullable = false, length = 40)
    private Set<Appearance> appearances = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "report_sensations", joinColumns = @JoinColumn(name = "report_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "sensation_code", nullable = false, length = 40)
    private Set<Sensation> sensations = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "report_situations", joinColumns = @JoinColumn(name = "report_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "situation_code", nullable = false, length = 50)
    private Set<Situation> situations = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "report_pre_care_checks", joinColumns = @JoinColumn(name = "report_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "check_code", nullable = false, length = 50)
    private Set<PreCareCheck> preCareChecks = new LinkedHashSet<>();
}
