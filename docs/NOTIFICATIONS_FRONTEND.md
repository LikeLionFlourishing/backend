# 알림·Push 구독 프론트엔드 연동 안내

이슈 #16으로 구현한 엔드포인트 4개와 17:30 발송 작업의 연동 방법입니다.

## 공통 규칙

- 모든 요청에 세션 쿠키가 필요합니다. `fetch`에는 `credentials: 'include'`를 넣습니다.
- `GET`을 제외한 요청(`PATCH`, `POST`, `DELETE`)에는 `X-CSRF-Token` 헤더가 필요합니다.
  값은 로그인 응답에서 받은 CSRF 토큰입니다. 없거나 틀리면 403 `CSRF_TOKEN_INVALID`입니다.
- 오류 응답은 `application/problem+json`이고 형식은 아래와 같습니다.

```json
{
  "type": "https://api.example.invalid/problems/validation-error",
  "title": "입력값 오류",
  "status": 422,
  "detail": "입력값을 확인해 주세요.",
  "instance": "/v1/push-subscriptions",
  "code": "VALIDATION_ERROR",
  "requestId": "req_7QK2M8XZP1",
  "errors": [
    { "field": "endpoint", "code": "NotBlank", "message": "must not be blank" }
  ]
}
```

- 분기는 `code`로 합니다. `errors`는 Bean Validation 실패일 때만 있습니다.
- 문의할 때는 `requestId`를 함께 알려 주시면 서버 로그에서 같은 값으로 찾을 수 있습니다.

## 1. 알림 설정 조회

```
GET /v1/me/notification-settings
```

응답 200:

```json
{
  "enabled": true,
  "notificationTime": "17:30",
  "timezone": "Asia/Seoul",
  "permissionState": "GRANTED",
  "activeSubscriptionCount": 1,
  "updatedAt": "2026-08-15T08:30:00Z"
}
```

- `notificationTime`과 `timezone`은 P0 고정값입니다. 서버가 항상 이 두 값을 보냅니다.
- `permissionState`: `DEFAULT` | `GRANTED` | `DENIED` | `UNSUPPORTED`
- `activeSubscriptionCount`가 0이면 알림을 켜 두었어도 받을 기기가 없는 상태입니다.
  이 경우 재구독을 안내하는 화면이 필요합니다.
- 온보딩을 건너뛴 사용자는 `enabled: false`, `permissionState: "DEFAULT"`, `updatedAt: null`이 옵니다.
- 응답에 `Cache-Control: no-store`가 붙습니다.

## 2. 알림 설정 변경

```
PATCH /v1/me/notification-settings
X-CSRF-Token: <토큰>
Content-Type: application/json
```

요청:

```json
{ "enabled": true, "permissionState": "GRANTED" }
```

- `enabled`는 필수입니다. 빠지면 422 `VALIDATION_ERROR`입니다.
- `permissionState`는 선택입니다. 보내지 않으면 서버에 저장된 값을 그대로 둡니다.
  브라우저 권한 상태가 바뀐 시점에만 함께 보내면 됩니다.
- 시각과 시간대는 바꿀 수 없습니다. `notificationTime` 같은 필드를 넣으면 400 `BAD_REQUEST`입니다.
  정의되지 않은 필드는 모두 거부합니다.
- `enabled: true`와 `permissionState: "DENIED"` 조합도 그대로 저장됩니다. 알림을 켜겠다는 의사와
  브라우저 권한은 별개로 다룹니다.
- 응답 본문은 조회와 같은 형식입니다.

## 3. Push 구독 등록

```
POST /v1/push-subscriptions
X-CSRF-Token: <토큰>
Content-Type: application/json
```

요청은 브라우저 `PushSubscription.toJSON()` 결과를 그대로 보내면 됩니다.

```json
{
  "endpoint": "https://push.example.net/push/JzLQ3raZJfFBR0aqvOMsLrt54w4rJUsV",
  "expirationTime": null,
  "keys": {
    "p256dh": "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4",
    "auth": "BTBZMqHH6r4Tts7J_aSIgg"
  }
}
```

응답 201(신규) 또는 200(같은 endpoint 재등록):

```json
{
  "subscriptionId": "0198a31f-f33f-7000-8000-0000000000a1",
  "endpointFingerprint": "2b7f0a6c…(64자 hex)",
  "active": true,
  "expiresAt": null,
  "createdAt": "2026-08-15T08:30:00Z",
  "updatedAt": "2026-08-15T08:30:00Z"
}
```

- 같은 기기에서 여러 번 보내도 행이 늘지 않습니다. 신규는 201, 갱신은 200이니 두 코드를 모두
  성공으로 처리하시면 됩니다.
- 응답에 endpoint 원문과 키는 없습니다. 되짚을 수 없는 지문만 내려갑니다.
  구독 해제에는 `subscriptionId`를 쓰십시오.
