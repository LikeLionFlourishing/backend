package likelion.flourishing.domain.report.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;
import likelion.flourishing.domain.report.entity.AiGenerationStatus;
import likelion.flourishing.domain.report.entity.MatchReason;
import likelion.flourishing.domain.report.entity.ResultType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 방금 만든 관리 결과.
 *
 * <p>기록 조회의 careResult와 필드가 같다. 보고를 만든 직후와 나중에 다시 볼 때 화면이 달라지지
 * 않아야 하기 때문이다.
 *
 * <p>명세 v2_1 에서 guideSections 와 recommendedIngredients 가 필수로 들어왔다. 둘 다
 * CLINICIAN_CHECK 결과에는 maxItems 0 이라 빈 배열로 나간다.
 *
 * @param retryUsed 관리 설명 재생성을 이미 썼는지. 클라이언트는 이 값으로 재생성 버튼을 감춘다.
 */
@Getter
@JsonInclude(JsonInclude.Include.ALWAYS)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CareGuideResponse {

    private final ResultType resultType;
    private final List<String> matchedRuleIds;

    /** 노출 순서는 배열 순서다. 프론트가 제목·설명을 하드코딩하지 않고 이 값을 그대로 그린다. */
    private final List<GuideSectionResponse> guideSections;
    private final String ruleVersion;
    private final String summary;
    private final List<String> doToday;
    private final List<String> avoidToday;
    private final List<String> checkNext;

    /** 관리 규칙표에서 조회한 성분만 담긴다. AI가 새로 만들지 않는다. */
    private final List<RecommendedIngredientResponse> recommendedIngredients;
    private final List<MatchReason> reasonTags;
    private final String clinicianMessage;
    private final SimilarExperienceSummaryResponse similarExperience;
    private final AiGenerationStatus aiGenerationStatus;
    private final OffsetDateTime generatedAt;
    private final boolean retryUsed;

    public static CareGuideResponse of(
            ResultType resultType,
            List<String> matchedRuleIds,
            List<GuideSectionResponse> guideSections,
            String ruleVersion,
            String summary,
            List<String> doToday,
            List<String> avoidToday,
            List<String> checkNext,
            List<RecommendedIngredientResponse> recommendedIngredients,
            List<MatchReason> reasonTags,
            String clinicianMessage,
            SimilarExperienceSummaryResponse similarExperience,
            AiGenerationStatus aiGenerationStatus,
            OffsetDateTime generatedAt,
            boolean retryUsed
    ) {
        return new CareGuideResponse(
                resultType,
                matchedRuleIds,
                guideSections,
                ruleVersion,
                summary,
                doToday,
                avoidToday,
                checkNext,
                recommendedIngredients,
                reasonTags,
                clinicianMessage,
                similarExperience,
                aiGenerationStatus,
                generatedAt,
                retryUsed
        );
    }
}
