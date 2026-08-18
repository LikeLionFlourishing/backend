package likelion.flourishing.domain.auth.service;

import likelion.flourishing.domain.auth.dto.response.AuthSessionResponse;

/**
 * 회원가입·로그인 결과. 컨트롤러가 본문과 세션 쿠키를 각각 만들 수 있게 나눠서 돌려준다.
 *
 * @param session      응답 본문에 담을 AuthSession
 * @param sessionToken Set-Cookie에 담을 원본 세션 토큰
 */
public record AuthSessionIssue(AuthSessionResponse session, String sessionToken) {
}
