package likelion.flourishing.domain.home.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import likelion.flourishing.global.entity.BaseTimeEntity;
import likelion.flourishing.support.UuidV7;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 하루 한 건의 상태 기록. (user_id, check_in_date)에 유니크 제약이 걸려 있다.
 *
 * <p>report_id는 피부 보고가 확정됐을 때만 채워지고, DDL의 CHECK가 state와 짝을 강제한다.
 * NO_DISCOMFORT면 report_id가 반드시 NULL, SKIN_REPORT면 반드시 값이 있어야 한다.
 * 그래서 상태 전환을 필드 대입이 아니라 메서드로만 하도록 막아 뒀다.
 *
 * <p>report_id를 채우는 쪽은 피부 보고 확정 흐름(Reports 태그)이라 여기서는 읽기만 한다.
 */
@Entity
@Getter
@Table(name = "daily_check_ins")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyCheckIn extends BaseTimeEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "check_in_date", nullable = false, updatable = false)
    private LocalDate checkInDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private CheckInState state;

    @Column(name = "report_id")
    private UUID reportId;

    private DailyCheckIn(UUID id, UUID userId, LocalDate checkInDate) {
        this.id = id;
        this.userId = userId;
        this.checkInDate = checkInDate;
        this.state = CheckInState.NO_DISCOMFORT;
    }

    /** 사용자가 "오늘 불편 없음"을 저장할 때 쓴다. report_id는 비운다. */
    public static DailyCheckIn noDiscomfort(UUID userId, LocalDate checkInDate) {
        return new DailyCheckIn(UuidV7.generate(), userId, checkInDate);
    }

    /** 같은 날 피부 보고가 확정됐을 때 쓴다. 그날 기록이 아직 없던 경우다. */
    public static DailyCheckIn skinReport(UUID userId, LocalDate checkInDate, UUID reportId) {
        DailyCheckIn checkIn = new DailyCheckIn(UuidV7.generate(), userId, checkInDate);
        checkIn.replaceWithSkinReport(reportId);
        return checkIn;
    }

    /**
     * "오늘 불편 없음"으로 남긴 기록을 피부 보고로 바꾼다.
     *
     * <p>불편이 없다고 답한 뒤 같은 날 보고를 하면 나중에 확정한 보고가 그날의 상태다.
     * 반대 방향은 열지 않는다. 보고를 지우지 않는 한 되돌릴 일이 없다.
     */
    public void replaceWithSkinReport(UUID reportId) {
        if (reportId == null) {
            throw new IllegalArgumentException("피부 보고 상태에는 보고 식별자가 있어야 합니다.");
        }
        this.state = CheckInState.SKIN_REPORT;
        this.reportId = reportId;
    }

    public boolean isNoDiscomfort() {
        return state == CheckInState.NO_DISCOMFORT;
    }
}
