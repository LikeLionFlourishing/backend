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
# P-256 키 쌍 생성
openssl ecparam -name prime256v1 -genkey -noout -out vapid.pem

# VAPID_PUBLIC_KEY (비압축 65바이트 base64url)
openssl ec -in vapid.pem -pubout -outform DER \
  | tail -c 65 | basenc --base64url | tr -d '=\n'

# VAPID_PRIVATE_KEY (32바이트 스칼라 base64url)
openssl ec -in vapid.pem -outform DER \
  | tail -c +8 | head -c 32 | basenc --base64url | tr -d '=\n'
```

`VAPID_SUBJECT`에는 push 서비스가 연락할 수 있는 `mailto:` 또는 `https:` URI를 넣습니다.
`vapid.pem`은 커밋하지 않고 삭제하거나 비밀 저장소로 옮깁니다.

### 2. MySQL과 Redis 실행

```bash
DB_PASSWORD=<비밀번호> docker compose up -d mysql-db redis
```

### 3. 스키마 적용

애플리케이션은 `ddl-auto: validate`를 사용하므로 최초 한 번 DDL을 직접 적용해야 합니다.

```bash
mysql -h 127.0.0.1 -P 3306 -u root -p flourishing < db/schema.sql
```

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
| `SERVER_PORT` | 애플리케이션 포트 | `8080` |

## 데이터베이스

- DB 이름: `flourishing`
- 문자셋: `utf8mb4`
- 기본 시간대: UTC
- 스키마: `db/schema.sql`
- 애플리케이션은 스키마를 자동 생성하거나 변경하지 않습니다.

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
