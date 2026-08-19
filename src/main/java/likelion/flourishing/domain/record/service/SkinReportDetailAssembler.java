package likelion.flourishing.domain.record.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import likelion.flourishing.domain.followup.dto.response.FollowUpResponse;
import likelion.flourishing.domain.followup.entity.SkinChange;
import likelion.flourishing.domain.record.dto.response.CareResultResponse;
import likelion.flourishing.domain.record.dto.response.ConfirmedStructuredReportResponse;
import likelion.flourishing.domain.record.dto.response.SkinReportDetailResponse;
import likelion.flourishing.domain.report.entity.Appearance;
import likelion.flourishing.domain.report.entity.Sensation;
import likelion.flourishing.domain.report.entity.Situation;
import likelion.flourishing.domain.report.entity.SkinReport;

/**
 * 피부 보고 상세 응답을 만드는 한 곳.
 *
 * <p>생성(POST /v1/skin-reports)과 상세 조회(GET /v1/skin-reports/{id})가 같은 타입을 돌려주는데,
 * 조립을 각자 하면 한쪽에만 필드가 붙어 시간이 지나며 응답이 갈라진다. 실제로 그렇게 갈라져
 * 생성 응답에는 최상위 선택값과 followUp 이 없고 명세에 없는 두 시각이 붙어 있었다.
 *
 * <p>원문과 부위 보충 설명은 저장 경로마다 출처가 달라(요청 본문 / 복호화한 값) 인자로 받는다.
 * 나머지는 전부 엔티티에서 꺼낸다.
 */
public final class SkinReportDetailAssembler {

    private SkinReportDetailAssembler() {
    }

    public static SkinReportDetailResponse assemble(
            SkinReport report,
            String rawText,
            String otherAreasNote,
            CareResultResponse careResult,
            FollowUpResponse followUp,
            SkinChange skinChange,
            OffsetDateTime createdAt
    ) {
        List<Appearance> appearances = sorted(report.getAppearances());
        List<Sensation> sensations = sorted(report.getSensations());
        List<Situation> situations = sorted(report.getSituations());

        return SkinReportDetailResponse.of(
                report.getId(),
                report.getReportDate(),
                report.getPrimaryArea(),
                appearances,
                sensations,
                situations,
                report.getResultType(),
                report.getStatus(),
                skinChange,
                rawText,
                ConfirmedStructuredReportResponse.of(
                        report.getPrimaryArea(),
                        otherAreasNote,
                        appearances,
                        sensations,
                        situations,
                        report.getCareAvailability()
                ),
                sorted(report.getPreCareChecks()),
                careResult,
                followUp,
                createdAt
        );
    }

    public static OffsetDateTime toOffset(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    /** 응답 순서를 enum 선언 순으로 고정한다. 같은 기록에 항상 같은 순서가 나가야 한다. */
    private static <E extends Enum<E>> List<E> sorted(Set<E> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().sorted(Comparator.comparingInt(Enum::ordinal)).toList();
    }
}
