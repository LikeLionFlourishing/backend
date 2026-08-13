package likelion.flourishing.referencedata.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class OptionResponse {

    private final String value;
    private final String label;

    public static OptionResponse of(String value, String label) {
        return new OptionResponse(value, label);
    }
}
