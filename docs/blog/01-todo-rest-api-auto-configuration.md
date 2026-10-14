Spring Boot에서 학습한 내용을 실제 코드에 적용하기 위해 미니 프로젝트로 투두메이트 클론 API를 구현하게 되었다.

Todo는 등록, 조회, 수정, 삭제만 생각하면 단순한 도메인이다. 그런데 직접 구현해 보니 URI와 HTTP Method, 상태 코드, 환경별 설정처럼 정해야 할 것이 생각보다 많았다.

이번 글에서는 구현 과정에서 공부한 REST API 설계 원칙과 Spring Boot Auto Configuration, 외부 설정과 프로파일을 정리해 보았다.

### 1. REST API란?

**REST**는 `Representational State Transfer`의 약자다.

서버가 관리하는 대상을 리소스(Resource)로 보고, URI로 리소스를 식별하며, HTTP Method로 리소스에 수행할 행위를 표현하는 방식이다.

Todo 한 건을 하나의 리소스로 보면 `/api/todos/1`처럼 URI로 식별할 수 있다.

```text
/api/todos/1
```

같은 URI라도 HTTP Method에 따라 동작이 달라진다.

```http
GET    /api/todos/1
PATCH  /api/todos/1
DELETE /api/todos/1
```

URI에 `getTodo`, `deleteTodo`처럼 동작을 모두 적지 않는다.

```text
URI → 리소스 표현
HTTP Method → 동작 표현
```

```text
URI와 HTTP Method의 역할을 분리
→ API 주소만 봐도 대상과 동작을 구분할 수 있음
```

### 2. REST의 주요 제약 조건

REST는 URI 작성 규칙만을 뜻하지 않는다. 이번에 함께 살펴본 주요 제약 조건은 다음과 같다.

#### Client-Server

클라이언트는 화면과 사용자 상호작용을, 서버는 데이터와 비즈니스 규칙을 담당한다.

```text
클라이언트 → 화면과 사용자 상호작용
서버 → 데이터와 비즈니스 규칙
```

```text
클라이언트와 서버의 역할 분리
→ 서로의 내부 구현을 몰라도 API 계약을 통해 통신 가능
```

#### Stateless

서버는 이전 요청에 저장된 클라이언트 상태에 의존하지 않는다. 각 요청에는 처리에 필요한 정보가 모두 담겨 있어야 한다.

#### Cacheable

응답이 캐시 가능한지 명확해야 한다. 이번 프로젝트에서는 HTTP 캐시가 아니라 Redis에 조회 결과를 저장했다.

```text
같은 조회 조건 → 같은 캐시 데이터 사용
다른 조회 조건 → 다른 캐시 데이터 사용
```

```text
요청 조건이 달라짐
→ 사용해야 하는 캐시 결과도 달라짐
```

#### Uniform Interface

URI, HTTP Method, 상태 코드와 응답 형식을 일관되게 사용한다. 규칙이 일정하면 클라이언트도 API 동작을 예측하기 쉽다.

#### Layered System

클라이언트는 중간에 프록시나 로드밸런서, 게이트웨이가 있더라도 같은 방식으로 서버를 호출할 수 있다.

모든 제약을 형식적으로 맞추기보다는 HTTP의 의미와 일관된 규칙을 지키는 데 초점을 맞췄다.

```text
일관된 규칙 사용
→ 클라이언트가 API의 동작을 쉽게 예측할 수 있음
```

### 3. URI는 동사가 아니라 리소스로 표현한다

URI는 가능하면 동작보다 대상을 나타내는 명사를 사용한다.

```text
/api/createTodo       X
/api/getTodos         X
/api/deleteTodo/1     X
```

```text
/api/todos            O
/api/todos/1          O
```

동작은 HTTP Method가 표현한다.

```http
POST   /api/todos
GET    /api/todos
DELETE /api/todos/1
```

```text
여러 Todo를 포함하는 컬렉션
→ 복수형인 /api/todos 사용
```

### 4. HTTP Method의 의미

#### GET

리소스를 조회한다.

```http
GET /api/todos
GET /api/todos/1
```

GET은 조회에 사용하며 서버 데이터를 변경하지 않는다.

