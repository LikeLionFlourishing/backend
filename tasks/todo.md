# 이슈 #3 작업 목록: 피부 보고 선택값 조회 API

- [x] Task 1: 대표 부위와 겉모습 Enum을 정의합니다.
  - Acceptance: `BodyArea`, `Appearance`의 값·한글 라벨·선언 순서가 OpenAPI와 기획 문서에 일치합니다.
  - Verify: 두 Enum의 전체 값과 라벨을 검증하는 단위 테스트를 실행합니다.
  - Files: `report/domain/BodyArea.java`, `report/domain/Appearance.java`, 관련 테스트 파일

- [x] Task 2: 느껴지는 불편과 직전 상황 Enum을 정의합니다.
  - Acceptance: `Sensation`, `Situation`의 값·한글 라벨·선언 순서가 OpenAPI와 기획 문서에 일치합니다.
  - Verify: 두 Enum의 전체 값과 라벨을 검증하는 단위 테스트를 실행합니다.
  - Files: `report/domain/Sensation.java`, `report/domain/Situation.java`, 관련 테스트 파일

- [x] Task 3: 관리 가능 상태와 관리 전 확인 Enum을 정의합니다.
  - Acceptance: `CareAvailability`, `PreCareCheck`의 값·한글 라벨·선언 순서가 OpenAPI와 기획 문서에 일치합니다.
  - Verify: 두 Enum의 전체 값과 라벨을 검증하는 단위 테스트를 실행합니다.
  - Files: `report/domain/CareAvailability.java`, `report/domain/PreCareCheck.java`, 관련 테스트 파일

- [x] Task 4: 기준정보 응답 DTO와 조립 서비스를 구현합니다.
  - Acceptance: 서비스가 버전 `2026-08-09`와 여섯 선택값 그룹을 모두 반환하며 목록이 외부에서 변경되지 않습니다.
  - Verify: `ReferenceDataServiceTest`에서 버전, 그룹별 개수, 첫 값·마지막 값과 불변 목록을 검증합니다.
  - Files: `OptionResponse.java`, `SkinReportOptionsResponse.java`, `ReferenceDataService.java`, `ReferenceDataServiceTest.java`

- [x] Task 5: 기준정보 Controller와 인증 접근 정책을 구현합니다.
  - Acceptance: Mock 인증 사용자는 `GET /v1/reference-data/skin-report-options`에서 `200`과 계약 JSON을 받고 미인증 사용자는 접근할 수 없습니다.
  - Verify: `ReferenceDataControllerTest`를 실행합니다.
  - Files: `ReferenceDataController.java`, `SecurityConfig.java`, `ReferenceDataControllerTest.java`

- [x] Task 6: 계약과 전체 빌드를 검증합니다.
  - Acceptance: 공개 Enum 값·라벨·순서·버전이 테스트로 고정되고 관련 없는 파일 변경이 없습니다.
  - Verify: `GRADLE_USER_HOME=.gradle-user-home ./gradlew clean build --no-daemon`, `git diff --check`, `git status --short`
  - Files: 테스트 파일과 필요한 API 문서 주석

## 검증 결과

- JDK 21 main 전체 소스 컴파일: 통과
- JDK 21 test 전체 소스 컴파일: 통과
- JUnit 전체 테스트: 12개 성공, 0개 실패
- `git diff --check`: 통과
- Gradle `clean build`: 통과 (`BUILD SUCCESSFUL in 19s`, 9개 작업 실행)

## 완료 후 권장 커밋

```text
3 feat: 피부 보고 선택값 조회 API 구현
```
