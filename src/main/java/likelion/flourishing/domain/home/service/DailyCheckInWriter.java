package likelion.flourishing.domain.home.service;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import likelion.flourishing.domain.home.dto.response.DailyCheckInResponse;
import likelion.flourishing.domain.home.entity.DailyCheckIn;
import likelion.flourishing.domain.home.repository.DailyCheckInRepository;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 피부 점호 저장의 트랜잭션 단계만 담당한다.
 *
 * <p>별도 빈으로 둔 이유는 트랜잭션 경계 때문이다. 유니크 제약에 걸린 트랜잭션은 되돌아가야
 * 다시 읽을 수 있는데, 같은 클래스 안에서 자기 메서드를 부르면 프록시를 지나지 않아 새 트랜잭션이
 * 열리지 않는다. {@link HomeService}가 이 빈을 통해 불러야 재시도가 성립한다.
 *
 * <p>전파를 REQUIRES_NEW로 못 박은 이유도 같다. 프록시를 지나는 것과 새 트랜잭션이 열리는 것은
 * 별개이고, 기본값인 REQUIRED는 호출자에 트랜잭션이 없을 때만 새로 연다. 다른 흐름이 자기
 * 트랜잭션 안에서 {@link HomeService}를 부르면 첫 시도의 제약 위반이 그 트랜잭션을 rollback-only로
 * 표시하고, REPEATABLE READ 스냅숏이 그대로라 재조회도 같은 결과를 본다. 재시도가 이 빈의 존재
 * 이유라 전파를 호출자 상태에 맡기지 않는다.
 */
@Component
public class DailyCheckInWriter {

    private final DailyCheckInRepository dailyCheckInRepository;

    public DailyCheckInWriter(DailyCheckInRepository dailyCheckInRepository) {
        this.dailyCheckInRepository = dailyCheckInRepository;
    }

    /**
     * 그날의 "오늘 불편 없음"을 저장한다. 이미 같은 값이 있으면 그대로 돌려준다.
     *
     * <p>날짜와 상태 검증은 호출하는 쪽에서 이미 끝냈다. 여기서는 저장된 값이 있는지만 보고
     * 새로 만들지 그대로 돌려줄지 정한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SavedDailyCheckIn saveNoDiscomfort(UUID userId, LocalDate date) {
        Optional<DailyCheckIn> existing = dailyCheckInRepository.findByUserIdAndCheckInDate(userId, date);
        if (existing.isPresent()) {
            DailyCheckIn checkIn = existing.get();
            // 같은 날 피부 보고가 확정되면 서버가 상태를 SKIN_REPORT로 바꾼다. 되돌릴 수 없다.
            if (!checkIn.isNoDiscomfort()) {
                throw new BusinessException(ErrorCode.CHECK_IN_ALREADY_REPORTED);
            }
            return new SavedDailyCheckIn(DailyCheckInResponse.from(checkIn), false);
        }

        DailyCheckIn saved = dailyCheckInRepository.saveAndFlush(DailyCheckIn.noDiscomfort(userId, date));
        return new SavedDailyCheckIn(DailyCheckInResponse.from(saved), true);
    }
}
