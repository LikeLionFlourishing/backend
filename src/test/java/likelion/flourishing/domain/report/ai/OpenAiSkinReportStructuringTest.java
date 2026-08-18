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
import java.util.List;
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
                  "appearances": ["APP_REDNESS", "APP_BUMP"],
                  "sensations": ["REDNESS"],
                  "situations": ["SHAVING"],
                  "careAvailability": "ALREADY_WASHED"
                }
                """);

        StructuringOutcome outcome = structuring.structure("오른쪽 턱이 빨갛고 따가워요.");

        assertThat(outcome.isSucceeded()).isTrue();
        assertThat(outcome.extracted().primaryArea()).isEqualTo(BodyArea.RIGHT_CHIN);
        assertThat(outcome.extracted().appearances())
                .containsExactlyInAnyOrder(Appearance.APP_REDNESS, Appearance.APP_BUMP);
        assertThat(outcome.extracted().sensations()).containsExactly(Sensation.REDNESS);
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

    /**
     * 단독 선택 값이 다른 값과 함께 오면 뺀다. 명세 v2_1에서 그런 값이 남은 그룹은 직전 상황뿐이다.
     *
     * <p>겉모습과 느껴지는 불편에는 "모름/없음"이 사라져 모순 조합 자체가 생기지 않으므로,
     * 모델이 여러 값을 주면 그대로 받는다.
     */
    @Test
    void exclusiveSituationIsDroppedWhenOtherValuesArePresent() {
        stubPayload("""
                {
                  "primaryArea": "NOSE",
                  "appearances": ["APP_REDNESS", "APP_OTHER"],
                  "sensations": ["REDNESS", "BREAKOUT"],
                  "situations": ["NONE_RECALLED", "SHAVING"],
                  "careAvailability": "ALREADY_WASHED"
                }
                """);

        StructuringOutcome outcome = structuring.structure("코가 빨갛고 트러블이 났어요.");

        assertThat(outcome.extracted().appearances())
                .containsExactlyInAnyOrder(Appearance.APP_REDNESS, Appearance.APP_OTHER);
        assertThat(outcome.extracted().sensations())
                .containsExactlyInAnyOrder(Sensation.REDNESS, Sensation.BREAKOUT);
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

    /**
     * 단일 선택 필드는 근거가 없으면 null이어야 한다.
     *
     * <p>enum이 값 전체를 목록으로 제한하므로 type에 null이 있어도 enum에 없으면 null을 쓸 수 없다.
     * 그러면 문장에 근거가 없어도 모델이 아무 값이나 고르게 되고, 그 값이 사용자 화면의 기본 선택으로
     * 올라간다.
     */
    @Test
    void nullableSingleSelectionSchemaAllowsNull() {
        stubPayload("""
                {"primaryArea": null, "appearances": [], "sensations": [], "situations": [],
                 "careAvailability": null}
                """);
        structuring.structure("확인용 문장");
        ArgumentCaptor<ObjectNode> schema = ArgumentCaptor.forClass(ObjectNode.class);
        verify(client).requestStructuredJson(
                eq("skin_report_structuring"), schema.capture(), anyString(), anyString()
        );

        for (String field : List.of("primaryArea", "careAvailability")) {
            JsonNode node = schema.getValue().path("properties").path(field);
            assertThat(node.path("type")).extracting(JsonNode::asText).contains("string", "null");
            assertThat(node.path("enum")).anyMatch(JsonNode::isNull);
        }
        assertThat(schema.getValue().path("properties").path("primaryArea").path("enum"))
                .hasSize(BodyArea.values().length + 1);
    }

    private void stubPayload(String json) {
        when(client.requestStructuredJson(anyString(), any(), anyString(), anyString()))
                .thenAnswer(call -> OpenAiJsonOutcome.succeeded(objectMapper.readTree(json)));
    }
}
