# 백엔드 개발 컨벤션

## 브랜치

- `develop`: 기본 개발 브랜치
- `main`: 운영 브랜치
- `feat/{이슈번호}`: 기능 개발 브랜치
- 기능 개발 후 `develop`에 병합하고, 개발 환경 검증 후 `main`에 병합한다.

## 커밋 메시지

```text
{이슈번호} {type}: 한글 커밋 메시지
```

예시:

```text
1 chore: Spring Boot 프로젝트 초기 구조 설정
1 test: Health Check 컨트롤러 테스트 추가
```

## Java 이름 규칙

- 패키지: lower-case
- 클래스: UpperCamelCase
- 메서드·변수: lowerCamelCase
- 상수·Enum 값: UPPER_SNAKE_CASE
- 요청 DTO: `DomainRequest`, `EntityNameRequest`
- 응답 DTO: `DomainResponse`, `EntityNameResponse`

## 응답 DTO

- Lombok 클래스와 `private final` 필드를 사용한다.
- 생성자는 private으로 제한하고 `of()` 정적 팩토리를 제공한다.
- 엔티티에서 생성하는 응답은 `from(entity)`를 제공한다.
- 성공 응답은 공통 봉투 없이 DTO를 직접 반환한다.
- 목록 응답에는 `PageResponse<T>`를 사용한다.
- 오류 응답에는 `ErrorResponse`를 사용한다.

## 협업

- 공통 DTO와 Enum은 작업 시작 전에 담당자를 정한다.
- 다른 도메인의 내부 구현에 직접 의존하지 않는다.
- 외부 입력은 Controller 경계에서 검증한다.