#### POST

POST는 컬렉션에 새로운 리소스를 생성할 때 사용했다.

```http
POST /api/todos
```

```text
POST /api/todos
→ Todo 컬렉션에 새로운 Todo 추가
```

```text
생성 전에는 Todo ID가 없음
→ 컬렉션 URI인 /api/todos로 요청
→ 생성된 ID는 서버가 결정
```

#### PUT과 PATCH

PUT과 PATCH는 모두 수정에 사용하지만 의미가 다르다.

```text
PUT   → 리소스 전체 교체
PATCH → 리소스 일부 변경
```

```text
Todo 전체 교체가 아닌 일부 값 변경
→ PATCH 사용
```

```http
PATCH /api/todos/1
```

```json
{
  "title": "스프링 강의 복습"
}
```

#### DELETE

DELETE는 특정 리소스를 삭제한다.

```http
DELETE /api/todos/1
```

### 5. 멱등성과 완료 상태 변경

**멱등성(Idempotency)**은 같은 요청을 여러 번 실행해도 서버의 최종 상태가 동일한 성질이다.

예를 들어 다음 요청은 여러 번 실행해도 Todo 1번이 삭제된 최종 상태는 같다.

```http
DELETE /api/todos/1
```

완료 처리에도 멱등성을 고려했다. 서버가 현재 상태를 무조건 반전하는 방식이라면 같은 요청을 두 번 보냈을 때 결과가 달라진다.

```text
첫 번째 요청 → false에서 true
두 번째 요청 → true에서 false
```

네트워크 문제로 같은 요청이 다시 전달될 수도 있다.

```text
현재 상태를 반전하는 요청 → 재요청 시 결과가 달라짐
원하는 상태를 직접 전달 → 재요청해도 결과가 같음
```

따라서 현재 값을 토글하라는 명령 대신 원하는 최종 상태를 요청 본문으로 전달했다.

```http
PATCH /api/todos/1/completion
```

```json
{
  "completed": true
}
```

같은 요청을 반복해도 최종 상태는 항상 `true`다.

### 6. Path Variable과 Query Parameter

특정 리소스를 식별할 때는 `Path Variable`을 사용한다.

```http
GET /api/todos/{id}
GET /api/todos/1
```

컬렉션의 검색, 필터, 페이지 조건에는 `Query Parameter`를 사용했다.

```http
GET /api/todos?date=2026-10-06&page=0&size=20
```

`date`는 날짜 필터, `page`는 페이지 번호, `size`는 한 페이지의 데이터 수다.

오늘의 Todo를 위해 별도의 동사형 URI를 만들 수도 있다.

```http
GET /api/todos/today
```

하지만 오늘의 Todo도 결국 Todo 컬렉션을 날짜로 필터링한 결과다.

```text
오늘의 Todo도 별도의 리소스가 아님
→ Todo 컬렉션에 오늘 날짜를 조회 조건으로 전달
```

```http
GET /api/todos?date=2026-10-08
```

### 7. HTTP 상태 코드

응답 본문뿐 아니라 HTTP 상태 코드로 요청 처리 결과를 표현한다.

이번 API에서 사용한 주요 상태 코드는 다음과 같다.

| 상태 코드 | 의미 | 사용 예시 |
|---:|---|---|
| 200 OK | 요청 성공 | 조회, 수정, 삭제 |
| 201 Created | 리소스 생성 성공 | Todo 등록 |
| 400 Bad Request | 잘못된 요청 | 검증 실패, 잘못된 페이지 |
| 404 Not Found | 리소스 없음 | 존재하지 않는 Todo |
| 405 Method Not Allowed | 지원하지 않는 Method | 잘못된 HTTP Method |
| 500 Internal Server Error | 예상하지 못한 서버 오류 | 공통 서버 오류 |

Todo 생성에서는 `201 Created`와 함께 생성된 리소스 주소를 `Location` 헤더로 반환했다.

```java
return ResponseEntity
        .created(URI.create("/api/todos/" + response.id()))
        .body(ApiResponse.success(response));
```

```http
HTTP/1.1 201 Created
Location: /api/todos/1
```

