package likelion.flourishing.domain.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "care_rule_versions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CareRuleVersion {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "rule_id", nullable = false, updatable = false)
    private UUID ruleId;

    @Column(name = "rule_set_id", nullable = false, updatable = false)
    private UUID ruleSetId;

    @Column(name = "version_code", nullable = false, length = 20)
    private String versionCode;
}