- `expirationTime`은 브라우저가 준 값이 있을 때만 넣습니다. 대부분 `null`입니다.
- `User-Agent` 헤더는 서버가 기기 구분용으로 저장합니다. 별도로 넣을 것은 없습니다.

## 4. Push 구독 해제

```
DELETE /v1/push-subscriptions/{subscriptionId}
X-CSRF-Token: <토큰>
```

- 성공은 204이고 본문이 없습니다.
- 다른 사용자의 구독이거나 없는 번호면 404 `RESOURCE_NOT_FOUND`입니다. 두 경우를 구분하지 않습니다.
- 브라우저에서 `subscription.unsubscribe()`도 함께 호출해 주십시오. 서버 행만 지우면 브라우저
  구독이 남습니다.

## 오류 코드

| 상태 | code | 언제 |
|---|---|---|
| 400 | `BAD_REQUEST` | 정의되지 않은 필드, 잘못된 enum 값, `subscriptionId`가 UUID 형식이 아닐 때 |
| 401 | `AUTHENTICATION_REQUIRED` | 세션 쿠키가 없거나 만료됐을 때 |
| 403 | `CSRF_TOKEN_INVALID` | `X-CSRF-Token`이 없거나 틀릴 때 |
| 404 | `RESOURCE_NOT_FOUND` | 구독이 없거나 다른 사용자 소유일 때 |
| 422 | `VALIDATION_ERROR` | `enabled` 누락, `endpoint`가 https 절대 URL이 아닐 때, `p256dh`가 P-256 곡선 위의 점이 아닐 때, `auth`가 16바이트가 아닐 때 |

## 구독 흐름 예시

```js
// 1. 권한 요청
const permission = await Notification.requestPermission();
await fetch('/v1/me/notification-settings', {
  method: 'PATCH',
  credentials: 'include',
  headers: { 'Content-Type': 'application/json', 'X-CSRF-Token': csrfToken },
  body: JSON.stringify({
    enabled: permission === 'granted',
    permissionState: permission.toUpperCase(), // GRANTED | DENIED | DEFAULT
  }),
});

// 2. 권한을 받았으면 구독 생성
if (permission === 'granted') {
  const registration = await navigator.serviceWorker.ready;
  const subscription = await registration.pushManager.subscribe({
    userVisibleOnly: true,
    applicationServerKey: urlBase64ToUint8Array(VAPID_PUBLIC_KEY),
  });

  // 3. 서버에 등록
  await fetch('/v1/push-subscriptions', {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', 'X-CSRF-Token': csrfToken },
    body: JSON.stringify(subscription.toJSON()),
  });
}
```

`VAPID_PUBLIC_KEY`는 백엔드 `VAPID_PUBLIC_KEY`와 **같은 값이어야 합니다**(87자 base64url).
값이 다르면 오류 없이 구독만 만들어지고 알림이 도착하지 않습니다.

## 수신 payload

Service Worker의 `push` 이벤트로 아래 JSON이 도착합니다.

```json
{
  "type": "FOLLOW_UP",
  "title": "어제 피부는 어땠나요?",
  "body": "경과를 남기면 다음 관리에 반영해요.",
  "url": "/skin-reports/{reportId}/follow-up"
}
```

```json
{
  "type": "DAILY_CHECK_IN",
  "title": "오늘 피부는 어땠나요?",
  "body": "오늘의 피부 점호를 남겨 주세요.",
  "url": "/check-in"
}
```

- payload에는 부위, 증상 같은 피부 상세정보를 넣지 않습니다. 알림은 잠금 화면에 그대로 보입니다.
  상세 내용은 `url`로 이동한 뒤 API로 조회해 주십시오.
- `url`은 앱 내부 경로입니다. `notificationclick`에서 이 경로로 이동시켜 주십시오.
- 발송은 하루 한 사용자당 한 건입니다. 미완료 경과가 있으면 `FOLLOW_UP`이 우선하고, 없으면
  `DAILY_CHECK_IN`이 갑니다. 그날 점호를 이미 마쳤더라도 `DAILY_CHECK_IN`은 발송됩니다.
- 발송 시각은 Asia/Seoul 17:30 기준이고 도착 시각은 보장되지 않습니다. Push 서비스 전달 시점은
  서버가 통제할 수 없습니다.

## 참고

- 구독이 만료되어 Push 서비스가 `404` 또는 `410`을 돌려주면 서버가 그 구독을 비활성으로 내립니다.
  이후 `activeSubscriptionCount`가 줄어드니, 값이 0이면 재구독을 안내해 주십시오.
- Swagger UI에서 실제 스키마를 확인할 수 있습니다: `http://localhost:8080/swagger-ui.html`
