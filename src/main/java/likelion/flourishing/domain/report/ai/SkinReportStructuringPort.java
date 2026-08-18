package likelion.flourishing.domain.report.ai;

/**
 * 사용자가 쓴 한 문장을 선택값으로 옮기는 구조화 담당.
 *
 * <p>인터페이스로 끊어 둔 이유는 두 가지다. 하나는 AI 제공자를 바꿔도 서비스가 그대로 남게
 * 하려는 것이고, 하나는 테스트에서 실제 호출 없이 성공과 실패를 모두 재현하기 위해서다.
 */
public interface SkinReportStructuringPort {

    /**
     * 원문에서 선택값을 뽑는다. 실패해도 예외를 던지지 않고 실패 결과를 돌린다.
     *
     * @param rawText 사용자가 쓴 한 문장. 구현체는 이 값을 로그에 남기지 않는다.
     */
    StructuringOutcome structure(String rawText);
}
