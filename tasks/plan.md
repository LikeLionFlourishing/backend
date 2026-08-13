# 이슈 #3 구현 계획: 피부 보고 선택값 조회 API

## 전제와 결정

1. 기준 계약은 `20260809_관리하는행보관_OpenAPI_v2.yaml`의 `GET /reference-data/skin-report-options`입니다.
2. 애플리케이션 공통 경로를 포함한 실제 URI는 `GET /v1/reference-data/skin-report-options`입니다.
3. 선택값은 DB가 아니라 서버 Enum과 코드에 포함된 한글 라벨을 단일 기준으로 사용합니다.
4. 응답 버전은 현재 계약의 `2026-08-09`를 사용하며 선택값이나 라벨이 바뀔 때 함께 변경합니다.
5. 이 API는 사용자 ID를 사용하지 않으며 인증 여부만 요구합니다. 인증 Principal의 구체적인 타입은 이 이슈의 선행 조건이 아닙니다.
6. 응답 배열 순서는 실행 계약인 OpenAPI와 DDL의 순서를 유지하며 공개 API 계약으로 취급합니다. 직전 상황의 `OTHER`, `NONE_RECALLED` 순서는 기획 문서와 차이가 있어 OpenAPI 순서를 우선합니다.

## 목표

프론트엔드가 피부 보고 화면에서 사용할 대표 부위, 겉모습, 느껴지는 불편, 직전 상황, 현재 관리 상태와 관리 전 확인 목록을 서버 Enum 값과 동일한 형태로 조회할 수 있게 합니다.

## 기술 스택

- Java 21
- Spring Boot 3.4.2
- Spring Web MVC, Spring Security
- Lombok
- JUnit 5, MockMvc, Spring Security Test

DB를 사용하지 않는 읽기 전용 기준정보 API이므로 이 이슈에서는 JPA와 Testcontainers MySQL을 사용하지 않습니다.

## API 계약

### 요청

```http
GET /v1/reference-data/skin-report-options
Cookie: __Host-session=<opaque>
```

요청 본문과 쿼리 매개변수는 없습니다.

### 성공 응답

```json
{
  "version": "2026-08-09",
  "areas": [{"value": "LEFT_FOREHEAD", "label": "왼쪽 이마"}],
  "appearances": [{"value": "REDNESS", "label": "붉어짐"}],
  "sensations": [{"value": "ITCHING", "label": "가려움"}],
  "situations": [{"value": "SHAVING", "label": "면도"}],
  "careAvailability": [{"value": "ALREADY_WASHED", "label": "이미 세안·샤워함"}],
  "preCareChecks": [{"value": "NONE", "label": "해당하는 변화가 없어요."}]
}
```

응답은 성공 공통 봉투 없이 DTO를 직접 반환합니다.

## 프로젝트 구조

```text
src/main/java/likelion/flourishing/
├── report/domain/                 # Reports·Records가 공유할 피부 보고 Enum
└── referencedata/
    ├── controller/                # GET API와 OpenAPI 설명
    ├── dto/response/              # OptionResponse, SkinReportOptionsResponse
    └── service/                   # Enum을 버전 있는 응답으로 조립

src/test/java/likelion/flourishing/
├── report/domain/                 # Enum 값·라벨·순서 계약 테스트
└── referencedata/                 # 서비스 및 MockMvc 테스트
```

패키지는 모두 소문자를 사용합니다. `referenceData`가 아니라 `referencedata`로 작성합니다.

## 코드 스타일

응답 DTO는 프로젝트 컨벤션에 따라 Lombok 클래스, `private final` 필드, private 생성자와 정적 팩토리를 사용합니다.

```java
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class OptionResponse {

    private final String value;
    private final String label;

    public static OptionResponse of(String value, String label) {
        return new OptionResponse(value, label);
    }
}
```

Enum 값은 `UPPER_SNAKE_CASE`, 클래스와 DTO는 `UpperCamelCase`, 메서드와 변수는 `lowerCamelCase`를 사용합니다.

## 구현 순서와 의존성

