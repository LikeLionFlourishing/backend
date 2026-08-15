package likelion.flourishing.domain.report.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import likelion.flourishing.domain.report.entity.Appearance;
import likelion.flourishing.domain.report.entity.BodyArea;
import likelion.flourishing.domain.report.entity.CareAvailability;
import likelion.flourishing.domain.report.entity.Sensation;
import likelion.flourishing.domain.report.entity.Situation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 구조화 응답을 도메인 값으로 옮기는 부분 테스트. 실제 호출 없이 응답 JSON만 갈아 끼운다.
 *
 * <p>확인하는 것: 스키마가 enum에서 만들어지는지, 모르는 코드가 오면 전체를 실패로 돌리는지,
 * 배타 선택이 섞여 오면 배타 값을 빼는지, 호출 실패 코드가 그대로 전달되는지.
 */
@ExtendWith(MockitoExtension.class)
class OpenAiSkinReportStructuringTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private OpenAiResponsesClient client;

    private OpenAiSkinReportStructuring structuring;

    @BeforeEach
    void setUp() {
        structuring = new OpenAiSkinReportStructuring(objectMapper, client);
    }

    @Test
    void mapsPayloadToSelections() {
        stubPayload("""
                {
                  "primaryArea": "RIGHT_CHIN",
                  "appearances": ["REDNESS", "SMALL_BUMPS"],
                  "sensations": ["STINGING_BURNING"],
                  "situations": ["SHAVING"],
                  "careAvailability": "ALREADY_WASHED"
                }
                """);

        StructuringOutcome outcome = structuring.structure("오른쪽 턱이 빨갛고 따가워요.");

        assertThat(outcome.isSucceeded()).isTrue();
        assertThat(outcome.extracted().primaryArea()).isEqualTo(BodyArea.RIGHT_CHIN);
        assertThat(outcome.extracted().appearances())
                .containsExactlyInAnyOrder(Appearance.REDNESS, Appearance.SMALL_BUMPS);
        assertThat(outcome.extracted().sensations()).containsExactly(Sensation.STINGING_BURNING);
        assertThat(outcome.extracted().situations()).containsExactly(Situation.SHAVING);
        assertThat(outcome.extracted().careAvailability()).isEqualTo(CareAvailability.ALREADY_WASHED);
    }

    @Test
    void missingEvidenceLeavesSingleValuesNullAndListsEmpty() {
        stubPayload("""
                {
                  "primaryArea": null,
                  "appearances": [],
                  "sensations": [],
                  "situations": [],
                  "careAvailability": null
                }
                """);

        StructuringOutcome outcome = structuring.structure("피부가 좀 이상해요.");

        assertThat(outcome.isSucceeded()).isTrue();
        assertThat(outcome.extracted().primaryArea()).isNull();
        assertThat(outcome.extracted().careAvailability()).isNull();
        assertThat(outcome.extracted().appearances()).isEmpty();
    }

    @Test
    void unknownCodeIsRejectedByServerSideValidation() {
        stubPayload("""
                {
                  "primaryArea": "RIGHT_CHIN",
                  "appearances": ["ECZEMA"],
                  "sensations": [],
                  "situations": [],
                  "careAvailability": null
                }
                """);

        StructuringOutcome outcome = structuring.structure("턱에 뭐가 났어요.");

        assertThat(outcome.isSucceeded()).isFalse();
        assertThat(outcome.failureCode()).isEqualTo(AiFailureCode.AI_SCHEMA_VIOLATION);
        assertThat(outcome.extracted().appearances()).isEmpty();
    }

    @Test
    void exclusiveValueIsDroppedWhenOtherValuesArePresent() {
        stubPayload("""
                {
                  "primaryArea": "NOSE",
                  "appearances": ["UNSURE", "REDNESS"],
                  "sensations": ["NONE", "ITCHING"],
                  "situations": ["NONE_RECALLED", "SHAVING"],
                  "careAvailability": "ALREADY_WASHED"
                }
                """);

        StructuringOutcome outcome = structuring.structure("코가 빨갛고 간지러운데 확실하진 않아요.");

        assertThat(outcome.extracted().appearances()).containsExactly(Appearance.REDNESS);
        assertThat(outcome.extracted().sensations()).containsExactly(Sensation.ITCHING);
        assertThat(outcome.extracted().situations()).containsExactly(Situation.SHAVING);
    }

    @Test
    void callFailureIsPassedThroughWithEmptySelections() {
        when(client.requestStructuredJson(anyString(), any(), anyString(), anyString()))
                .thenReturn(OpenAiJsonOutcome.failed(AiFailureCode.AI_TIMEOUT));

        StructuringOutcome outcome = structuring.structure("턱이 아파요.");

        assertThat(outcome.isSucceeded()).isFalse();
        assertThat(outcome.failureCode()).isEqualTo(AiFailureCode.AI_TIMEOUT);
        assertThat(outcome.extracted()).isEqualTo(ExtractedSelections.empty());
    }

    /** 스키마를 손으로 적어 두면 선택값이 늘어날 때 어긋난다. enum에서 만들어지는지 확인한다. */
    @Test
    void schemaEnumeratesEverySelectionValue() {
        stubPayload("""
                {"primaryArea": null, "appearances": [], "sensations": [], "situations": [],
                 "careAvailability": null}
                """);

        structuring.structure("확인용 문장");

        ArgumentCaptor<ObjectNode> schema = ArgumentCaptor.forClass(ObjectNode.class);
        verify(client).requestStructuredJson(
                eq("skin_report_structuring"), schema.capture(), anyString(), anyString()
        );
        JsonNode appearanceEnum = schema.getValue()
                .path("properties").path("appearances").path("items").path("enum");
        assertThat(appearanceEnum).hasSize(Appearance.values().length);
        assertThat(schema.getValue().path("additionalProperties").asBoolean(true)).isFalse();
        assertThat(schema.getValue().path("required")).hasSize(5);
    }

    private void stubPayload(String json) {
        when(client.requestStructuredJson(anyString(), any(), anyString(), anyString()))
                .thenAnswer(call -> OpenAiJsonOutcome.succeeded(objectMapper.readTree(json)));
    }
}
