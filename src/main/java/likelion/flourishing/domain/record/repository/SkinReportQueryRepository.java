package likelion.flourishing.domain.record.repository;

import java.util.List;
import java.util.UUID;
import likelion.flourishing.domain.record.cursor.SkinReportCursor;
import likelion.flourishing.domain.report.entity.ReportStatus;
import likelion.flourishing.domain.report.entity.ResultType;
import likelion.flourishing.domain.report.entity.SkinReport;

public interface SkinReportQueryRepository {

    List<SkinReport> findOwnedPage(
            UUID userId,
            ReportStatus status,
            ResultType resultType,
            SkinReportCursor cursor,
            int fetchSize
    );
}
