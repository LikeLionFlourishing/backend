package likelion.flourishing.domain.report.similarity;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import likelion.flourishing.domain.followup.entity.FollowUp;
import likelion.flourishing.domain.followup.repository.FollowUpRepository;
import likelion.flourishing.domain.record.dto.response.SimilarExperienceResponse;
import likelion.flourishing.domain.report.entity.CareResult;
import likelion.flourishing.domain.report.entity.ReportStatus;
import likelion.flourishing.domain.report.entity.SkinReport;
import likelion.flourishing.domain.report.repository.CareResultRepository;
import likelion.flourishing.domain.report.repository.SkinReportRepository;
import likelion.flourishing.domain.report.rule.RuleEvaluationFacts;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지금 보고와 견줄 만한 과거 완료 기록을 찾는다.
 *
 * <p>후보 조건은 같은 사용자, 과거 날짜, COMPLETED 상태다. 진행 중인 보고를 견주면 "그때 어떻게
 * 됐는지"를 알려 줄 수 없다.
 *
 * <p>점수가 높아도 그때의 결과나 경과가 없으면 건너뛴다. 응답에 요약과 변화를 담아야 하고, 짝이
 * 없는 ID를 결과에 저장하면 나중에 기록 상세 조회가 불변식 위반으로 막힌다.
 */
@Component
public class SimilarExperienceFinder {

    private final SkinReportRepository skinReportRepository;
    private final CareResultRepository careResultRepository;
    private final FollowUpRepository followUpRepository;
    private final SimilarExperienceScorer scorer;

    public SimilarExperienceFinder(
            SkinReportRepository skinReportRepository,
            CareResultRepository careResultRepository,
            FollowUpRepository followUpRepository,
            SimilarExperienceScorer scorer
    ) {
        this.skinReportRepository = skinReportRepository;
        this.careResultRepository = careResultRepository;
        this.followUpRepository = followUpRepository;
        this.scorer = scorer;
    }

    /** 과거 완료 기록을 한 번 읽어 유사 경험과 규칙 조건용 코드를 함께 만든다. */
    @Transactional(readOnly = true)
    public SimilarExperienceLookup lookup(UUID userId, LocalDate reportDate, SimilarExperienceQuery query) {
        List<SkinReport> candidates = skinReportRepository
                .findTop30ByUserIdAndStatusAndReportDateLessThanOrderByReportDateDesc(
                        userId, ReportStatus.COMPLETED, reportDate
                );
        if (candidates.isEmpty()) {
            return SimilarExperienceLookup.empty();
        }

        Optional<FoundSimilarExperience> found = Optional.empty();
        for (ScoredSimilarExperience scored : scorer.rank(query, candidates)) {
            found = toFound(userId, scored, candidates);
            if (found.isPresent()) {
                break;
            }
        }
        return new SimilarExperienceLookup(found, historyCodes(candidates, found.isPresent()));
    }

    private Optional<FoundSimilarExperience> toFound(
            UUID userId,
            ScoredSimilarExperience scored,
            List<SkinReport> candidates
    ) {
        SkinReport report = candidates.stream()
                .filter(candidate -> candidate.getId().equals(scored.reportId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("점수를 매긴 후보를 다시 찾지 못했습니다."));

        Optional<CareResult> careResult = careResultRepository.findByReportIdAndUserId(report.getId(), userId);
        Optional<FollowUp> followUp = followUpRepository.findByReportIdAndUserId(report.getId(), userId);
        if (careResult.isEmpty() || followUp.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new FoundSimilarExperience(
                scored,
                SimilarExperienceResponse.of(
                        report.getId(),
                        report.getReportDate(),
                        scored.score(),
                        careResult.get().getSummary(),
                        followUp.get().getSkinChange()
                )
        ));
    }

    /**
     * 이미 결과에 저장된 유사 경험을 응답 모양으로 다시 만든다.
     *
     * <p>관리 설명을 다시 만들 때 쓴다. 유사 경험은 다시 고르지 않는다. 같은 보고를 두고 근거가
     * 바뀌면 사용자가 보는 기준이 흔들린다.
     *
     * <p>참조한 보고가 지워졌으면 빈 값이다. DDL이 similar_report_id를 NULL로 바꾸는 경우다.
     */
    @Transactional(readOnly = true)
    public Optional<SimilarExperienceResponse> describe(UUID userId, UUID similarReportId, int score) {
        Optional<SkinReport> report = skinReportRepository.findByIdAndUserId(similarReportId, userId);
        Optional<CareResult> careResult = careResultRepository.findByReportIdAndUserId(similarReportId, userId);
        Optional<FollowUp> followUp = followUpRepository.findByReportIdAndUserId(similarReportId, userId);
        if (report.isEmpty() || careResult.isEmpty() || followUp.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(SimilarExperienceResponse.of(
                similarReportId,
                report.get().getReportDate(),
                score,
                careResult.get().getSummary(),
                followUp.get().getSkinChange()
        ));
    }

    /**
     * 규칙 조건이 보는 과거 기록 코드.
     *
     * <p>지금은 완료된 과거 보고의 겉모습 코드와 유사 경험을 찾았는지 여부를 넣는다. 어떤 코드를
     * 조건으로 쓸지는 관리 규칙 최종본에서 확정되므로 그때 이 목록을 함께 맞춘다.
     */
    private Set<String> historyCodes(List<SkinReport> candidates, boolean similarExperienceFound) {
        Set<String> codes = candidates.stream()
                .flatMap(report -> report.getAppearances().stream())
                .map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (similarExperienceFound) {
            codes.add(RuleEvaluationFacts.SIMILAR_EXPERIENCE_FOUND);
        }
        return codes;
    }
}
