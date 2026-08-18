# flourishing-backend

제대로 서비스의 Spring Boot 백엔드 저장소입니다.

## 요구사항

- JDK 21
- Docker 및 Docker Compose
- MySQL Client(DDL 수동 적용 시)

## 기술 스택

- Spring Boot 3.4.2
- Gradle Groovy DSL
- Spring Data JPA, QueryDSL 5.1.0
- MySQL 8.0, Redis 7
- Spring Security, Bean Validation
- springdoc OpenAPI 2.8.6
- JUnit 5

## 로컬 실행

### 1. 환경변수 준비

```bash
cp .env.example .env
```

`.env`의 `DB_PASSWORD`를 로컬에서 사용할 값으로 변경하고,
`RECORD_DATA_ENCRYPTION_KEY`와 `PUSH_DATA_ENCRYPTION_KEY`에는 각각
`openssl rand -base64 32`로 생성한 값을 입력합니다.
실제 비밀값은 Git에 커밋하지 않습니다.

Web Push 알림을 실제로 발송하려면 VAPID 키도 필요합니다. 키가 비어 있으면 애플리케이션은
정상 기동하고 17:30 발송 작업만 건너뜁니다.

```bash
# 1. P-256 키 쌍 생성
umask 077
openssl ecparam -name prime256v1 -genkey -noout -out vapid.pem

# 2. VAPID_PUBLIC_KEY (비압축 65바이트를 패딩 없는 base64url로)
openssl ec -in vapid.pem -pubout -outform DER \
  | tail -c 65 | openssl base64 -A | tr '+/' '-_' | tr -d '='

# 3. VAPID_PRIVATE_KEY (32바이트 스칼라를 패딩 없는 base64url로)
openssl ec -in vapid.pem -outform DER \
  | tail -c +8 | head -c 32 | openssl base64 -A | tr '+/' '-_' | tr -d '='
```

공개키는 87자, 비밀키는 43자가 나옵니다. 공개키를 디코딩하면 `0x04`로 시작하는 65바이트입니다.

`VAPID_SUBJECT`에는 push 서비스가 연락할 수 있는 `mailto:` 또는 `https:` URI를 넣습니다.

AI 구조화와 관리 설명 생성을 실제로 쓰려면 `OPENAI_API_KEY`와 `OPENAI_MODEL`이 필요합니다.
구조화 응답(`text.format.type = json_schema`, `strict: true`)을 지원하는 모델을 지정합니다.

```bash
OPENAI_API_KEY=sk-...
OPENAI_MODEL=gpt-4.1-mini
```

키나 모델이 비어 있어도 애플리케이션은 정상 기동합니다. 이때 구조화 API는
`processingStatus: FAILED`와 사용자가 직접 고른 값을 돌려주고, 관리 결과는 승인된 규칙의
대체 문구로 저장됩니다. 원문, 프롬프트, 모델 원본 응답, API 키는 로그에 남기지 않고
요청에 `store: false`를 실어 OpenAI 쪽에도 대화를 남기지 않습니다.

주의할 점이 있습니다.

- `vapid.pem`은 커밋하지 않고 비밀 저장소로 옮긴 뒤 삭제합니다.
- 공개키는 프론트엔드의 `applicationServerKey`와 같은 값이어야 합니다. 다르면 구독은
  만들어지지만 알림이 전달되지 않습니다.
- 키를 바꾸면 기존 구독이 모두 무효가 되어 사용자가 다시 구독해야 합니다. 환경별로
  다른 키를 쓰면 환경을 옮길 때 구독도 함께 무효가 됩니다.

### 2. MySQL과 Redis 실행

```bash
DB_PASSWORD=<비밀번호> docker compose up -d mysql-db redis
```

### 3. 스키마와 기준 데이터 적용

애플리케이션은 `ddl-auto: validate`를 사용하고 `spring.sql.init.mode`도 `never`라, DDL과
기준 데이터를 직접 적용해야 합니다. 아래 순서대로 한 번씩 돌립니다.

```bash
DB="mysql -h 127.0.0.1 -P 3306 -u root -p flourishing"

# 1. 스키마 (필수)
$DB < db/schema.sql

# 2. 기준 데이터 (필수)
for f in db/seed/*.sql; do $DB < "$f"; done
```

**2번을 건너뛰면 피부 보고 제출이 막히고 결과 화면이 빕니다.**

`db/seed/` 안의 두 파일이 각각 다르게 필요합니다.

- `20260818_care_rules_v0_3.sql` — 관리규칙 26건과 성분 8건입니다. 활성 규칙 세트가 없으면
  `POST /v1/skin-reports` 가 503 `RULE_ENGINE_UNAVAILABLE` 로 막힙니다. 안내할 근거가 없을 때
  문구를 지어내지 않기 때문입니다.