1. OpenAPI·기획 문서·DDL의 선택값을 대조하여 공통 Enum과 한글 라벨을 정의합니다.
2. Enum 목록을 `OptionResponse`로 변환하고 전체 그룹과 버전을 조립하는 서비스를 구현합니다.
3. Controller를 구현하고 `/v1/reference-data/**`를 인증 사용자 경로로 등록합니다.
4. 값·라벨·순서·버전과 인증 접근 제어를 테스트합니다.
5. 전체 빌드와 변경 파일 검사를 실행합니다.

1→2→3은 순차 작업입니다. Enum 계약 테스트와 Controller 테스트는 구현 후 함께 검증할 수 있습니다.

## 테스트 전략

- Enum 계약 테스트
  - OpenAPI와 동일한 값의 개수·이름·순서를 검증합니다.
  - 모든 Enum의 한글 라벨이 공백이 아닌지 검증합니다.
- 서비스 단위 테스트
  - 여섯 그룹과 `2026-08-09` 버전을 검증합니다.
  - 응답 목록이 수정 불가능한 스냅샷인지 검증합니다.
- Controller 테스트
  - Mock 인증 사용자의 `200`과 전체 JSON 구조를 검증합니다.
  - 미인증 요청이 허용되지 않는지 검증합니다.
- 전체 검증
  - `GRADLE_USER_HOME=.gradle-user-home ./gradlew clean build --no-daemon`
  - `git diff --check`

인증 모듈 병합 전에는 최종 세션 쿠키를 이용한 `401 AUTHENTICATION_REQUIRED` 응답 형식까지 검증할 수 없습니다. 이 이슈에서는 Spring Security의 인증 필요 정책과 Mock 인증 성공 경로를 검증하고, 실제 쿠키 세션 오류 계약은 인증 모듈 통합 시 검증합니다.

## 경계

### 항상 수행

- OpenAPI, 기획 문서와 DDL의 Enum 값을 대조합니다.
- API 응답에 정의된 여섯 그룹을 모두 포함합니다.
- 공개 응답 순서와 버전을 테스트로 고정합니다.
- 구현 후 테스트와 전체 빌드를 실행합니다.

### 먼저 확인

- 선택값, 한글 라벨 또는 버전 변경
- DB 기반 기준정보로 전환
- 공통 Enum의 패키지 소유권 변경
- 인증 모듈의 Principal 또는 오류 계약 수정

### 수행하지 않음

- 검토되지 않은 관리 규칙이나 의료 행동 문구 추가
- 피부질환·제품·의약품 기준정보 추가
- 실제 사용자나 임시 사용자 ID를 하드코딩
- 선택값을 DB와 코드에 이중 관리

## 위험과 대응

| 위험 | 영향 | 대응 |
|---|---|---|
| OpenAPI·DDL·Enum 값 불일치 | Reports 저장 또는 프론트 요청 실패 | 세 자료를 대조하고 Enum 계약 테스트로 고정 |
| 응답 배열 순서 변경 | 프론트 표시 순서 변경 | OpenAPI 순서를 Enum 선언 순서로 고정하고 테스트 |
| 인증 모듈 미완성 | 실제 쿠키 인증 E2E 불가 | `authenticated()` 정책과 Mock 인증을 검증하고 통합 검증을 후속 수행 |
| Enum이 ReferenceData에 종속 | Reports·Records의 역방향 의존성 발생 | Enum은 `report.domain`, 응답 조립만 `referencedata`에 배치 |
| 버전 미변경 | 프론트 캐시와 서버 값 불일치 | 선택값·라벨 변경 시 버전 변경을 완료 조건으로 지정 |

## 성공 기준

- 인증된 요청에 `200`과 여섯 선택값 그룹 및 버전을 반환합니다.
- 모든 값·라벨·순서가 OpenAPI와 기획 문서에 일치합니다.
- DB 조회 없이 동작합니다.
- 성공 응답은 공통 봉투 없이 DTO를 직접 반환합니다.
- 미인증 요청은 접근할 수 없습니다.
- 관련 테스트와 전체 Gradle 빌드가 통과합니다.

## 열린 사항

- 인증 담당자가 실제 세션 Principal과 `401` 오류 응답을 완성한 뒤 쿠키 기반 통합 테스트를 추가해야 합니다. 이 사항은 ReferenceData 비즈니스 구현을 막지 않습니다.
