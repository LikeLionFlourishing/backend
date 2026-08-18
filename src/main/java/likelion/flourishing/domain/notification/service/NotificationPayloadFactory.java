package likelion.flourishing.domain.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import likelion.flourishing.domain.notification.entity.NotificationType;
import org.springframework.stereotype.Component;

/**
 * 알림 payload를 만든다.
 *
 * <p>payload에는 피부 상세정보를 넣지 않는다. Push 서비스는 우리 암호문을 그대로 전달하지만
 * 알림은 잠금 화면에 그대로 보이고, 사용자가 기기를 잠시 남에게 보여 줄 수도 있다.
 * 그래서 어떤 부위가 어떻게 됐는지는 담지 않고 "무엇을 할 차례인지"만 알린다.
 *
 * <p>보고 번호는 화면으로 이동하기 위한 식별자라서 넣는다. 그 자체로는 피부 상태를 알려 주지 않고,
 * 상세 내용은 로그인한 사용자만 API로 볼 수 있다.
 */
@Component
public class NotificationPayloadFactory {

    private static final String FOLLOW_UP_TITLE = "어제 피부는 어땠나요?";
    private static final String FOLLOW_UP_BODY = "경과를 남기면 다음 관리에 반영해요.";
    private static final String DAILY_CHECK_IN_TITLE = "오늘 피부는 어땠나요?";
    private static final String DAILY_CHECK_IN_BODY = "오늘의 피부 점호를 남겨 주세요.";
    private static final String CHECK_IN_PATH = "/check-in";

    private final ObjectMapper objectMapper;

    public NotificationPayloadFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public byte[] create(NotificationType notificationType, UUID targetReportId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", notificationType.name());
        switch (notificationType) {
            case FOLLOW_UP -> {
                payload.put("title", FOLLOW_UP_TITLE);
                payload.put("body", FOLLOW_UP_BODY);
                payload.put("url", "/skin-reports/" + targetReportId + "/follow-up");
            }
            case DAILY_CHECK_IN -> {
                payload.put("title", DAILY_CHECK_IN_TITLE);
                payload.put("body", DAILY_CHECK_IN_BODY);
                payload.put("url", CHECK_IN_PATH);
            }
        }

        try {
            return objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("알림 payload를 만들지 못했습니다.", exception);
        }
    }
}
