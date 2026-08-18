package likelion.flourishing.domain.record.cursor;

import java.time.LocalDateTime;
import java.util.UUID;

public record SkinReportCursor(LocalDateTime createdAt, UUID id) {
}