### 8. ResponseEntity와 응답 객체의 차이

`ResponseEntity`는 HTTP 상태 코드와 헤더, 본문을 직접 제어하는 Spring 타입이다.

```java
ResponseEntity<ApiResponse<TodoResponse>>
```

```text
생성 API
→ 201 Created와 Location 헤더를 직접 설정해야 함
→ ResponseEntity 사용
```

반면 조회 API는 기본 `200 OK`와 JSON 본문만 필요하다.

```java
public ApiResponse<PageResponse<TodoResponse>> findAll(...) {
    return ApiResponse.success(
            todoService.findAll(date, page, size)
    );
}
```

`@RestController`에서 객체를 반환하면 Spring MVC가 JSON 본문으로 변환하고 기본 상태 코드인 `200 OK`를 설정한다.

즉, `ResponseEntity`가 있어야만 HTTP 응답이 되는 것은 아니다.

```text
상태 코드나 헤더를 직접 설정해야 함 → ResponseEntity 사용
기본 200 OK와 본문만 필요함 → 응답 객체를 바로 반환
```

```text
API마다 필요한 HTTP 응답 설정이 다름
→ 반환 방식도 달라질 수 있음
```

### 9. 공통 성공 응답과 오류 응답

```text
API마다 응답 형식이 다름
→ 클라이언트가 API별 처리 방식을 따로 작성해야 함

성공·오류 응답 형식 통일
→ 같은 방식으로 응답 처리 가능
```

성공 응답은 `ApiResponse<T>`로 통일했다.

```java
public record ApiResponse<T>(
        boolean success,
        T data,
        LocalDateTime timestamp
) {
}
```

제네릭 `T`에는 API가 실제로 반환할 타입이 들어간다.

```java
ApiResponse<TodoResponse>
ApiResponse<PageResponse<TodoResponse>>
ApiResponse<Void>
```

`ApiResponse<TodoResponse>`는 Todo 한 건을, `ApiResponse<PageResponse<TodoResponse>>`는 Todo 목록과 페이지 정보를 담는다.

오류도 별도의 공통 형식으로 반환했다.

```json
{
  "success": false,
  "code": "TODO_404",
  "message": "투두를 찾을 수 없습니다.",
  "errors": [],
  "path": "/api/todos/999",
  "timestamp": "2026-10-08T10:00:00"
}
```

`@RestControllerAdvice`를 사용해 오류 코드와 HTTP 상태를 한곳에서 정하도록 했다.

```text
컨트롤러마다 같은 try-catch 작성
→ 중복 발생

공통 예외 처리 적용
→ 오류 응답을 한곳에서 관리
```

### 10. 페이지네이션은 왜 필요할까?

데이터를 한 번에 모두 조회하면 DB와 서버가 처리할 양이 늘고 응답도 커진다.

```text
전체 데이터 조회 → 처리량과 응답 크기 증가
페이지 단위 조회 → 필요한 범위만 처리
```

따라서 조회 API는 페이지 번호와 크기를 받도록 했다.

```http
GET /api/todos?page=0&size=20
```

Spring Data의 페이지 번호는 0부터 시작한다.

`page=0`이 첫 번째 페이지이고 `page=1`이 두 번째 페이지다.

페이지 조건과 정렬 조건은 `Pageable`로 전달한다.

```java
Pageable pageable = PageRequest.of(
        page,
        size,
        Sort.by(
                Sort.Order.desc("todoDate"),
                Sort.Order.desc("createdAt")
        )
);
```

```text
size는 최대 100으로 제한
→ 한 번에 지나치게 많은 데이터를 조회하는 요청 방지
```

Repository는 `Page<Todo>`를 반환한다.

```java
Page<Todo> findAllByTodoDate(
        LocalDate todoDate,
        Pageable pageable
);
```

`Pageable`은 페이지 번호와 크기, 정렬 조건을 전달하고 `Page<Todo>`는 현재 페이지의 Todo와 전체 개수, 전체 페이지 수를 담는다.

`List<Todo>`가 현재 조회된 데이터만 가진다면 `Page<Todo>`에는 다음 정보도 포함된다.

