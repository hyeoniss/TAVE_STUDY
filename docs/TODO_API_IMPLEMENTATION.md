# Todomate API 구현 설명서

## 1. 프로젝트 개요

Todomate는 날짜별 투두를 등록하고 조회·수정·완료 처리·삭제할 수 있는 REST API 서버다.

현재 구현에는 다음 기능이 포함되어 있다.

- 투두 CRUD
- 날짜 조건 조회
- 페이지네이션과 정렬
- 공통 성공·오류 응답
- 요청값 검증
- 전역 예외 처리
- Redis 조회 캐시
- local/prod 프로파일 분리
- Actuator 헬스체크
- JPA Auditing

API 버저닝은 적용하지 않으며 기본 리소스 경로로 `/api/todos`를 사용한다.

## 2. 기술 구성

| 구분 | 기술 |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 4.0.5 |
| Web | Spring Web MVC |
| ORM | Spring Data JPA, Hibernate |
| Database | MySQL, 로컬·테스트용 H2 |
| Cache | Redis, Spring Cache |
| Validation | Jakarta Validation |
| Monitoring | Spring Boot Actuator |
| Build | Gradle |
| Boilerplate | Lombok |

## 3. 패키지 구성

도메인이 하나이므로 투두 관련 코드는 `todo` 패키지에 모았다. 여러 도메인에서 공통으로 사용하는
응답, 예외, 설정만 `global` 패키지로 분리했다.

```text
com.example.todomate
├── TodomateApplication.java
├── global
│   ├── config
│   │   └── RedisCacheConfig.java
│   ├── exception
│   │   ├── BusinessException.java
│   │   ├── CommonErrorCode.java
│   │   ├── ErrorCode.java
│   │   ├── ErrorResponse.java
│   │   └── GlobalExceptionHandler.java
│   └── response
│       ├── ApiResponse.java
│       └── PageResponse.java
└── todo
    ├── BaseTimeEntity.java
    ├── Todo.java
    ├── TodoController.java
    ├── TodoRepository.java
    ├── TodoRequest.java
    ├── TodoResponse.java
    └── TodoService.java
```

### 계층별 역할

| 계층 | 역할 |
|---|---|
| Controller | HTTP 요청 매핑, 요청값 검증, 상태 코드와 응답 생성 |
| Service | 트랜잭션, 비즈니스 규칙, 캐시 적용과 무효화 |
| Repository | JPA를 통한 데이터 저장과 조회 |
| Entity | 투두 데이터와 상태 변경 메서드 |
| Request/Response | API 입력과 출력 모델 |

일반적인 요청 처리 흐름은 다음과 같다.

```text
HTTP 요청
  → TodoController
  → TodoService
  → TodoRepository
  → Database
  → TodoResponse 변환
  → ApiResponse로 포장
  → HTTP 응답
```

## 4. 데이터 모델

`Todo` 엔티티는 다음 필드를 가진다.

| 필드 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | Long | PK, Auto Increment | 투두 식별자 |
| title | String | 필수, 최대 100자 | 투두 제목 |
| memo | String | 선택, 최대 500자 | 투두 메모 |
| todoDate | LocalDate | 필수 | 투두 수행 날짜 |
| completed | boolean | 필수 | 완료 여부 |
| createdAt | LocalDateTime | 자동 기록 | 생성 시각 |
| updatedAt | LocalDateTime | 자동 기록 | 마지막 수정 시각 |

`createdAt`, `updatedAt`은 `BaseTimeEntity`와 JPA Auditing으로 자동 기록한다.
애플리케이션 시작 클래스에 `@EnableJpaAuditing`을 적용했다.

날짜 조회와 정렬을 고려해 `todo_date, created_at` 복합 인덱스를 선언했다.

## 5. API 구현

### 5.1 투두 등록

```http
POST /api/todos
Content-Type: application/json
```

요청 예시:

```json
{
  "title": "스프링 강의 듣기",
  "memo": "자동 구성 파트",
  "todoDate": "2026-10-06"
}
```

검증 조건:

