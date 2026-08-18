# 배포 안내

가비아 클라우드 인스턴스에 도메인 기반 HTTPS로 올리는 절차입니다. 배포 기준 브랜치는 `main`입니다.

구성은 컨테이너 네 개입니다. Caddy가 80·443을 잡고 인증서를 자동으로 발급·갱신하며, 나머지는 내부 네트워크에만 열려 있습니다.

```
인터넷 → :443 Caddy(proxy) → app:8080 → mysql-db:3306
                                      → redis:6379
```

## 1. 사전 준비

서버에서 확인할 것입니다.

- 공인 IP가 붙은 인스턴스와 SSH 접속
- 도메인의 A 레코드가 그 공인 IP를 가리킴 (이게 없으면 인증서 발급이 실패합니다)
- 방화벽에서 22, 80, 443 인바운드 허용. 가비아 콘솔의 보안 그룹과 서버 내부 방화벽 둘 다 봐야 합니다
- Docker와 Docker Compose v2

80을 함께 열어야 하는 이유는 Let's Encrypt의 HTTP-01 검증과 HTTPS 리다이렉트에 쓰이기 때문입니다.

DNS 전파를 먼저 확인합니다.

```bash
dig +short api.example.com    # 서버의 공인 IP가 나와야 합니다
```

## 2. 저장소 받기

```bash
git clone https://github.com/LikeLionFlourishing/backend.git
cd backend
git checkout main
```

## 3. 시크릿 만들기

`.env`는 서버에서만 만들고 커밋하지 않습니다.

```bash
cp .env.example .env
```

암호화 마스터 키 두 개를 서버에서 생성합니다.

```bash
openssl rand -base64 32   # RECORD_DATA_ENCRYPTION_KEY
openssl rand -base64 32   # PUSH_DATA_ENCRYPTION_KEY
```

이 값을 바꾸면 이미 저장된 기록 원문과 Push 구독 정보를 더 이상 풀 수 없습니다. 한 번 정하면 백업해 두고 바꾸지 않습니다.

Web Push를 쓰려면 VAPID 키쌍이 필요합니다. 생성 방법은 [README](../README.md)의 환경 변수 절에 있습니다. 비어 있어도 앱은 뜨고 매일 17:30 발송 작업만 건너뜁니다.

`.env`에 채울 값입니다.

| 변수 | 값 | 없으면 |
|---|---|---|
| `APP_DOMAIN` | `api.example.com` | 기동 전 중단 |
| `ACME_EMAIL` | 만료 알림 받을 주소 | 기동 전 중단 |
| `DB_PASSWORD` | 임의의 강한 비밀번호 | 기동 전 중단 |
| `RECORD_DATA_ENCRYPTION_KEY` | `openssl rand -base64 32` | 기동 전 중단 |
| `PUSH_DATA_ENCRYPTION_KEY` | `openssl rand -base64 32` | 기동 전 중단 |
| `FRONTEND_ALLOWED_ORIGINS` | `https://app.example.com` | 기동 전 중단 |
| `DOCS_BASIC_AUTH_USER` | API 문서 계정 이름 | 프록시 기동 전 중단 |
| `DOCS_BASIC_AUTH_HASH` | `caddy hash-password` 결과 | 프록시 기동 전 중단 |
| `SESSION_COOKIE_SAME_SITE` | `Lax` 또는 `None` | `Lax` |
| `API_DOCS_ENABLED` | `true` 면 서버에서 문서를 연다 | `false`(닫힘) |
| `VAPID_*` | P-256 키쌍 | 알림 발송만 건너뜀 |
| `OPENAI_API_KEY`·`OPENAI_MODEL` | OpenAI 키와 모델 | AI 설명이 규칙 문구로 대체 |

`SESSION_COOKIE_SAME_SITE`는 프런트엔드가 어디 있는지에 따라 다릅니다. API와 같은 등록 도메인이면 `Lax`로 두고, 다른 도메인이면 `None`이 필요합니다. `None`은 브라우저가 Secure를 함께 요구하는데 HTTPS라 충족됩니다.

API 문서 계정은 이렇게 만듭니다. 문서를 켜지 않아도 `Caddyfile`이 이 값을 읽으므로 비워 두면 프록시가 기동하지 못합니다.

```bash
HASH=$(docker run --rm caddy:2-alpine caddy hash-password --plaintext '비밀번호')
printf 'DOCS_BASIC_AUTH_HASH=%s\n' "$(printf '%s' "$HASH" | sed 's/\$/$$/g')" >> .env
```

해시의 `$`를 `$$`로 바꿔 넣는 것이 중요합니다. Compose가 `$`를 변수 시작으로 읽어 값이 잘리면 인증이 항상 실패합니다. 넣은 뒤 `docker compose -f docker-compose.prod.yml exec proxy printenv DOCS_BASIC_AUTH_HASH`로 `$2a$14$...` 형태가 그대로 들어갔는지 확인하세요.

`.env` 권한을 좁혀 둡니다.

```bash
chmod 600 .env
```

## 4. 기동

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

첫 빌드는 Gradle이 의존성을 받아 몇 분 걸립니다. 첫 기동 때 MySQL 볼륨이 비어 있으므로 `db/`의 스키마와 시드가 초기화 스크립트로 자동 적용됩니다.

```
db/schema.sql                          → 001
db/seed/20260818_guide_sections.sql    → 002
db/seed/20260818_care_rules_v0_3.sql   → 003
```