- `20260818_guide_sections.sql` — 결과 카드의 6개 섹션 제목입니다. 비어 있으면
  `GET /v1/reference-data/skin-report-options` 와 결과 카드의 `guideSections` 가 `[]` 로
  나가고 프론트가 결과 화면 골격을 그릴 수 없습니다. 기동은 정상적으로 되고 로그에 경고만
  남으므로 알아차리기 어렵습니다.

두 파일 모두 재실행해도 안전합니다. 이미 있으면 문구만 최신으로 맞춥니다.

적용됐는지 확인합니다.

```bash
$DB -e "SELECT
  (SELECT COUNT(*) FROM care_rules) AS care_rules,
  (SELECT COUNT(*) FROM care_ingredients WHERE active = TRUE) AS ingredients,
  (SELECT COUNT(*) FROM guide_sections) AS guide_sections,
  (SELECT COUNT(*) FROM rule_sets WHERE status = 'ACTIVE') AS active_rule_sets;"
```

`26 / 8 / 6 / 1` 이 나와야 합니다.

### 4. 애플리케이션 실행

```bash
DB_PASSWORD=<비밀번호> \
SPRING_PROFILES_ACTIVE=local \
./gradlew bootRun
```

정상 실행 확인:

```bash
curl http://localhost:8080/health
```

예상 응답:

```text
OK
```

## Docker 애플리케이션 실행

먼저 프로젝트를 빌드하고 스키마를 적용한 다음 애플리케이션 프로파일을 활성화합니다.

```bash
./gradlew clean build
DB_PASSWORD=<비밀번호> docker compose --profile app up --build
```

## 운영 배포

도메인 기반 HTTPS로 올리는 절차는 [배포 안내](docs/DEPLOY.md)에 있습니다. 배포 기준 브랜치는
`main` 입니다.

```bash
docker compose -f docker-compose.prod.yml up -d --build
./scripts/apply-db.sh
```

로컬용 `docker-compose.yml` 과 파일을 나눠 두었습니다. 노출 범위가 정반대라 한 파일에 프로필로
섞으면 어느 쪽이 열려 있는지 읽기 어려워집니다. 운영 구성은 Caddy 만 80·443 을 잡고 앱과
MySQL, Redis 는 컨테이너 네트워크 안에만 열려 있으며, 인증서 발급과 갱신은 Caddy 가 맡습니다.

`scripts/apply-db.sh` 는 마이그레이션과 시드를 순서대로 넣고 결과를 세어 보여 줍니다. MySQL
초기화 스크립트는 볼륨이 비어 있는 첫 기동에만 실행되므로, 두 번째 배포부터는 이 스크립트가
그 자리를 맡습니다.

## 테스트와 빌드

```bash
./gradlew test
./gradlew clean build --no-daemon
```

테스트 프로파일은 H2를 사용하므로 단위·컨텍스트 테스트 실행에 MySQL이 필요하지 않습니다.

## API 문서

애플리케이션 실행 후 다음 경로를 사용합니다.

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

운영 프로파일(`SPRING_PROFILES_ACTIVE=prod`)에서는 두 경로를 닫습니다. `SecurityConfig` 의
공개 경로 목록에 들어 있어 켜 두면 인증 없이 열리고, 명세는 저장소와 팀 문서에 있으므로
서버에서 다시 내보낼 이유가 없습니다.

## 주요 환경변수

| 변수 | 설명 | 기본값 |
|---|---|---|
| `DB_USERNAME` | MySQL 사용자명 | `root` |
| `DB_PASSWORD` | MySQL 비밀번호 | 없음 |
| `SPRING_DATASOURCE_URL` | MySQL JDBC URL | 로컬 `flourishing` DB |
| `REDIS_HOST` | Redis 호스트 | `localhost` |
| `REDIS_PORT` | Redis 포트 | `6379` |
| `FRONTEND_ALLOWED_ORIGINS` | 허용할 프론트엔드 Origin 목록 | `http://localhost:5173` |
| `RECORD_DATA_ENCRYPTION_KEY` | 피부 기록 원문 암호화·목록 커서 서명용 Base64 32바이트 마스터 키 | 없음(필수) |
| `PUSH_DATA_ENCRYPTION_KEY` | Push 구독 암호화·endpoint 지문용 Base64 32바이트 마스터 키 | 없음(필수) |
| `VAPID_PUBLIC_KEY` | VAPID 공개키(비압축 65바이트 base64url) | 없음(발송 시 필요) |
| `VAPID_PRIVATE_KEY` | VAPID 비밀키(32바이트 스칼라 base64url) | 없음(발송 시 필요) |
| `VAPID_SUBJECT` | VAPID subject(`mailto:` 또는 `https:` URI) | 없음(발송 시 필요) |
| `OPENAI_API_KEY` | OpenAI API 키 | 없음(AI 사용 시 필요) |
| `OPENAI_MODEL` | 구조화 응답을 지원하는 모델 이름 | 없음(AI 사용 시 필요) |
| `OPENAI_BASE_URL` | OpenAI API 기본 주소 | `https://api.openai.com/v1` |
| `SERVER_PORT` | 애플리케이션 포트 | `8080` |
| `SESSION_COOKIE_SAME_SITE` | 세션 쿠키 SameSite. 프런트엔드가 다른 등록 도메인이면 `None` | `Lax` |
| `PROBLEM_BASE_URI` | 오류 응답 `type` 의 기준 주소 | `https://api.example.invalid/problems` |