- `title`: 필수, 공백 불가, 최대 100자
- `memo`: 선택, 최대 500자
- `todoDate`: 필수, `yyyy-MM-dd` 형식

처리 과정:

1. `@Valid`로 요청 본문을 검증한다.
2. `TodoService.create()`가 요청값으로 `Todo` 엔티티를 생성한다.
3. `TodoRepository.save()`로 저장한다.
4. 저장 결과를 `TodoResponse`로 변환한다.
5. `201 Created`와 생성된 리소스의 `Location` 헤더를 반환한다.
6. 목록 데이터가 변경되었으므로 기존 `todoLists` 캐시를 제거한다.

응답 헤더 예시:

```http
HTTP/1.1 201 Created
Location: /api/todos/1
```

### 5.2 전체 투두 조회

```http
GET /api/todos?page=0&size=20
```

쿼리 파라미터:

| 이름 | 필수 | 기본값 | 제약 | 설명 |
|---|---:|---:|---:|---|
| page | 아니요 | 0 | 0 이상 | 0부터 시작하는 페이지 번호 |
| size | 아니요 | 20 | 1~100 | 한 페이지의 데이터 개수 |

처리 과정:

1. 페이지 번호와 크기를 검증한다.
2. `PageRequest`를 생성한다.
3. `todoDate DESC, createdAt DESC` 정렬로 DB를 조회한다.
4. `Page<Todo>`를 `PageResponse<TodoResponse>`로 변환한다.
5. 조회 결과를 Redis에 저장하거나 기존 Redis 캐시에서 반환한다.

정렬 기준:

```text
1순위: todoDate 내림차순
2순위: createdAt 내림차순
```

