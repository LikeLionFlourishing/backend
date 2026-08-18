package likelion.flourishing.domain.notification.service;

import likelion.flourishing.domain.notification.dto.response.PushSubscriptionResponse;

/**
 * 구독 저장 결과. created가 참이면 새로 만든 구독이라 201, 거짓이면 갱신이라 200으로 답한다.
 *
 * <p>컨트롤러가 상태 코드를 정할 수 있게 서비스가 이 사실을 함께 돌려준다.
 */
public record SavedPushSubscription(PushSubscriptionResponse response, boolean created) {
}