- 전체 데이터 수
- 전체 페이지 수
- 첫 번째·마지막 페이지 여부
- 다음 페이지 존재 여부


### 11. API 버저닝

API가 변경되면 기존 클라이언트와 새로운 클라이언트가 서로 다른 계약을 필요로 할 수 있다.

대표적인 URI 버저닝은 다음과 같다.

```text
/api/v1/todos
/api/v2/todos
```

```text
기존 클라이언트 → v1 사용
새로운 클라이언트 → v2 사용
```

버저닝을 적용하면 기존 API를 유지하면서 새로운 응답 구조를 제공할 수 있다.

다만 버전이 늘어날수록 유지해야 할 코드와 문서도 증가한다.

이번 프로젝트는 단일 클라이언트를 사용하는 학습용 미니 프로젝트라 기존 버전과의 호환 요구사항이 없었다.

```text
별도로 유지해야 하는 이전 API 없음
→ 현재는 버저닝을 적용하지 않음
```

버전이 없는 `/api/todos`를 사용한 이유다.

```text
/api/todos
```

### 12. Spring Boot Auto Configuration이란?

먼저 자동 구성이 왜 필요한지부터 살펴봤다.

웹 서버에서 JPA를 사용하려면 단순히 Repository 인터페이스만 작성한다고 바로 DB에 접근할 수 있는 것은 아니다.

애플리케이션 안에는 다음과 같은 객체들이 준비되어 있어야 한다.

```text
DB 접속 정보를 가진 DataSource
JPA를 실행하는 EntityManagerFactory
트랜잭션을 관리하는 TransactionManager
HTTP 요청을 Controller로 전달하는 DispatcherServlet
Java 객체를 JSON으로 바꾸는 MessageConverter
```

**Spring Framework**에서는 이런 객체들을 개발자가 직접 Bean으로 등록하고 서로 연결할 수 있다.

하지만 대부분의 웹 프로젝트에서 비슷한 설정이 반복된다.

```text
JPA를 사용할 때마다 DataSource와 JPA 실행 환경 설정
웹을 사용할 때마다 DispatcherServlet과 JSON 변환 설정
Redis를 사용할 때마다 연결 객체 설정
```

Spring Boot는 이런 반복 설정에 **일반적으로 사용할 수 있는 기본값**을 제공한다.

개발자가 사용하려는 라이브러리와 설정값을 확인한 뒤, 실행에 필요한 Bean을 대신 준비해 주는 기능이 `Auto Configuration`이다.

```text
프로젝트마다 비슷한 설정이 반복됨
→ Spring Boot가 기본 설정과 Bean을 대신 구성
```

```text
개발자가 하는 일
→ 사용할 Starter 추가
→ 필요한 접속 정보 작성

Spring Boot가 하는 일
→ 현재 사용할 수 있는 라이브러리 확인
→ 설정값 확인
→ 필요한 Bean 자동 등록
→ 서로 연결
```

예를 들어 개발자는 다음 Repository 인터페이스만 작성했다.

```java
public interface TodoRepository
        extends JpaRepository<Todo, Long> {
}
```

하지만 내부적으로는 DB 연결과 JPA 실행에 필요한 객체가 존재해야 한다.

```text
Repository 인터페이스만 작성
→ Spring Data JPA가 구현체 생성
→ Spring Boot가 실행에 필요한 환경 구성
→ 별도의 구현 클래스 없이 Repository 사용 가능
```

자동 구성의 목적은 개발자가 모든 설정을 직접 만들지 않게 하는 것이다.

```text
공통적인 기본 설정
→ Spring Boot가 제공

프로젝트마다 다른 정책
→ 개발자가 직접 설정
```

프로젝트에는 다음 Starter 의존성이 포함되어 있다.

```groovy
implementation 'org.springframework.boot:spring-boot-starter-webmvc'
implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
implementation 'org.springframework.boot:spring-boot-starter-data-redis'
implementation 'org.springframework.boot:spring-boot-starter-actuator'
```

Starter를 추가하면 관련 라이브러리들이 클래스패스에 들어온다.

Spring Boot는 다음 정보를 기준으로 필요한 자동 구성을 결정한다.