응답 예시:

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 1,
        "title": "스프링 강의 듣기",
        "memo": "자동 구성 파트",
        "todoDate": "2026-10-06",
        "completed": false,
        "createdAt": "2026-10-07T10:00:00",
        "updatedAt": "2026-10-07T10:00:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "first": true,
    "last": true,
    "hasNext": false
  },
  "timestamp": "2026-10-07T10:00:01"
}
```

### 5.3 날짜별 투두 조회

```http
GET /api/todos?date=2026-10-06&page=0&size=20
```

별도의 `/today` 엔드포인트를 만들지 않고 동일한 투두 컬렉션에 `date` 필터를 적용한다.
오늘의 투두가 필요하면 클라이언트가 오늘 날짜를 `date`에 전달한다.

처리 과정:

1. Spring이 `date`를 `LocalDate`로 변환한다.
2. `TodoRepository.findAllByTodoDate()`로 해당 날짜만 조회한다.
3. 전체 조회와 동일하게 정렬·페이징한다.
4. 날짜와 페이지 조건별로 Redis에 캐싱한다.

잘못된 날짜 형식은 `400 Bad Request` 공통 오류 응답으로 처리한다.

### 5.4 투두 단건 조회

```http
GET /api/todos/{id}
```

처리 과정:

1. `TodoRepository.findById()`로 ID를 조회한다.
2. 존재하면 `TodoResponse`로 변환한다.
3. 존재하지 않으면 `BusinessException(TODO_NOT_FOUND)`을 발생시킨다.
4. `GlobalExceptionHandler`가 이를 `404 Not Found` 오류 응답으로 변환한다.

단건 조회는 현재 Redis에 캐싱하지 않는다. 과제의 핵심인 목록 조회에만 캐시를 적용해
캐시 무효화 지점을 단순하게 유지한다.

### 5.5 투두 수정

```http
PATCH /api/todos/{id}
Content-Type: application/json
```

부분 수정 요청 예시:

```json
{
  "title": "스프링 강의 복습"
}
```

여러 필드를 수정하는 요청 예시:

```json
{
  "title": "스프링 강의 복습",
  "memo": "캐시 파트까지",
  "todoDate": "2026-10-07"
}
```

처리 과정:

1. ID로 기존 투두를 조회한다.
2. 요청에 포함된 필드만 엔티티에 반영한다.
3. 트랜잭션 종료 시 JPA 변경 감지로 SQL `UPDATE`가 실행된다.
4. 수정된 결과를 반환한다.
5. 기존 목록 캐시를 모두 제거한다.

모든 값이 빠진 빈 요청 `{}`은 `400 Bad Request`로 처리한다.

현재 `memo: null`은 “메모 제거”가 아니라 “메모를 수정하지 않음”으로 해석한다.
메모를 빈 값으로 변경하려면 빈 문자열을 전달한다.

### 5.6 완료 상태 변경

```http
PATCH /api/todos/{id}/completion
Content-Type: application/json
```

완료 처리:

```json
{
  "completed": true
}
```

완료 취소:

```json
{
  "completed": false
}
```

서버에서 현재 값을 반전하는 단순 토글로 구현하지 않고 클라이언트가 목표 상태를 전달한다.
따라서 같은 요청을 여러 번 보내더라도 결과가 동일하다. 요청 완료 여부가 불확실한 상황에서
클라이언트가 안전하게 재시도할 수 있다.

상태를 변경한 뒤 기존 목록 캐시를 모두 제거한다.

### 5.7 투두 삭제

```http
DELETE /api/todos/{id}
```

처리 과정:

1. ID로 투두를 조회한다.
2. 존재하지 않으면 `404 Not Found`를 반환한다.
3. 존재하면 `TodoRepository.delete()`로 삭제한다.
4. 기존 목록 캐시를 모두 제거한다.
5. 공통 응답 형식을 유지하기 위해 `200 OK`를 반환한다.

응답 예시:

```json
{
  "success": true,
  "data": null,
  "timestamp": "2026-10-07T10:00:00"
}
```

본문 없는 REST 응답을 우선한다면 `204 No Content`도 가능하지만, 현재 구현은 모든 성공 응답을
동일한 형태로 제공하는 요구사항에 맞춰 `200 OK`를 선택했다.

## 6. 공통 응답

### 성공 응답

모든 성공 응답은 `ApiResponse<T>`로 감싼다.

```json
{
  "success": true,
  "data": {},
  "timestamp": "2026-10-07T10:00:00"
}
```

| 필드 | 설명 |
|---|---|
| success | 요청 성공 여부 |
| data | 실제 응답 데이터 |
| timestamp | 서버 응답 생성 시각 |

### 오류 응답

오류는 `ErrorResponse` 형식으로 통일한다.

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

입력값 검증 오류는 필드별 상세 내용을 제공한다.

```json
{
  "success": false,
  "code": "COMMON_400",
  "message": "요청 값이 올바르지 않습니다.",
  "errors": [
    {
      "field": "title",
      "rejectedValue": "",
      "reason": "제목은 필수입니다."
    }
  ],
  "path": "/api/todos",
  "timestamp": "2026-10-07T10:00:00"
}
```

현재 오류 코드는 다음과 같다.

| 코드 | HTTP 상태 | 의미 |
|---|---:|---|
| TODO_404 | 404 | 투두를 찾을 수 없음 |
| COMMON_400 | 400 | 잘못된 요청값 |
| PAGE_400 | 400 | 잘못된 페이지 번호 또는 크기 |
| COMMON_405 | 405 | 지원하지 않는 HTTP 메서드 |
| COMMON_500 | 500 | 처리되지 않은 서버 오류 |

## 7. 전역 예외 처리

`GlobalExceptionHandler`에 `@RestControllerAdvice`를 적용했다.

| 예외 | 처리 결과 |
|---|---|
| BusinessException | 정의된 업무 오류 코드와 상태 반환 |
| MethodArgumentNotValidException | 요청 본문의 필드 검증 오류 반환 |
| HandlerMethodValidationException | 쿼리 파라미터 검증 오류 반환 |
| MethodArgumentTypeMismatchException | 날짜, 숫자 등의 타입 변환 오류 반환 |
| HttpRequestMethodNotSupportedException | 405 오류 반환 |
| 기타 Exception | 내부 로그 기록 후 500 오류 반환 |

예상하지 못한 예외의 내부 메시지나 스택 트레이스는 클라이언트에 노출하지 않는다.
서버 로그에는 전체 예외를 기록해 원인을 추적할 수 있게 했다.

## 8. 페이지네이션

페이지네이션은 Spring Data의 `PageRequest`와 `Page`를 사용한다.

```java
PageRequest.of(
    page,
    size,
    Sort.by(
        Sort.Order.desc("todoDate"),
        Sort.Order.desc("createdAt")
    )
)
```

`size`에 최대 100 제한을 둔 이유는 클라이언트가 지나치게 큰 값을 요청해 DB와 서버 메모리를
과도하게 사용하지 못하도록 하기 위해서다.

페이지 응답은 데이터뿐 아니라 현재 페이지, 전체 개수, 전체 페이지 수와 다음 페이지 존재 여부를
제공한다. 클라이언트는 이 정보로 페이지 버튼 또는 무한 스크롤을 구현할 수 있다.

## 9. Redis 캐싱

### 캐싱 대상

전체/날짜별 목록 조회인 `TodoService.findAll()`에 `@Cacheable`을 적용했다.

```java
@Cacheable(
    cacheNames = "todoLists",
    key = "(#date == null ? 'all' : #date.toString()) + ':' + #page + ':' + #size"
)
```

캐시 키에는 조회 결과를 결정하는 모든 조건을 포함한다.

```text
전체 첫 페이지: todoLists::all:0:20
2026-10-06 첫 페이지: todoLists::2026-10-06:0:20
2026-10-06 두 번째 페이지: todoLists::2026-10-06:1:20
```

### Cache Aside 흐름

```text
목록 조회 요청
  → Redis에 동일 키가 있는가?
      ├─ Yes → Redis 결과 반환
      └─ No  → DB 조회 → Redis 저장 → 결과 반환
