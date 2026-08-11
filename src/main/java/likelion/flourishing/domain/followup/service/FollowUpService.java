package likelion.flourishing.domain.followup.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.followup.dto.request.SaveFollowUpRequest;
import likelion.flourishing.domain.followup.dto.response.FollowUpResponse;
import likelion.flourishing.domain.followup.entity.FollowUp;
import likelion.flourishing.domain.followup.entity.FollowUpKind;
import likelion.flourishing.domain.followup.repository.FollowUpReportRepository;
import likelion.flourishing.domain.followup.repository.FollowUpReportRepository.ReportRow;
import likelion.flourishing.domain.followup.repository.FollowUpRepository;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 다음 날 경과 조회와 저장. */
@Service
public class FollowUpService {

    private final FollowUpRepository followUpRepository;
    private final FollowUpReportRepository followUpReportRepository;
    private final Clock clock;

    public FollowUpService(
            FollowUpRepository followUpRepository,
            FollowUpReportRepository followUpReportRepository,
            Clock clock
    ) {
        this.followUpRepository = followUpRepository;
        this.followUpReportRepository = followUpReportRepository;
        this.clock = clock;
    }

    /**
     * 저장된 경과를 돌려준다.
     *
     * <p>조회 조건에 사용자를 함께 걸어 남의 보고 번호로는 결과가 나오지 않는다.
     * 보고가 없는 경우와 남의 것인 경우를 모두 404로 만들어 존재 여부를 노출하지 않는다.
     */
    @Transactional(readOnly = true)
    public FollowUpResponse getFollowUp(AuthenticatedUser principal, UUID reportId) {
        return followUpRepository.findByReportIdAndUserId(reportId, principal.userId())
                .map(FollowUpResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    /**
     * 경과를 저장하고 보고를 COMPLETED로 바꾼다. 보고당 한 번만 저장할 수 있다.
     *
     * <p>같은 내용을 다시 보내면 저장된 값을 그대로 돌려준다. 네트워크가 끊겨 재요청한 경우를
     * 실패로 만들지 않기 위해서다. 내용이 다르면 덮어쓰기 시도로 보아 409를 낸다.
     *
     * <p>확인 순서는 소유권 → 종류 → 기한 → 기존 경과다. 남의 보고인지부터 걸러야
     * 뒤이은 오류 메시지로 남의 보고 상태가 새어 나가지 않는다.
     */
    @Transactional
    public SavedFollowUp saveFollowUp(
            AuthenticatedUser principal,
            UUID reportId,
            SaveFollowUpRequest request
    ) {
        UUID userId = principal.userId();
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

        // 기한 확인은 기존 경과를 본 다음에 한다. 이미 저장한 뒤 기한이 지나도
        // 같은 내용의 재요청은 계속 200으로 돌려주어야 하기 때문이다.
        LocalDateTime now = LocalDateTime.now(clock);
        if (now.isBefore(report.followUpAvailableAt())) {
            throw new BusinessException(ErrorCode.FOLLOW_UP_NOT_AVAILABLE_YET);
        }
        if (now.isAfter(report.followUpExpiresAt())) {
            throw new BusinessException(ErrorCode.FOLLOW_UP_EXPIRED);
        }

        FollowUp saved = followUpRepository.saveAndFlush(FollowUp.of(reportId, userId, request, now));
        followUpReportRepository.markCompleted(reportId, userId);
        return new SavedFollowUp(FollowUpResponse.from(saved), true);
    }
}