```text
클래스패스에 어떤 라이브러리가 있는가?
사용자가 어떤 Bean을 직접 등록했는가?
외부 설정에 어떤 값이 있는가?
현재 실행 중인 애플리케이션 종류는 무엇인가?
```

### 13. @SpringBootApplication의 역할

애플리케이션 시작 클래스에는 `@SpringBootApplication`을 사용한다.

```java
@SpringBootApplication
public class TodomateApplication {
    public static void main(String[] args) {
        SpringApplication.run(
                TodomateApplication.class,
                args
        );
    }
}
```

`@SpringBootApplication`은 다음 기능을 묶은 어노테이션이다.

```text
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan
```

#### @SpringBootConfiguration

해당 클래스가 Spring Boot의 설정 클래스임을 나타낸다.

#### @EnableAutoConfiguration

클래스패스와 설정값을 기준으로 자동 구성을 활성화한다.

#### @ComponentScan

시작 클래스가 위치한 패키지와 하위 패키지에서 Component를 탐색한다.

```text
@Controller
@Service
@Repository
@Configuration
@Component
```

```text
시작 클래스를 최상위 패키지에 배치
→ 하위 패키지의 Component를 모두 탐색 가능
```

### 14. 자동 구성은 조건에 따라 동작한다

**자동 구성은 모든 Bean을 무조건 등록하지 않는다.**

대표적인 조건은 다음과 같다.

```text
@ConditionalOnClass
→ 특정 클래스가 클래스패스에 있을 때

@ConditionalOnMissingBean
→ 같은 역할의 Bean을 사용자가 등록하지 않았을 때

@ConditionalOnProperty
→ 특정 외부 설정값을 만족할 때

@ConditionalOnWebApplication
→ 웹 애플리케이션일 때
```

예를 들어 Redis Starter와 접속 설정이 있으면 Spring Boot가 `RedisConnectionFactory`를 자동 구성한다.

```text
spring-data-redis가 클래스패스에 존재
  +
spring.data.redis 설정 존재
  ↓
RedisConnectionFactory 자동 등록
```

JPA도 비슷한 흐름으로 동작한다.

```text
JPA와 JDBC 관련 클래스 존재
  +
DataSource 설정 존재
  ↓
DataSource
EntityManagerFactory
TransactionManager
자동 구성
```

개발자가 같은 역할의 Bean을 직접 등록하면 자동 구성이 물러나는 `Back-off`가 적용될 수 있다.

```text
Spring Boot가 기본값 제공
  ↓
개발자가 직접 Bean 등록
  ↓
사용자 설정을 우선 사용
```

즉, 자동 구성은 설정 자체를 없애는 기능이 아니다.

```text
반복되는 기본 설정 → Spring Boot가 구성
프로젝트에 필요한 설정 → 개발자가 변경
```

```text
기본 설정은 자동 구성에 맡김
→ 프로젝트에 필요한 설정만 직접 작성
```

### 15. 프로젝트에서 확인한 자동 구성

#### Spring MVC

Web MVC Starter를 추가해 내장 Tomcat, DispatcherServlet, JSON 메시지 변환 등이 구성된다.

```text
HTTP 요청
  ↓
Tomcat
  ↓
DispatcherServlet
  ↓
TodoController
```

정적 리소스 경로의 `index.html`도 기본 설정에 따라 `/`에서 제공된다.

#### Spring Data JPA

JPA Starter와 DataSource 설정으로 JPA 실행 환경이 구성된다.

`TodoRepository`는 인터페이스만 작성했다.

```java
public interface TodoRepository
        extends JpaRepository<Todo, Long> {
}
```

구현 클래스를 직접 작성하지 않아도 Spring Data JPA가 프록시 구현체를 생성해 Bean으로 등록한다.

#### Redis

Redis 접속 정보로 `RedisConnectionFactory`가 자동 구성된다.

프로젝트에서는 이 연결 객체를 주입받아 캐시 정책이 담긴 `RedisCacheManager`를 직접 등록했다.

```java
@Bean
public CacheManager redisCacheManager(
        RedisConnectionFactory connectionFactory
) {
    return RedisCacheManager
            .builder(connectionFactory)
            .cacheDefaults(configuration)
            .build();
}
```