```

### TTL

캐시는 10분 후 자동 만료된다.

```java
.entryTtl(Duration.ofMinutes(10))
```

TTL은 오래된 데이터가 Redis에 영구적으로 남는 것을 방지하고 메모리 사용량을 제한한다.

### 캐시 무효화

등록·수정·완료 변경·삭제에는 다음 설정을 적용했다.

```java
@CacheEvict(cacheNames = "todoLists", allEntries = true)
```

쓰기 작업 후 기존 목록 캐시를 전부 제거하는 이유는 하나의 투두가 여러 날짜 및 페이지 결과에
영향을 줄 수 있기 때문이다. 과제 규모에서는 조건별 캐시 키를 찾아 선택적으로 제거하는 것보다
전체 제거가 단순하고 정합성도 안전하다.

### 직렬화

Redis 키는 문자열로, 값은 JSON으로 저장한다. Java 기본 직렬화보다 Redis에서 내용을 확인하기
쉽고 클래스 변경에 대한 결합을 줄일 수 있다.

캐시 설정은 `RedisCacheConfig`에서 관리한다.

## 10. 프로파일과 외부 설정

### 공통 설정

`application.yml`에는 환경에 공통인 설정을 둔다.

- 기본 프로파일: `local`
- Open Session In View 비활성화
- Redis 호스트와 포트
- Redis 캐시 활성화
- Actuator 노출 범위

Redis 연결 정보는 환경변수로 덮어쓸 수 있다.

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
```

### local 프로파일

`application-local.yml`은 H2 인메모리 데이터베이스를 사용한다.

- MySQL 호환 모드
- 실행할 때마다 스키마 생성
- SQL 출력 활성화

별도 DB 설치 없이 애플리케이션 동작을 확인하기 위한 설정이다.

### prod 프로파일

`application-prod.yml`은 MySQL 연결 정보를 환경변수에서 받는다.

```text
DB_URL
DB_USERNAME
DB_PASSWORD
REDIS_HOST
REDIS_PORT
```

운영 실행 예시:

```bash
SPRING_PROFILES_ACTIVE=prod \
DB_URL=jdbc:mysql://localhost:3306/todomate \
DB_USERNAME=todomate \
DB_PASSWORD=todomate \
REDIS_HOST=localhost \
./gradlew bootRun
```

운영 프로파일은 Hibernate의 `ddl-auto`를 `validate`로 설정한다. 애플리케이션이 임의로 운영
스키마를 변경하지 않고 엔티티와 기존 스키마가 일치하는지만 확인한다.