상태를 봅니다.

```bash
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f app
```

`app`이 `healthy`가 되면 Caddy가 인증서 발급을 시작합니다.

## 5. 확인

```bash
curl -i https://api.example.com/health
```

`200`과 함께 `strict-transport-security` 헤더가 보이면 프록시와 인증서까지 정상입니다.

규칙 데이터가 들어갔는지 봅니다. 이게 비어 있으면 피부 보고 제출이 503으로 막힙니다.

```bash
./scripts/apply-db.sh
```

인자 없이 실행하면 마이그레이션과 시드만 적용하고 결과를 세어 보여 줍니다. `care_rules 26 / ingredients 8 / guide_sections 6 / active_rule_sets 1`이면 정상입니다.

## API 문서를 서버에서 열기

기본은 닫힘입니다. 저장소의 `docs/openapi.generated.json`으로 충분하면 켜지 않는 편이 낫습니다. 서버에서 Swagger UI를 봐야 하면 `.env`에 아래를 넣고 다시 올립니다.

```bash
API_DOCS_ENABLED=true
```

```bash
docker compose -f docker-compose.prod.yml up -d
```

접속 주소는 이렇습니다. `DOCS_BASIC_AUTH_USER`와 설정한 비밀번호로 Basic 인증을 통과해야 합니다.

- Swagger UI: `https://api.example.com/swagger-ui.html`
- OpenAPI JSON: `https://api.example.com/v3/api-docs`

두 겹으로 막혀 있습니다. 프록시가 `/swagger-ui*`와 `/v3/api-docs*`에 Basic 인증을 걸고, 애플리케이션은 `API_DOCS_ENABLED`가 꺼져 있으면 404를 돌려줍니다. 그래서 자격을 넣어도 문서를 켜지 않은 배포에서는 404가 나옵니다.

인증이 계속 실패하면 `.env`의 해시에서 `$`가 `$$`로 이스케이프되었는지 확인하세요.

```bash
docker compose -f docker-compose.prod.yml exec proxy printenv DOCS_BASIC_AUTH_HASH
```

`$2a$14$...` 형태로 온전히 나와야 합니다. `2a14...` 처럼 `$`가 빠져 있으면 Compose가 변수로 해석해 값을 삼킨 것입니다.

## 6. 다시 배포하기

```bash
git pull origin main
docker compose -f docker-compose.prod.yml up -d --build
./scripts/apply-db.sh
```

MySQL 초기화 스크립트는 볼륨이 비어 있는 첫 기동에만 실행됩니다. 두 번째 배포부터는 실행되지 않으므로 마이그레이션과 시드를 `apply-db.sh`로 넣습니다. 두 종류 모두 재실행에 안전하게 쓰여 있어 여러 번 돌려도 결과가 같습니다.

## 7. 막힐 때

**인증서 발급이 안 됩니다**

```bash
docker compose -f docker-compose.prod.yml logs proxy
```

대개 셋 중 하나입니다. A 레코드가 아직 전파되지 않았거나, 80 포트가 막혀 있거나, 발급 한도에 걸렸습니다. Let's Encrypt는 같은 도메인에 주당 발급 횟수 제한이 있습니다. `caddy_data` 볼륨을 지우고 다시 올리면 재발급을 시도하므로, 시행착오 중에는 볼륨을 지우지 않는 편이 좋습니다.

**앱이 `healthy`가 되지 않습니다**

```bash
docker compose -f docker-compose.prod.yml logs app
```

`ddl-auto: validate`라서 스키마가 엔티티와 맞지 않으면 기동에서 멈춥니다. 마이그레이션을 빠뜨렸을 때 이렇게 됩니다.

**로그인이 되는데 이후 요청이 401입니다**

세션 쿠키가 저장되지 않는 상황입니다. 쿠키 이름이 `__Host-session`이라 HTTPS와 Secure 속성을 요구합니다. HTTPS로 접속하고 있는지, 프런트엔드 출처가 `FRONTEND_ALLOWED_ORIGINS`에 있는지, 다른 도메인이라면 `SESSION_COOKIE_SAME_SITE=None`인지 봅니다.

**피부 보고 제출이 503 `RULE_ENGINE_UNAVAILABLE`입니다**

활성 규칙 세트가 없습니다. `./scripts/apply-db.sh`를 실행하고 `active_rule_sets`가 1인지 확인합니다.

## 운영 프로파일에서 달라지는 것

`SPRING_PROFILES_ACTIVE=prod`가 켜지면 세 가지가 바뀝니다.

- SQL 로그를 끕니다. 기본 프로파일은 `debug`라 모든 쿼리가 로그로 나갑니다
- Swagger UI와 `/v3/api-docs`를 닫습니다. 이 경로는 인증 없이 열려 있어 서버에서 내보낼 이유가 없습니다
- `PROBLEM_BASE_URI`가 배포 도메인 기준으로 맞춰집니다

## 백업

지켜야 할 볼륨은 `mysql_data`입니다. 기록 원문이 암호화되어 들어 있고, 암호를 푸는 키는 `.env`에 있습니다. **둘 중 하나만 있으면 복구되지 않습니다.** 데이터 백업과 키 백업을 함께 관리합니다.

```bash
docker compose -f docker-compose.prod.yml exec -T mysql-db \
  sh -c 'exec mysqldump -u root -p"$MYSQL_ROOT_PASSWORD" --single-transaction flourishing' \
  > backup-$(date +%F).sql
```
