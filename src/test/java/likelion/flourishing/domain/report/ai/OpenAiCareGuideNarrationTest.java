package likelion.flourishing.domain.report.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import likelion.flourishing.domain.report.entity.Appearance;
import likelion.flourishing.domain.report.entity.BodyArea;
import likelion.flourishing.domain.report.entity.CareAvailability;
import likelion.flourishing.domain.report.entity.Sensation;
import likelion.flourishing.domain.report.entity.Situation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 관리 설명 생성의 허용 목록 검증 테스트.
 *
 * <p>확인하는 것: 규칙이 허용한 문구만 통과하는지, 개수를 넘기면 실패로 돌리는지, 금지 표현이
 * 섞인 요약을 막는지, 고를 문구가 없으면 호출조차 하지 않는지.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OpenAiCareGuideNarrationTest {

    private static final List<String> ALLOWED_DO = List.of("미지근한 물로 씻기", "찬 물수건으로 진정하기");
    private static final List<String> ALLOWED_AVOID = List.of("손으로 만지지 않기");
    private static final List<String> ALLOWED_CHECK = List.of("붉은 범위가 넓어졌는지 보기");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private OpenAiResponsesClient client;

    private OpenAiCareGuideNarration narration;

    @BeforeEach
    void setUp() {
        narration = new OpenAiCareGuideNarration(objectMapper, client);
    }

    @Test
    void selectionInsideTheAllowListIsAccepted() {
        stubPayload("""
                {
                  "summary": "오늘은 자극을 줄이고 상태를 지켜봐 주세요.",
                  "doToday": ["미지근한 물로 씻기"],
                  "avoidToday": ["손으로 만지지 않기"],
                  "checkNext": ["붉은 범위가 넓어졌는지 보기"]
                }
                """);

        NarrationOutcome outcome = narration.narrate(command(List.of()));

        assertThat(outcome.isSucceeded()).isTrue();
        assertThat(outcome.summary()).isEqualTo("오늘은 자극을 줄이고 상태를 지켜봐 주세요.");
        assertThat(outcome.doToday()).containsExactly("미지근한 물로 씻기");
    }

    @Test
    void contentOutsideTheAllowListFailsTheWholeResult() {
        stubPayload("""
                {
                  "summary": "연고를 바르세요.",
                  "doToday": ["스테로이드 연고 바르기"],
                  "avoidToday": [],
                  "checkNext": []
                }
                """);

        NarrationOutcome outcome = narration.narrate(command(List.of()));

        assertThat(outcome.isSucceeded()).isFalse();
        assertThat(outcome.failureCode()).isEqualTo(AiFailureCode.AI_SCHEMA_VIOLATION);
        assertThat(outcome.doToday()).isEmpty();
    }

    @Test
    void tooManyItemsFailTheResult() {
        stubPayload("""
                {
                  "summary": "세 개를 골랐습니다.",
                  "doToday": ["미지근한 물로 씻기", "찬 물수건으로 진정하기"],
                  "avoidToday": ["손으로 만지지 않기"],
                  "checkNext": ["붉은 범위가 넓어졌는지 보기"]
                }
                """);

        NarrationOutcome withinLimit = narration.narrate(command(List.of()));
        assertThat(withinLimit.isSucceeded()).isTrue();

        NarrationOutcome overLimit = narration.narrate(commandWithMaxItems(1));
        assertThat(overLimit.isSucceeded()).isFalse();
        assertThat(overLimit.failureCode()).isEqualTo(AiFailureCode.AI_SCHEMA_VIOLATION);
    }

    @Test
    void forbiddenExpressionInSummaryFailsTheResult() {
        stubPayload("""
                {
                  "summary": "이렇게 하면 완치됩니다.",
                  "doToday": ["미지근한 물로 씻기"],
                  "avoidToday": [],
                  "checkNext": []
                }
                """);

        NarrationOutcome outcome = narration.narrate(command(List.of("완치")));

        assertThat(outcome.isSucceeded()).isFalse();
        assertThat(outcome.failureCode()).isEqualTo(AiFailureCode.AI_SCHEMA_VIOLATION);
    }

    @Test
    void blankSummaryFailsTheResult() {
        stubPayload("""
                {"summary": "   ", "doToday": ["미지근한 물로 씻기"], "avoidToday": [], "checkNext": []}
                """);

        assertThat(narration.narrate(command(List.of())).isSucceeded()).isFalse();
    }

    @Test
    void emptySelectionFailsTheResult() {
        stubPayload("""
                {"summary": "고른 항목이 없습니다.", "doToday": [], "avoidToday": [], "checkNext": []}
                """);

        assertThat(narration.narrate(command(List.of())).isSucceeded()).isFalse();
    }

    @Test
    void noAllowedActionSkipsTheCallEntirely() {
        NarrationOutcome outcome = narration.narrate(new NarrationCommand(
                BodyArea.RIGHT_CHIN,
                Set.of(Appearance.REDNESS),
                Set.of(Sensation.NONE),
                Set.of(Situation.SHAVING),
                CareAvailability.ALREADY_WASHED,
                List.of("규칙 요약"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                2
        ));

        assertThat(outcome.isSucceeded()).isFalse();
        verify(client, never()).requestStructuredJson(anyString(), any(), anyString(), anyString());
    }

    private NarrationCommand command(List<String> forbiddenExpressions) {
        return commandOf(forbiddenExpressions, 2);
    }

    private NarrationCommand commandWithMaxItems(int maxItemsPerType) {
        return commandOf(List.of(), maxItemsPerType);
    }

    private NarrationCommand commandOf(List<String> forbiddenExpressions, int maxItemsPerType) {
        return new NarrationCommand(
                BodyArea.RIGHT_CHIN,
                Set.of(Appearance.REDNESS),
                Set.of(Sensation.STINGING_BURNING),
                Set.of(Situation.SHAVING),
                CareAvailability.ALREADY_WASHED,
                List.of("붉음이 있을 때의 관리"),
                ALLOWED_DO,
                ALLOWED_AVOID,
                ALLOWED_CHECK,
                forbiddenExpressions,
                maxItemsPerType
        );
    }

    private void stubPayload(String json) {
        when(client.requestStructuredJson(anyString(), any(), anyString(), anyString()))
                .thenAnswer(call -> OpenAiJsonOutcome.succeeded(objectMapper.readTree(json)));
    }
}