## 11. Actuator 헬스체크

다음 엔드포인트를 노출한다.

```http
GET /actuator/health
GET /actuator/info
GET /actuator/metrics
```

대표적인 헬스체크 응답:

```json
{
  "status": "UP"
}
```

배포 환경의 로드밸런서나 모니터링 시스템은 `/actuator/health`를 호출해 애플리케이션 상태를
확인할 수 있다.

## 12. 로컬 실행

### Redis와 MySQL 실행

```bash
docker compose up -d
```

기본 `local` 프로파일은 H2를 사용하므로 투두 API 실행에 MySQL은 필수가 아니다. Redis 캐시를
사용하려면 최소한 Redis 컨테이너는 실행해야 한다.

```bash
docker compose up -d redis
```

### 애플리케이션 실행

```bash
./gradlew bootRun
```

### 테스트

```bash
./gradlew clean test
```

## 13. 구현 시 고려한 사항

- 리소스 이름은 단수 `/api/todo`가 아닌 복수 `/api/todos`를 사용했다.
- API 버저닝은 현재 요구사항에 따라 적용하지 않았다.
- 날짜별 조회는 별도 동사형 URL 대신 컬렉션 필터 조건으로 표현했다.
- 완료 변경은 단순 토글이 아니라 목표 상태를 전달해 재시도에 안전하게 구성했다.
- 엔티티를 API 응답에 직접 노출하지 않고 응답 DTO로 변환했다.
- 읽기 서비스의 기본 트랜잭션을 `readOnly = true`로 설정했다.
- 쓰기 메서드에만 별도 쓰기 트랜잭션을 적용했다.
- 데이터 변경 후 목록 캐시를 제거해 DB와 Redis의 불일치를 방지했다.
- 예외의 내부 정보를 클라이언트에 노출하지 않고 공통 오류 형식으로 변환했다.
- 외부 연결 정보는 소스에 고정하지 않고 환경변수로 주입할 수 있게 구성했다.

## 14. 현재 범위 밖의 작업

다음 항목은 현재 구현 범위에 포함되지 않았다.

- Swagger/OpenAPI 명세
- 인증과 사용자별 투두 분리
- Flyway 또는 Liquibase DB 마이그레이션
- Redis 장애 시 캐시 우회 정책
- Testcontainers를 이용한 Redis/MySQL 통합 테스트
- 캐시 적중률 모니터링

과제에서 API 명세 정리가 필요한 시점에는 현재 구현을 기준으로 Swagger/OpenAPI 또는 별도
API 명세서를 추가할 수 있다.

## 15. 제네릭과 ResponseEntity 이해하기

### 15.1 제네릭이란 무엇인가

제네릭은 클래스가 담을 데이터 타입을 사용하는 시점에 지정하는 기능이다. 다음 공통 응답에서
`T`는 아직 결정되지 않은 데이터 타입을 뜻한다.

```java
public record ApiResponse<T>(
    boolean success,
    T data,
    LocalDateTime timestamp
) {
}
```

투두 한 건을 반환할 때는 `T` 자리에 `TodoResponse`가 들어간다.

```java
ApiResponse<TodoResponse>
```

페이지 목록을 반환할 때는 `T` 자리에 `PageResponse<TodoResponse>`가 들어간다.

```java
ApiResponse<PageResponse<TodoResponse>>
```

제네릭은 하나의 `ApiResponse`를 여러 데이터 타입에 재사용하게 해준다. 또한 컴파일러가
`data`에 올바른 타입이 들어가는지 검사하므로 잘못된 형변환으로 인한 실행 중 오류를 줄인다.

중첩된 제네릭은 안쪽부터 읽으면 이해하기 쉽다.

```text
TodoResponse
→ 투두 한 건

PageResponse<TodoResponse>
→ 투두 여러 건과 페이지 정보

ApiResponse<PageResponse<TodoResponse>>
→ 투두 페이지를 data에 담은 공통 응답
```

### 15.2 ResponseEntity의 역할

`ApiResponse`와 `ResponseEntity`는 역할이 서로 다르다.

