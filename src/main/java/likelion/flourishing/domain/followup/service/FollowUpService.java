package likelion.flourishing.domain.followup.service;

import java.util.UUID;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.followup.dto.request.SaveFollowUpRequest;
import likelion.flourishing.domain.followup.dto.response.FollowUpResponse;
import likelion.flourishing.domain.followup.repository.FollowUpRepository;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 다음 날 경과 조회와 저장. */
@Service
public class FollowUpService {

    private final FollowUpRepository followUpRepository;
    private final FollowUpWriter followUpWriter;

    public FollowUpService(FollowUpRepository followUpRepository, FollowUpWriter followUpWriter) {
        this.followUpRepository = followUpRepository;
        this.followUpWriter = followUpWriter;
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
     * 경과를 저장한다. 같은 내용을 다시 보내면 저장된 값을 그대로 돌려주고, 내용이 다르면 409를 낸다.
     *
     * <p>트랜잭션은 저장 단계에만 건다. 같은 보고에 첫 저장이 겹치면 둘 다 저장된 경과가 없다고
     * 보고 각자 넣으려다 뒤늦은 쪽이 uq_follow_ups_report에 걸리는데, 그 트랜잭션이 되돌아간 뒤
     * 다시 읽어야 먼저 저장된 경과가 보인다.
     */
    public SavedFollowUp saveFollowUp(
            AuthenticatedUser principal,
            UUID reportId,
            SaveFollowUpRequest request
    ) {
        UUID userId = principal.userId();
        try {
            return followUpWriter.saveFollowUp(userId, reportId, request);
        } catch (DataIntegrityViolationException raced) {
            // 재시도 때는 먼저 저장된 경과가 보인다. 같은 내용이면 200, 다른 내용이면 409가 되어
            // 겹치지 않았을 때와 같은 결과가 나간다. 재시도는 한 번뿐이고, 두 번째도 제약에
            // 걸리면 원인이 경합이 아니라는 뜻이라 그대로 올린다.
            return followUpWriter.saveFollowUp(userId, reportId, request);
        }
    }
}
