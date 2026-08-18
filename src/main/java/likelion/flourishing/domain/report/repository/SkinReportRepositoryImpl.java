package likelion.flourishing.domain.report.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import java.util.UUID;
import likelion.flourishing.domain.record.cursor.SkinReportCursor;
import likelion.flourishing.domain.record.repository.SkinReportQueryRepository;
import likelion.flourishing.domain.report.entity.QSkinReport;
import likelion.flourishing.domain.report.entity.ReportStatus;
import likelion.flourishing.domain.report.entity.ResultType;
import likelion.flourishing.domain.report.entity.SkinReport;

/** 사용자 소유권과 복합 커서 조건을 한 SQL에 강제하는 QueryDSL 조회 구현. */
public class SkinReportRepositoryImpl implements SkinReportQueryRepository {

    private final JPAQueryFactory queryFactory;

    public SkinReportRepositoryImpl(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    @Override
    public List<SkinReport> findOwnedPage(
            UUID userId,
            ReportStatus status,
            ResultType resultType,
            SkinReportCursor cursor,
            int fetchSize
    ) {
        QSkinReport report = QSkinReport.skinReport;
        BooleanBuilder predicates = new BooleanBuilder(report.userId.eq(userId));

        if (status != null) {
            predicates.and(report.status.eq(status));
        }
        if (resultType != null) {
            predicates.and(report.resultType.eq(resultType));
        }
        if (cursor != null) {
            predicates.and(
                    report.createdAt.lt(cursor.createdAt())
                            .or(report.createdAt.eq(cursor.createdAt()).and(report.id.lt(cursor.id())))
            );
        }

        return queryFactory.selectFrom(report)
                .where(predicates)
                .orderBy(report.createdAt.desc(), report.id.desc())
                .limit(fetchSize)
                .fetch();
    }
}
