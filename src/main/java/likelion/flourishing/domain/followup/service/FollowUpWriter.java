package likelion.flourishing.domain.followup.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import likelion.flourishing.domain.followup.dto.request.SaveFollowUpRequest;
import likelion.flourishing.domain.followup.dto.response.FollowUpResponse;
import likelion.flourishing.domain.followup.entity.FollowUp;
import likelion.flourishing.domain.followup.entity.FollowUpKind;
import likelion.flourishing.domain.followup.repository.FollowUpReportRepository;
import likelion.flourishing.domain.followup.repository.FollowUpReportRepository.ReportRow;
import likelion.flourishing.domain.followup.repository.FollowUpRepository;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 경과 저장의 트랜잭션 단계만 담당한다.
 *
 * <p>별도 빈으로 둔 이유는 트랜잭션 경계 때문이다. 유니크 제약에 걸린 트랜잭션은 되돌아가야
 * 다시 읽을 수 있는데, 같은 클래스 안에서 자기 메서드를 부르면 프록시를 지나지 않아 새 트랜잭션이
 * 열리지 않는다. {@link FollowUpService}가 이 빈을 통해 불러야 재시도가 성립한다.
 */
@Component
public class FollowUpWriter {

    private final FollowUpRepository followUpRepository;
    private final FollowUpReportRepository followUpReportRepository;
    private final Clock clock;

    public FollowUpWriter(
            FollowUpRepository followUpRepository,
            FollowUpReportRepository followUpReportRepository,
            Clock clock
    ) {
        this.followUpRepository = followUpRepository;
        this.followUpReportRepository = followUpReportRepository;
        this.clock = clock;
    }

    /**
     * 경과를 저장하고 보고를 COMPLETED로 바꾼다. 보고당 한 번만 저장할 수 있다.
     *
     * <p>확인 순서는 소유권 → 종류 → 기존 경과 → 기한이다. 남의 보고인지부터 걸러야 뒤이은
     * 오류 메시지로 남의 보고 상태가 새어 나가지 않는다.
     *
     * <p>기한 확인을 기존 경과보다 뒤에 두는 이유는, 이미 저장한 뒤 기한이 지나도 같은 내용의
     * 재요청은 계속 200으로 돌려주어야 하기 때문이다.
     *
     * <p>입력 구간은 availableAt 이상 expiresAt 미만이다. 만료 시각 자체는 이미 끝난 시점이라
     * 받지 않는다.
     */
    @Transactional
    public SavedFollowUp saveFollowUp(UUID userId, UUID reportId, SaveFollowUpRequest request) {
        ReportRow report = followUpReportRepository.findOwnedReport(reportId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (FollowUpKind.forResultType(report.resultType()) != request.kind()) {
            throw new BusinessException(ErrorCode.FOLLOW_UP_KIND_MISMATCH);
        }

        Optional<FollowUp> existing = followUpRepository.findByReportIdAndUserId(reportId, userId);
        if (existing.isPresent()) {
            FollowUp followUp = existing.get();
            if (!followUp.hasSameContentAs(request)) {
                throw new BusinessException(ErrorCode.FOLLOW_UP_ALREADY_SUBMITTED);
            }
            return new SavedFollowUp(FollowUpResponse.from(followUp), false);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (now.isBefore(report.followUpAvailableAt())) {
            throw new BusinessException(ErrorCode.FOLLOW_UP_NOT_AVAILABLE_YET);
        }
        if (!now.isBefore(report.followUpExpiresAt())) {
            throw new BusinessException(ErrorCode.FOLLOW_UP_EXPIRED);
        }

        FollowUp saved = followUpRepository.saveAndFlush(FollowUp.of(reportId, userId, request, now));
        followUpReportRepository.markCompleted(reportId, userId);
        return new SavedFollowUp(FollowUpResponse.from(saved), true);
    }
}
