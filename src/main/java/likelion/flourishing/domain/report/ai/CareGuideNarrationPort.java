package likelion.flourishing.domain.report.ai;

/**
 * 규칙이 허용한 문구 안에서 관리 설명을 쓰는 담당.
 *
 * <p>규칙 데이터가 결정하는 것은 "무엇을 안내할 수 있는지"이고, 이 Port가 하는 일은 그중에서
 * 고르고 읽기 좋은 요약을 붙이는 것뿐이다. 그래서 규칙 최종본이 오기 전에도 계약을 고정할 수 있다.
 */
public interface CareGuideNarrationPort {

    /** 설명을 만든다. 실패해도 예외를 던지지 않고 실패 결과를 돌린다. */
    NarrationOutcome narrate(NarrationCommand command);
}