자동 구성과 사용자 설정을 함께 활용한 사례다.

```text
Redis 연결 생성
→ Spring Boot 자동 구성

TTL과 JSON 직렬화 정책
→ 프로젝트에서 직접 구성
```

#### Actuator

Actuator Starter와 노출 설정을 기반으로 헬스체크 엔드포인트가 등록된다.

```http
GET /actuator/health
```

별도의 헬스체크 컨트롤러를 만들지 않아도 된다.

### 16. 외부 설정이 필요한 이유

자동 구성이 “필요한 객체를 누가 만들어 주는가”에 관한 내용이라면, **외부 설정은 “그 객체를 어떤 값으로 만들 것인가”에 관한 내용이다.**
예를 들어 Spring Boot가 DataSource를 자동으로 만들 수 있어도 접속할 DB 주소, 사용자 이름, 비밀번호는 스스로 알 수 없다.

```text
Spring Boot
→ DataSource를 만드는 방법은 알고 있음

개발자 또는 실행 환경
→ 어느 DB에 접속할지 값을 제공해야 함
```

이 값을 Java 코드 안에 직접 작성한다고 가정해 보자.

```java
String databaseUrl = "jdbc:mysql://localhost:3306/todomate";
String databasePassword = "1234";
```

로컬에서는 동작할 수 있지만 운영 DB 주소가 달라지면 코드를 수정해야 한다.

설정 하나를 변경하기 위해 소스 코드를 수정하고 다시 빌드해야 하며, 비밀번호가 Git에 올라갈 위험도 있다.

그래서 **실행 환경마다 달라지는 값은 코드에서 분리**한다.

```text
Java 코드
→ 모든 환경에서 동일하게 사용

외부 설정
→ 실행 환경에 따라 다른 값 사용
```

```text
코드와 환경별 설정을 분리
→ 애플리케이션을 다시 수정하지 않고 설정만 바꿔 실행 가능
```

```text
같은 애플리케이션 JAR
  ├─ 로컬 DB 설정으로 실행
  ├─ 테스트 DB 설정으로 실행
  └─ 운영 DB 설정으로 실행
```

Spring Boot는 여러 외부 설정 방식을 제공한다.

```text
application.yml
프로파일별 application-{profile}.yml
환경변수
Java 시스템 속성
명령행 인자
```

같은 설정이 여러 위치에 있으면 우선순위가 높은 값이 낮은 값을 덮어쓴다.

예를 들어 `application.yml`의 기본값을 환경변수로 변경할 수 있다.

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
```

```text
REDIS_HOST 환경변수 있음
→ 환경변수 사용

REDIS_HOST 환경변수 없음
→ localhost 사용
```

### 17. 프로파일로 환경별 설정 분리

외부 설정을 사용하더라도 모든 환경의 값을 한 파일에 섞어 두면 로컬용과 운영용 설정을 구분하기 어렵다.

```text
환경별 설정이 한 파일에 섞임
→ 어떤 환경의 값인지 구분하기 어려움
→ 프로파일별 설정 파일로 분리
```

프로파일은 환경별 설정 묶음에 이름을 붙이고, 실행할 때 필요한 묶음을 선택하는 기능이다.

```text
local 프로파일
→ 개발자 컴퓨터에서 사용할 설정 묶음

prod 프로파일
→ 운영 서버에서 사용할 설정 묶음
```

Java 코드 안에서 환경을 구분할 수도 있지만, 환경이 추가될 때마다 조건문과 수정할 코드가 늘어난다.

```text
Java 코드에서 환경 구분
→ 환경이 추가될 때마다 코드 수정

프로파일로 환경 구분
→ Java 코드는 유지하고 설정 파일만 선택
```

```text
동일한 Java 코드
  + local 프로파일
  → H2 기반 로컬 애플리케이션

동일한 Java 코드
  + prod 프로파일
  → MySQL 기반 운영 애플리케이션
```

이번 프로젝트에서는 `local`과 `prod`를 구분했다.

공통 설정은 `application.yml`에 둔다.

```yaml
spring:
  application:
    name: todomate
  profiles:
    default: local
  jpa:
    open-in-view: false
