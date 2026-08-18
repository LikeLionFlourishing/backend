package likelion.flourishing.domain.record.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@JsonInclude(JsonInclude.Include.ALWAYS)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CursorPageResponse {

    private final String nextCursor;
    private final boolean hasMore;
    private final int limit;

    public static CursorPageResponse of(String nextCursor, boolean hasMore, int limit) {
        return new CursorPageResponse(nextCursor, hasMore, limit);
    }
}
