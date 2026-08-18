package likelion.flourishing.domain.record.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import likelion.flourishing.domain.report.dto.response.GuideSectionResponse;
import likelion.flourishing.domain.report.dto.response.RecommendedIngredientResponse;
import java.util.List;
import likelion.flourishing.domain.report.entity.AiGenerationStatus;
import likelion.flourishing.domain.report.entity.MatchReason;
import likelion.flourishing.domain.report.entity.ResultType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@JsonInclude(JsonInclude.Include.ALWAYS)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CareResultResponse {

    private final ResultType resultType;
    private final List<String> matchedRuleIds;

    /** 노출 순서는 배열 순서다. 보고 생성 응답과 같은 값을 내보낸다. */
    private final List<GuideSectionResponse> guideSections;
    private final String ruleVersion;
    private final String summary;
    private final List<String> doToday;
    private final List<String> avoidToday;
    private final List<String> checkNext;

    /** 결과를 만들 때 저장한 스냅샷을 그대로 읽는다. 성분 사전이 바뀌어도 값이 달라지지 않는다. */
    private final List<RecommendedIngredientResponse> recommendedIngredients;
    private final List<MatchReason> reasonTags;
    private final String clinicianMessage;
    private final SimilarExperienceResponse similarExperience;
    private final AiGenerationStatus aiGenerationStatus;
    private final OffsetDateTime generatedAt;
    private final boolean retryUsed;

    public static CareResultResponse of(
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
            SimilarExperienceResponse similarExperience,
            AiGenerationStatus aiGenerationStatus,
            OffsetDateTime generatedAt,
            boolean retryUsed
    ) {
        return new CareResultResponse(
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
