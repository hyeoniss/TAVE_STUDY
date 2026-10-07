# Todomate

Java 17 + Spring Boot 기반 투두 API 프로젝트입니다. 투두 CRUD, 날짜 조건 조회,
페이지네이션, 공통 응답과 예외 처리, Redis 목록 캐시를 포함합니다.

## 실행

```bash
docker compose up -d redis
./gradlew bootRun
```

실행 후 브라우저에서 `http://localhost:8080`에 접속하면 기본 투두 화면을 사용할 수 있습니다.

Gradle에는 Web MVC, JPA, Validation, Redis/Cache, Actuator, Lombok, MySQL과 테스트용 H2가 등록되어 있습니다.

## API 명세

기본 경로는 `/api/todos`이며 API 버저닝은 적용하지 않습니다.

| Method | URL | 설명 | 응답 상태 |
|---|---|---|---:|
| POST | `/api/todos` | 투두 등록 | 201 |
| GET | `/api/todos?page=0&size=20` | 전체 투두 조회 | 200 |
| GET | `/api/todos?date=2026-10-06&page=0&size=20` | 날짜별 투두 조회 | 200 |
| GET | `/api/todos/{id}` | 투두 단건 조회 | 200 |
| PATCH | `/api/todos/{id}` | 제목·메모·날짜 부분 수정 | 200 |
| PATCH | `/api/todos/{id}/completion` | 완료 상태 변경 | 200 |
| DELETE | `/api/todos/{id}` | 투두 삭제 | 200 |

### 투두 등록

```http
POST /api/todos
Content-Type: application/json
```

```json
{
  "title": "스프링 강의 듣기",
  "memo": "캐시 파트까지",
  "todoDate": "2026-10-06"
}
```

- `title`: 필수, 공백 불가, 최대 100자
- `memo`: 선택, 최대 500자
- `todoDate`: 필수, `yyyy-MM-dd` 형식
- 성공 시 `Location: /api/todos/{id}` 헤더를 반환합니다.

### 투두 목록 조회

```http
GET /api/todos?date=2026-10-06&page=0&size=20
```

| 파라미터 | 필수 | 기본값 | 설명 |
|---|---:|---:|---|
| date | 아니요 | 전체 날짜 | 조회할 날짜 (`yyyy-MM-dd`) |
| page | 아니요 | 0 | 0부터 시작하는 페이지 번호 |
| size | 아니요 | 20 | 페이지 크기, 1~100 |

정렬 순서는 `todoDate DESC`, `createdAt DESC`입니다.

### 투두 부분 수정

변경할 필드만 전달합니다.

```http
PATCH /api/todos/{id}
Content-Type: application/json
```

```json
{
  "title": "스프링 강의 복습",
  "memo": "페이징까지 학습"
}
```

빈 요청 `{}`은 허용하지 않습니다. 현재 `memo: null`은 메모 삭제가 아니라 수정하지 않음으로 처리합니다.

### 완료 상태 변경

```http
PATCH /api/todos/{id}/completion
Content-Type: application/json
```

```json
{
  "completed": true
}
```

토글 방식이 아니라 목표 상태를 전달하므로 같은 요청을 반복해도 결과가 동일합니다.

## 공통 응답

### 성공 응답

```json
{
  "success": true,
  "data": {},
  "timestamp": "2026-10-07T10:00:00"
}
```

삭제 응답은 전달할 데이터가 없으므로 `data`가 `null`입니다.

### 페이지 응답

```json
{
  "success": true,
  "data": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0,
    "first": true,
    "last": true,
    "hasNext": false
  },
  "timestamp": "2026-10-07T10:00:00"
}
```

### 오류 응답

```json
{
  "success": false,
  "code": "TODO_404",
  "message": "투두를 찾을 수 없습니다.",
  "errors": [],
  "path": "/api/todos/999",
  "timestamp": "2026-10-07T10:00:00"
}
```

| 오류 코드 | HTTP 상태 | 설명 |
|---|---:|---|
| `COMMON_400` | 400 | 요청값 또는 형식 오류 |
| `PAGE_400` | 400 | 페이지 번호·크기 오류 |
| `TODO_404` | 404 | 존재하지 않는 투두 |
| `COMMON_405` | 405 | 지원하지 않는 HTTP 메서드 |
| `COMMON_500` | 500 | 서버 내부 오류 |

요청값 검증에 실패하면 `errors`에 필드명, 거절된 값, 실패 이유가 포함됩니다.

## Redis 캐시

- 전체 및 날짜별 목록 조회 결과를 Redis에 캐싱합니다.
- 캐시 키는 `date:page:size` 조건으로 구분합니다.
- 캐시 TTL은 10분입니다.
- 등록·수정·완료 변경·삭제 후 목록 캐시를 모두 제거합니다.

## 프로파일

- `local`: H2 인메모리 DB, 스키마 자동 생성, SQL 출력
- `prod`: MySQL, 환경변수 기반 연결, 스키마 검증

운영 환경변수:

```text
DB_URL
DB_USERNAME
DB_PASSWORD
REDIS_HOST
REDIS_PORT
```

## 헬스체크

```http
GET /actuator/health
```

정상 응답:

```json
{
  "status": "UP"
}
```

## 상세 문서

구현 구조와 API별 처리 과정은 [Todomate API 구현 설명서](docs/TODO_API_IMPLEMENTATION.md)를 참고합니다.
