package likelion.flourishing.domain.report.service;

import java.util.List;
import likelion.flourishing.domain.report.entity.GuideSectionCopy;
import likelion.flourishing.domain.report.entity.GuideSectionKey;
import likelion.flourishing.domain.report.repository.GuideSectionCopyRepository;
import org.mockito.Mockito;

/**
 * 가이드 섹션 문구를 DB 없이 공급하는 테스트 도우미.
 *
 * <p>단위 테스트가 확인하는 것은 섹션이 어떤 순서로 나오고 언제 비었다고 표시되는지이지,
 * 문구가 무엇인지가 아니다. 문구는 db/seed 의 기본값과 같은 여섯 줄을 쓴다.
 */
public final class GuideSectionFixtures {

    private GuideSectionFixtures() {
    }

    public static List<GuideSectionCopy> defaultCopies() {
        return List.of(
                GuideSectionCopy.of(GuideSectionKey.CURRENT_SUMMARY, "지금 상태", "오늘 남긴 기록 요약입니다.", 1),
                GuideSectionCopy.of(GuideSectionKey.DO_TODAY, "오늘 할 일", "오늘 시도해볼 행동입니다.", 2),
                GuideSectionCopy.of(GuideSectionKey.AVOID_TODAY, "오늘 피할 일", "오늘 피할 행동입니다.", 3),
                GuideSectionCopy.of(GuideSectionKey.SIMILAR_EXPERIENCE, "이전 비슷한 경험", "비슷했던 지난 기록입니다.", 4),
                GuideSectionCopy.of(GuideSectionKey.CHECK_NEXT, "다음에 확인할 변화", "내일 볼 지점입니다.", 5),
                GuideSectionCopy.of(GuideSectionKey.RECOMMENDED_INGREDIENTS, "추천 성분 보기", "규칙에서 고른 성분입니다.", 6)
        );
    }

    /** 기본 문구 여섯 줄을 돌려주는 조립기. */
    public static GuideSectionAssembler assembler() {
        GuideSectionCopyRepository repository = Mockito.mock(GuideSectionCopyRepository.class);
        Mockito.when(repository.findAllByOrderByDisplayOrderAsc()).thenReturn(defaultCopies());
        return new GuideSectionAssembler(repository);
    }
}
