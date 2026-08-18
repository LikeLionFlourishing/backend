package likelion.flourishing.domain.record.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
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
    private final String ruleVersion;
    private final String summary;
    private final List<String> doToday;
    private final List<String> avoidToday;
    private final List<String> checkNext;
    private final List<MatchReason> reasonTags;
    private final String clinicianMessage;
    private final SimilarExperienceResponse similarExperience;
    private final AiGenerationStatus aiGenerationStatus;
    private final OffsetDateTime generatedAt;
    private final boolean retryUsed;

    public static CareResultResponse of(
            ResultType resultType,
            List<String> matchedRuleIds,
            String ruleVersion,
            String summary,
            List<String> doToday,
            List<String> avoidToday,
            List<String> checkNext,
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
                ruleVersion,
                summary,
                doToday,
                avoidToday,
                checkNext,
                reasonTags,
                clinicianMessage,
                similarExperience,
                aiGenerationStatus,
                generatedAt,
                retryUsed
        );
    }
}