```

#### local 프로파일

로컬에서는 별도 DB 설치 없이 실행할 수 있도록 H2를 사용한다.

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:todomate;MODE=MySQL
    username: sa
    password:
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
```

```text
H2 인메모리 DB
→ 실행이 간단함
→ 서버 종료 시 데이터 초기화

ddl-auto: create-drop
→ 시작할 때 테이블 생성
→ 종료할 때 테이블 삭제

show-sql: true
→ 실행된 SQL 확인
```

#### prod 프로파일

운영에서는 MySQL과 환경변수를 사용한다.

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: validate
```

```text
DB 접속 정보
→ 코드에 저장하지 않고 환경변수 사용

ddl-auto: validate
→ 운영 스키마를 자동 변경하지 않음
→ 엔티티와 스키마가 일치하는지만 확인
```

운영 프로파일은 다음과 같이 활성화할 수 있다.

```bash
SPRING_PROFILES_ACTIVE=prod \
DB_URL=jdbc:mysql://localhost:3306/todomate \
DB_USERNAME=todomate \
DB_PASSWORD=secret \
./gradlew bootRun
```

`@Profile`을 사용해 특정 환경에서만 Bean을 등록할 수도 있다.

```java
@Component
@Profile("local")
public class LocalTodoDataInitializer {
}
```

페이지네이션 확인용 테스트 데이터는 local에서만 생성되고 prod에서는 등록되지 않는다.

### 18. Auto Configuration과 외부 설정의 관계

지금까지의 내용을 실제 실행 순서로 연결하면 이해하기 쉽다.

먼저 Starter 의존성은 애플리케이션이 어떤 기술을 사용할 수 있는지 알려 준다.

```groovy
implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
```

```text
JPA Starter
→ 이 애플리케이션은 JPA를 사용할 수 있음
```

프로파일은 이번 실행에서 사용할 설정 묶음을 선택한다.

```text
local 활성화
→ application-local.yml 선택
```

외부 설정은 선택된 환경에서 실제로 사용할 값을 제공한다.

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:todomate
```

마지막으로 Auto Configuration이 의존성과 설정값을 확인하고 실제 Bean을 만든다.

```text
JPA 관련 클래스가 있음
  +
local 프로파일의 H2 설정이 있음
  ↓
H2 DataSource 생성
  ↓
EntityManagerFactory 생성
  ↓
TransactionManager 생성
  ↓
TodoRepository 사용 가능
```

각 역할은 다음처럼 나눌 수 있다.

```text
Starter
→ 무엇을 사용할 것인가?

프로파일
→ 어느 환경의 설정을 선택할 것인가?

외부 설정
→ 실제로 어떤 값을 사용할 것인가?

Auto Configuration
→ 위 정보를 바탕으로 어떤 Bean을 만들 것인가?
```

```text
Starter + 프로파일 + 외부 설정 + Auto Configuration
→ 환경마다 인프라 객체를 처음부터 다시 조립할 필요가 없음
```

예를 들어 DataSource 구성은 다음 흐름으로 이해할 수 있다.

```text
JDBC와 JPA 클래스가 존재하는가?
  ↓ Yes
DataSource 관련 Bean이 이미 있는가?
  ↓ No
spring.datasource 설정은 무엇인가?
  ↓
해당 URL과 사용자 정보로 DataSource 구성
```

프로파일이 달라지면 Auto Configuration이 읽는 설정값도 달라진다.

```text
local
→ H2 DataSource 자동 구성

prod
→ MySQL DataSource 자동 구성
```

코드는 같지만 활성화한 프로파일과 외부 설정에 따라 실제로 만들어지는 인프라 객체는 달라진다.

### 19. 마무리

이번 프로젝트를 통해 REST API는 URI만 정하면 끝나는 것이 아니라는 점을 알게 됐다.

```text
REST API → HTTP Method, 상태 코드, 응답 형식까지 하나의 약속
자동 구성과 프로파일 → 같은 코드를 환경에 맞게 실행
```

다음 글에서는 Redis 캐시와 Actuator 헬스체크 적용 과정을 정리한다.