- `ApiResponse<T>`는 프로젝트에서 정의한 JSON 본문 형식이다.
- `ResponseEntity<T>`는 HTTP 상태 코드, 헤더, 본문 전체를 표현하는 Spring 클래스다.

다음 반환 타입은 투두 한 건을 공통 JSON 형식에 넣고, 그 위에서 HTTP 상태와 헤더까지 직접
제어한다는 뜻이다.

```java
ResponseEntity<ApiResponse<TodoResponse>>
```

구조를 펼치면 다음과 같다.

```text
ResponseEntity
├── HTTP 상태 코드
├── HTTP 헤더
└── HTTP 본문
    └── ApiResponse
        ├── success
        ├── data: TodoResponse
        └── timestamp
```

### 15.3 생성 API에서 ResponseEntity를 사용한 이유

투두 생성 API는 단순한 `200 OK`가 아니라 리소스가 생성되었다는 의미의 `201 Created`를
반환한다. 또한 새로 만들어진 투두의 주소를 `Location` 헤더에 제공한다.

```java
public ResponseEntity<ApiResponse<TodoResponse>> create(...) {
    TodoResponse response = todoService.create(request);

    return ResponseEntity
        .created(URI.create("/api/todos/" + response.id()))
        .body(ApiResponse.success(response));
}
```

실제 HTTP 응답은 다음과 같다.

```http
HTTP/1.1 201 Created
Location: /api/todos/1
Content-Type: application/json
```

즉, 생성 API는 상태 코드와 헤더를 직접 설정해야 하므로 `ResponseEntity`를 사용했다.

### 15.4 조회 API에서 ResponseEntity를 생략한 이유

목록 조회 API의 반환 타입은 다음과 같다.

```java
public ApiResponse<PageResponse<TodoResponse>> findAll(...)
```

조회 성공은 기본적으로 `200 OK`이며 추가로 설정할 HTTP 헤더가 없다. `@RestController`에서
Java 객체를 직접 반환하면 Spring MVC가 객체를 JSON으로 변환하고 기본 상태 코드인
`200 OK`를 자동으로 설정한다. 따라서 `ResponseEntity`로 한 번 더 감싸지 않아도 완전한
HTTP 응답이 만들어진다.

현재 코드:

```java
return ApiResponse.success(todoService.findAll(date, page, size));
```

다음처럼 작성해도 동작은 같다.

```java
public ResponseEntity<ApiResponse<PageResponse<TodoResponse>>> findAll(...) {
    return ResponseEntity.ok(
        ApiResponse.success(todoService.findAll(date, page, size))
    );
}
```

두 번째 코드는 `200 OK`를 개발자가 명시했을 뿐 실제 HTTP 결과는 동일하다. 현재 구현은
상태나 헤더를 별도로 제어할 필요가 없는 API에서 더 간결한 첫 번째 방식을 사용한다.

### 15.5 두 반환 타입 비교

```java
public ResponseEntity<ApiResponse<TodoResponse>> create(...)
public ApiResponse<PageResponse<TodoResponse>> findAll(...)
```

첫 번째 API는 생성 결과 한 건을 반환하면서 `201 Created`와 `Location` 헤더를 직접 지정한다.
두 번째 API도 HTTP 응답이 맞지만 기본 `200 OK`와 JSON 본문만 필요하므로 Spring의 자동 응답
처리를 사용한다.

| 항목 | 생성 API | 목록 조회 API |
|---|---|---|
| 실제 데이터 | TodoResponse | PageResponse\<TodoResponse\> |
| 공통 JSON 응답 | ApiResponse | ApiResponse |
| HTTP 상태 | 201 Created | 200 OK |
| 별도 HTTP 헤더 | Location | 없음 |
| ResponseEntity | 필요 | 생략 가능 |

정리하면 `ResponseEntity`가 있어야만 HTTP 응답이 되는 것은 아니다. 별도의 상태 코드나 헤더를
직접 설정할 때 사용하며, 기본 `200 OK` 응답에서는 컨트롤러가 객체를 바로 반환해도 된다.
