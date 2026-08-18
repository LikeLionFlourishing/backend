package likelion.flourishing.domain.home.service;

import likelion.flourishing.domain.home.dto.response.DailyCheckInResponse;

/**
 * 저장 결과와 그것이 새로 만들어진 것인지 여부.
 *
 * <p>명세가 새로 저장하면 201, 같은 값이 이미 있으면 200으로 나누라고 해서 상태 코드를
 * 정하려면 이 구분이 필요하다. 서비스가 HTTP 상태를 직접 다루지 않도록 boolean으로만 알린다.
 *
 * @param response 저장돼 있는 하루 상태
 * @param created  이번 요청으로 새로 만들어졌으면 true
 */
public record SavedDailyCheckIn(DailyCheckInResponse response, boolean created) {
}
