package likelion.flourishing.domain.notification.service;

import java.util.UUID;
import likelion.flourishing.domain.notification.webpush.WebPushResult;

/** 구독 하나에 대한 발송 결과. 어떤 구독을 비활성으로 내릴지 판단하는 근거가 된다. */
public record SubscriptionOutcome(UUID subscriptionId, WebPushResult result) {
}
