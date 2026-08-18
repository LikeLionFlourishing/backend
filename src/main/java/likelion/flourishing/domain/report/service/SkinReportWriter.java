package likelion.flourishing.domain.report.service;

import java.time.LocalDate;
import java.util.UUID;
import likelion.flourishing.domain.home.entity.DailyCheckIn;
import likelion.flourishing.domain.home.repository.DailyCheckInRepository;
import likelion.flourishing.domain.report.entity.SkinReport;
import likelion.flourishing.domain.report.idempotency.IdempotencyService;
import likelion.flourishing.domain.report.idempotency.IdempotentResponse;
import likelion.flourishing.domain.report.repository.SkinReportRepository;
import likelion.flourishing.domain.report.similarity.ScoredSimilarExperience;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보고 저장의 쓰기 단계만 담당한다.
 *
 * <p>별도 빈으로 둔 이유는 트랜잭션 경계 때문이다. 같은 클래스 안에서 부르면 프록시를 지나지 않아
 * {@code @Transactional}이 걸리지 않는다. 규칙 조회와 AI 호출을 밖에서 끝내고 여기서 저장만 하면
 * 트랜잭션이 외부 응답을 기다리지 않는다.
 *
 * <p>보고, 결과, 적용 규칙, 결과 항목, 피부 점호, 멱등 기록이 한 트랜잭션 안에서 함께 커밋된다.
 * 하나라도 실패하면 전부 되돌아가야 한다. 결과 없는 보고가 남으면 기록 상세 조회가 불변식 위반이
 * 되고, 하루 한 건 제약 때문에 사용자가 그날 다시 보고할 수도 없다.
 */
@Component
public class SkinReportWriter {

    private final SkinReportRepository skinReportRepository;
    private final DailyCheckInRepository dailyCheckInRepository;
    private final CareResultGenerator careResultGenerator;
    private final IdempotencyService idempotencyService;

    public SkinReportWriter(
            SkinReportRepository skinReportRepository,
            DailyCheckInRepository dailyCheckInRepository,
            CareResultGenerator careResultGenerator,
            IdempotencyService idempotencyService
    ) {
        this.skinReportRepository = skinReportRepository;
        this.dailyCheckInRepository = dailyCheckInRepository;
        this.careResultGenerator = careResultGenerator;
        this.idempotencyService = idempotencyService;
    }

    /**
     * 보고와 결과를 저장하고 응답을 만든다.
     *
     * @param responseFactory 저장된 엔티티로 응답 본문을 만드는 함수. 응답 조립까지 트랜잭션 안에서
     *                        끝내야 지연 로딩 필드에 닿을 수 있고, 저장한 값과 나가는 값이 같아진다.
     */
    @Transactional
    public IdempotentResponse write(
            UUID userId,
            String operationId,
            UUID idempotencyKey,
            Object requestFingerprint,
            SkinReport report,
            CareResultPlan plan,
            ScoredSimilarExperience similarExperience,
            SubmissionResponseFactory responseFactory
    ) {
        SkinReport saved = saveReport(report);
        markDailyCheckIn(userId, saved.getReportDate(), saved.getId());
        GeneratedCareResult generated = careResultGenerator.persist(
                saved.getId(), userId, plan, similarExperience
        );

        IdempotentResponse created = IdempotentResponse.created(
                idempotencyService.serialize(responseFactory.create(saved, generated)), saved.getId()
        );
        idempotencyService.store(userId, operationId, idempotencyKey, requestFingerprint, created);
        return created;
    }

    /**
     * 보고를 넣는다.
     *
     * <p>같은 날 보고는 미리 걸러 내지만, 두 요청이 겹치면 검사와 저장 사이에 상대가 먼저 들어올 수
     * 있다. 그때 유니크 제약 위반이 그대로 500으로 나가면 사용자는 무슨 일인지 알 수 없으므로
     * 먼저 저장된 보고가 있다는 뜻으로 바꿔 409로 답한다.
     */
    private SkinReport saveReport(SkinReport report) {
        try {
            return skinReportRepository.saveAndFlush(report);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.REPORT_ALREADY_EXISTS);
        }
    }

    /**
     * 그날의 피부 점호를 보고 상태로 바꾼다.
     *
     * <p>"오늘 불편 없음"을 먼저 저장했더라도 나중에 확정한 보고가 그날의 상태다. 홈 화면이 보고와
     * 점호를 따로 보여 주면 사용자는 같은 날에 두 답을 한 것처럼 보게 된다.
     */
    private void markDailyCheckIn(UUID userId, LocalDate reportDate, UUID reportId) {
        dailyCheckInRepository.findByUserIdAndCheckInDate(userId, reportDate)
                .ifPresentOrElse(
                        checkIn -> checkIn.replaceWithSkinReport(reportId),
                        () -> dailyCheckInRepository.save(
                                DailyCheckIn.skinReport(userId, reportDate, reportId)
                        )
                );
    }

    /** 저장된 보고와 결과로 응답 본문을 만드는 함수. */
    @FunctionalInterface
    public interface SubmissionResponseFactory {

        Object create(SkinReport report, GeneratedCareResult generated);
    }
}
