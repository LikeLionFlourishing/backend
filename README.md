# flourishing-backend

관리하는 행보관 서비스의 Spring Boot 백엔드 저장소입니다.

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

**2번을 건너뛰면 오류 없이 화면이 빕니다.** `guide_sections` 가 비어 있으면
`GET /v1/reference-data/skin-report-options` 와 결과 카드의 `guideSections` 가 `[]` 로
나가고, 프론트가 결과 화면 골격을 그릴 수 없습니다. 기동은 정상적으로 되고 로그에 경고만
남으므로 알아차리기 어렵습니다.

적용됐는지 확인하려면 6행이 있어야 합니다.

```bash
$DB -e "SELECT COUNT(*) FROM guide_sections;"   # 6
```

`db/seed/` 는 여러 번 돌려도 결과가 같습니다. 이미 있으면 문구만 최신으로 맞춥니다.

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
