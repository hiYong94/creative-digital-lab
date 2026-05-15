# API 규칙

## URL 설계

- 복수형 명사: `/sessions`, `/events`
- 계층 관계: `/sessions/{id}/events`, `/sessions/{id}/timeline`
- 동사 사용 금지 (행위는 HTTP 메서드로 표현)

## HTTP 메서드 의미

| 동작 | 메서드 | 예시 |
|------|--------|------|
| 세션 목록 조회 | `GET` | `GET /sessions` |
| 세션 단건 조회 | `GET` | `GET /sessions/{id}` |
| 세션 생성 | `POST` | `POST /sessions` |
| 세션 종료 | `DELETE` | `DELETE /sessions/{id}` |
| 참여 | `POST` | `POST /sessions/{id}/participants` |
| 이벤트 수집 | `POST` | `POST /sessions/{id}/events` |
| 타임라인 복원 | `GET` | `GET /sessions/{id}/timeline?at=` |

## 응답 코드

| 상황 | 코드 |
|------|------|
| 정상 생성 | 201 |
| 정상 조회·처리 | 200 |
| 중복 요청 (멱등) | 200 (기존 결과 반환) |
| 유효성 오류 | 400 |
| 세션 없음 | 404 |
| 이미 참여 | 409 |
| 비즈니스 규칙 위반 | 422 |
| 서버 오류 | 500 |

## 에러 응답 형식

```json
{
  "httpStatus": 404,
  "code": "SESSION_NOT_FOUND",
  "message": "세션을 찾을 수 없습니다.",
  "traceId": "abc123"
}
```

- `httpStatus`: HTTP 응답 코드와 동일한 값. 클라이언트가 헤더 없이도 코드를 확인할 수 있게 한다.
- `code`: 도메인 예외 클래스명 기반 상수 (예: `SESSION_NOT_FOUND`)
- `traceId`: MDC에서 주입. 로그와 연계.
- 스택 트레이스는 응답 바디에 포함하지 않는다.

## 페이지네이션

- 목록 API는 커서 기반 또는 `page`/`size` 파라미터 사용.
- 기본 `size`: 20. 최대 `size`: 100.
- 응답에 `totalCount` 포함 여부는 API별로 결정.

## 요청 유효성 검증

- Bean Validation (`@NotNull`, `@NotBlank`, `@Size`)은 DTO에 선언.
- 비즈니스 규칙 검증(ACTIVE 상태 여부 등)은 도메인 메서드에서 수행.
- Controller는 유효성 통과한 DTO를 Command로 변환해 Application Service에 전달.

## OpenAPI 문서

- `springdoc-openapi-starter-webmvc-ui:3.0.2` 사용.
- `/swagger-ui.html`에서 접근 가능.
- Controller 메서드에 `@Operation`, DTO 필드에 `@Schema` 선언.