운영 배포에서만 쓰는 변수입니다. `docker-compose.prod.yml` 이 읽습니다.

| 변수 | 설명 | 없으면 |
|---|---|---|
| `APP_DOMAIN` | 인증서를 받을 도메인. A 레코드가 서버를 가리켜야 합니다 | 기동 전 중단 |
| `ACME_EMAIL` | Let's Encrypt 만료 알림 주소 | 기동 전 중단 |

## 데이터베이스

- DB 이름: `flourishing`
- 문자셋: `utf8mb4`
- 기본 시간대: UTC
- 애플리케이션은 스키마를 자동 생성하거나 변경하지 않습니다.

`db/` 아래 세 종류를 구분해서 씁니다.

| 경로 | 무엇 | 언제 |
|---|---|---|
| `db/schema.sql` | 전체 DDL | 새 DB를 만들 때 한 번 |
| `db/seed/` | 기준 데이터 | 스키마 적용 뒤 한 번. 여러 번 돌려도 안전 |
| `db/migration/` | 기존 데이터 변환 | 이미 데이터가 있는 DB를 새 스키마로 옮길 때 |

**`db/migration/` 은 새 DB에는 돌리지 않습니다.** `db/schema.sql` 이 이미 최신 정의를 담고
있어, 빈 DB에 스키마를 적용하면 마이그레이션이 할 일이 없습니다. 운영 중인 DB를 옮길 때만
**스키마 변경 전에** 먼저 돌립니다. 순서를 지키지 않으면 새 CHECK 제약을 만들 수 없어
적용이 중간에 멈춥니다. 각 스크립트 머리말에 무엇을 왜 옮기는지 적혀 있습니다.

요구 버전은 MySQL **8.0.19 이상**입니다. `db/seed/` 가 `INSERT ... AS new ON DUPLICATE KEY
UPDATE` 별칭 문법을 쓰고, 그 문법이 8.0.19에서 들어왔습니다.

### 마이그레이션

`db/migration/`에 날짜순으로 둡니다. 이미 데이터가 있는 DB에 새 스키마를 적용하기 전에
해당 스크립트를 먼저 돌려야 합니다.

| 스크립트 | 선행 조건 |
|---|---|
| `2026-08-18_v2_1_follow_ups.sql` | `follow_ups`에 `skin_change`가 `NEW_AREA`·`UNSURE`이거나 `action_completion`이 NULL인 행이 남아 있으면 명세 v2_1의 CHECK 제약을 만들 수 없습니다. |
| `20260818_v2_1_selection_values.sql` | 선택값 세 그룹을 v2.1로 옮깁니다. 리네임보다 옛 CHECK 제거가 먼저 와야 하므로 스크립트 안의 순서를 지킵니다. |
| `20260818_rule_taxonomy_v0_3.sql` | 관리규칙 9개 분류를 받도록 CHECK 세 개를 넓힙니다. 허용 목록만 넓혀 기존 행은 그대로 통과합니다. |

```bash
docker exec -i flourishing-mysql mysql -uroot -p<비밀번호> flourishing < db/migration/2026-08-18_v2_1_follow_ups.sql
```

## 브랜치 전략

- `develop`: 기본 개발 브랜치
- `main`: 운영 브랜치
- `feat/{이슈번호}`: 기능 개발 브랜치

기능 개발 후 `develop`에 병합하고, 개발 환경 검증 후 `main`에 병합합니다.

## 커밋 컨벤션

```text
{이슈번호} {type}: 한글 커밋 메시지
```

자세한 내용은 [개발 컨벤션](docs/CONVENTIONS.md)을 확인합니다.
