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
@Table(name = "rule_sets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RuleSet {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "version_code", nullable = false, length = 30)
    private String versionCode;
}
