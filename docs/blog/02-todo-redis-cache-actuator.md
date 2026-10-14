Todo 조회 API에 Redis 캐시 적용하기 — 캐시 키, 무효화와 Actuator 헬스체크

이전 글에서는 Todo REST API를 설계하고 공통 응답, 페이지네이션, 프로파일 분리를 적용했다.

이번에는 같은 날짜와 페이지를 반복 조회할 때마다 DB에 동일한 쿼리가 실행되는 문제를 개선했다.

```text
Todo 목록 조회 → Redis 캐싱
데이터 변경 → 기존 캐시 제거
애플리케이션 상태 확인 → Actuator 헬스체크
```

### 1. 어떤 데이터를 캐싱할까?

현재 목록 API는 날짜와 페이지 조건으로 Todo를 조회한다.

```http
GET /api/todos?date=2026-10-06&page=0&size=20
```

같은 조건으로 요청이 반복되면 매번 동일한 DB 조회가 실행될 수 있다.

```text
Client
  ↓
TodoService.findAll()
  ↓
TodoRepository
  ↓
Database
```

Todo 목록은 등록, 수정, 삭제보다 조회 빈도가 높고, 날짜와 페이지 조건이 같으면 같은 결과를 반복해서 사용할 수 있다.

```text
같은 조건의 목록 조회 반복
→ 동일한 DB 쿼리 발생
→ 목록 조회 결과를 캐싱
```

단건 조회는 이번 범위에서 캐싱하지 않았다.

```text
여러 캐시를 한 번에 관리하지 않음
→ 과제의 핵심인 목록 캐싱에 집중
→ 캐시 무효화 지점도 단순하게 유지
```

### 2. Redis 실행하기

로컬 Redis는 Docker Compose로 실행했다.

```yaml
services:
  redis:
    image: redis:7.4-alpine
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 5
```

실행 명령은 다음과 같다.

```bash
docker compose up -d redis
```

명령을 나누어 보면 다음과 같다.

```text
docker compose → docker-compose.yml 사용
up             → 서비스 생성 및 실행
-d             → 백그라운드 실행
redis          → redis 서비스만 실행
```

정상 실행 여부는 다음 명령으로 확인할 수 있다.

```bash
docker compose exec redis redis-cli ping
```

```text
PONG
```

처음 실행했을 때 다음 오류가 발생하기도 했다.

```text
Cannot connect to the Docker daemon
```

```text
Docker 명령은 설치됨
→ Docker Desktop이 실행되지 않아 daemon 연결 실패
```

```bash
open -a Docker
```

Docker Desktop 실행이 완료된 후 Redis 컨테이너를 다시 실행했다.

### 3. Redis 의존성과 외부 설정

Redis와 Spring Cache를 사용하기 위해 다음 의존성을 추가했다.

```groovy
implementation 'org.springframework.boot:spring-boot-starter-data-redis'
implementation 'org.springframework.boot:spring-boot-starter-cache'
```

Redis 접속 정보는 `application.yml`에 작성했다.

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      repositories:
        enabled: false
  cache:
    type: redis
```

```text
REDIS_HOST 환경변수 없음 → localhost
REDIS_PORT 환경변수 없음 → 6379
운영 환경 → 환경변수로 연결 정보 변경
```

```text
Redis를 Spring Data Repository로 사용하지 않음
→ Redis Repository 탐색 비활성화
→ 캐시 저장소로만 사용
```

### 4. Redis 구성 요소의 역할

Spring Data Redis를 추가하면 여러 구성 요소를 사용할 수 있다.

```text
RedisConnectionFactory
RedisTemplate
StringRedisTemplate
RedisCacheManager
```

이번 프로젝트에서 직접 사용한 것은 `RedisConnectionFactory`와 `RedisCacheManager`다.

전체 연결 구조는 다음과 같다.

```text
TodoService
  ↓ @Cacheable, @CacheEvict
RedisCacheManager
  ↓
RedisConnectionFactory
  ↓
Redis Server
```

#### RedisConnectionFactory

Redis 서버와 실제 연결을 생성한다.

Spring Boot가 `spring.data.redis` 설정을 읽어 자동으로 등록한다.

```text
RedisConnectionFactory
→ Redis 서버로 접근할 연결 통로
```

#### RedisCacheManager

Spring Cache와 Redis를 연결한다.

`@Cacheable`, `@CacheEvict`가 요청한 캐시 조회, 저장, 삭제 작업을 처리한다.

```text
RedisCacheManager
→ 어떤 캐시에서 값을 조회·저장·삭제할지 관리
```

#### RedisTemplate과 StringRedisTemplate

두 객체는 Redis 데이터를 코드에서 직접 조작할 때 사용한다.

```java
stringRedisTemplate.opsForValue()
        .set("hello", "world");
```

```text
현재 프로젝트 → Spring Cache 추상화 사용
Redis 직접 조작 → 필요하지 않음
RedisTemplate, StringRedisTemplate → 사용하지 않음
```

### 5. RedisCacheManager 설정

캐시 기능을 활성화하고 Redis 설정을 적용했다.

```java
@EnableCaching
@Configuration
public class RedisCacheConfig {

    @Bean
    public CacheManager redisCacheManager(
            RedisConnectionFactory connectionFactory
    ) {
        RedisCacheConfiguration configuration =
                RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofMinutes(10))
                        .disableCachingNullValues()
                        .serializeKeysWith(
                                RedisSerializationContext.SerializationPair
                                        .fromSerializer(new StringRedisSerializer())
                        )
                        .serializeValuesWith(
                                RedisSerializationContext.SerializationPair
                                        .fromSerializer(RedisSerializer.json())
                        );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(configuration)
                .build();
    }
}
```

#### @EnableCaching

Spring Cache 기능을 활성화한다.

```text
@EnableCaching
→ @Cacheable, @CacheEvict 기반의 캐시 기능 활성화
```

#### TTL 10분

```java
.entryTtl(Duration.ofMinutes(10))
```

캐시가 저장된 후 10분이 지나면 자동으로 만료된다.

```text
캐시 저장 후 10분 경과
→ 캐시 자동 만료
→ 오래된 데이터와 불필요한 메모리 점유 방지
```

#### null 캐싱 방지

```java
.disableCachingNullValues()
```

메서드 결과가 `null`이면 Redis에 저장하지 않는다.

빈 페이지 응답은 객체가 존재하고 `content`가 비어 있는 것이므로 `null`과는 다르다.

#### 문자열 키와 JSON 값

```java
.serializeKeysWith(...StringRedisSerializer...)
.serializeValuesWith(...RedisSerializer.json()...)
```

키는 사람이 읽을 수 있는 문자열로 저장한다.

값은 `PageResponse<TodoResponse>` 객체를 JSON으로 변환해 저장한다.

### 6. @Cacheable로 목록 조회 캐싱

Todo 목록 조회 메서드에 `@Cacheable`을 적용했다.

```java
@Cacheable(
        cacheNames = "todoLists",
        key = "(#date == null ? 'all' : #date.toString())"
                + " + ':' + #page + ':' + #size"
)
public PageResponse<TodoResponse> findAll(
        LocalDate date,
        int page,
        int size
) {
    Pageable pageable = PageRequest.of(page, size);

    Page<Todo> todos = date == null
            ? todoRepository.findAll(pageable)
            : todoRepository.findAllByTodoDate(date, pageable);

    return PageResponse.from(todos, TodoResponse::from);
}
```

첫 번째 요청에는 캐시가 없다.

```text
Redis에서 캐시 키 조회
  ↓
Cache Miss
  ↓
findAll() 실행
  ↓
DB 조회
  ↓
반환값을 Redis에 저장
  ↓
응답
```

같은 조건의 두 번째 요청에서는 캐시가 존재한다.

```text
Redis에서 캐시 키 조회
  ↓
Cache Hit
  ↓
findAll() 실행 생략
  ↓
DB 조회 생략
  ↓
Redis 결과 반환
```

```text
Cache Hit
→ findAll() 내부 코드 실행 안 함
→ DB 조회도 실행 안 함
```

### 7. 캐시 키는 어떻게 설계할까?

캐시 키 형식은 정해져 있지 않다.

```text
같은 조회 결과 → 같은 키
다른 조회 결과 → 다른 키
```

현재 조회 결과에 영향을 주는 조건은 다음 세 가지다.

```text
date → 조회할 날짜
page → 페이지 번호
size → 페이지 크기
```

따라서 세 값을 모두 캐시 키에 포함했다.

```text
todoLists::all:0:20
todoLists::2026-10-06:0:20
todoLists::2026-10-06:1:20
todoLists::2026-10-06:0:50
```

만약 페이지 번호를 키에서 제외하면 문제가 발생한다.

```text
첫 번째 페이지 요청 → 2026-10-06:20
두 번째 페이지 요청 → 2026-10-06:20
```

```text
서로 다른 페이지에 같은 키 생성
→ 두 번째 페이지를 요청해도 첫 번째 페이지의 캐시가 반환될 수 있음
```

좋은 캐시 키는 다음 조건을 만족해야 한다.

- 같은 조회 조건에서는 항상 같은 키가 생성되어야 한다.
- 조회 결과가 다르면 다른 키가 생성되어야 한다.
- 결과에 영향을 주는 조건을 빠짐없이 포함해야 한다.
- 비밀번호나 토큰 같은 민감한 값을 포함하지 않아야 한다.
- 가능하면 사람이 의미를 파악할 수 있어야 한다.

### 8. null 캐시 키 오류 해결

처음에는 날짜를 문자열로 바꾸기 위해 다음 SpEL을 사용했다.

```java
key = "T(java.lang.String).valueOf(#date)"
        + " + ':' + #page + ':' + #size"
```

날짜가 있을 때는 정상적인 키가 생성됐다.

```text
2026-10-06:0:20
```

그러나 날짜 없이 전체 목록을 조회했을 때 `500 Internal Server Error`가 발생했다.

로그에는 다음 오류가 기록됐다.

```text
NullPointerException
at java.lang.String.valueOf(...)
at CacheOperationExpressionEvaluator.key(...)
```

SpEL이 `null` 값을 처리하는 과정에서 `String.valueOf()`의 오버로드를 기대와 다르게 선택한 것이 원인이었다.

날짜가 없는 경우를 명시적으로 처리하도록 변경했다.

```java
key = "(#date == null ? 'all' : #date.toString())"
        + " + ':' + #page + ':' + #size"
```

최종 키는 다음과 같다.

```text
날짜 없음 → todoLists::all:0:20
날짜 있음 → todoLists::2026-10-06:0:20
```

```text
date는 선택값
→ null인 경우까지 캐시 키에서 처리해야 함
```

### 9. 데이터 변경 후 캐시 무효화

캐시를 적용하면 DB와 Redis에 데이터가 동시에 존재한다.

```text
Database → 최신 Todo 목록
Redis    → 이전에 조회한 Todo 목록
```

```text
Todo 변경 후 기존 캐시 유지
→ DB와 Redis의 데이터가 달라짐
→ 이전 목록이 반환될 수 있음
```

등록, 수정, 완료 변경, 삭제에는 `@CacheEvict`를 적용했다.

```java
@Transactional
@CacheEvict(
        cacheNames = "todoLists",
        allEntries = true
)
public TodoResponse update(...) {
    // Todo 수정
}
```

데이터 변경 후의 흐름은 다음과 같다.

```text
Todo 변경
  ↓
DB 반영
  ↓
todoLists 캐시 전체 삭제
  ↓
다음 목록 조회에서 Cache Miss
  ↓
DB의 최신 목록 조회
  ↓
Redis에 다시 저장
```

`allEntries = true`는 `todoLists`에 포함된 모든 키를 제거한다.

```text
todoLists::all:0:20
todoLists::2026-10-06:0:20
todoLists::2026-10-06:1:20
```

```text
Todo 한 건 변경
→ 전체 조회 캐시에 영향
→ 날짜별·페이지별 캐시에도 영향
```

과제 규모에서는 관련 키를 하나씩 계산하는 것보다 목록 캐시 전체를 삭제하는 쪽이 단순하다고 판단했다.

데이터와 요청량이 많아지면 선택적인 캐시 무효화 또는 다른 캐시 구조를 검토해야 한다.

### 10. 페이지네이션 테스트 데이터 생성

페이지네이션을 확인하려면 한 페이지 크기보다 많은 데이터가 필요하다.

매번 직접 입력하지 않도록 local 프로파일에서 테스트 Todo 65개를 자동 생성했다.

```java
@Component
@Profile("local")
@ConditionalOnProperty(
        name = "app.seed.enabled",
        havingValue = "true"
)
public class LocalTodoDataInitializer
        implements ApplicationRunner {
}
```

```text
@Profile("local")
→ local 환경에서만 테스트 데이터 생성
→ 운영 환경에서는 실행되지 않음
```

설정으로도 실행 여부를 결정할 수 있다.

```yaml
app:
  seed:
    enabled: true
```

테스트 데이터가 필요하지 않으면 `false`로 변경한다.

```text
서버 시작
  ↓
local 프로파일인지 확인
  ↓
app.seed.enabled=true 확인
  ↓
기존 데이터가 없는지 확인
  ↓
Todo 65개 저장
  ↓
이전 Redis 목록 캐시 삭제
```

```text
애플리케이션 재실행 → H2 데이터 초기화
Redis 재실행 안 함 → 이전 목록 캐시가 남을 수 있음
테스트 데이터 생성 완료 → todoLists 캐시도 함께 제거
```

### 11. Actuator 헬스체크

애플리케이션이 실행 중인지 확인하기 위해 Actuator를 사용했다.

```groovy
implementation 'org.springframework.boot:spring-boot-starter-actuator'
```

노출할 엔드포인트를 설정한다.

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: when_authorized
```

헬스체크 주소는 다음과 같다.

```http
GET /actuator/health
```

정상 응답:

```json
{
  "groups": [
    "liveness",
    "readiness"
  ],
  "status": "UP"
}
```

각 상태의 의미는 다음과 같다.

- `UP`: 애플리케이션이 정상 동작 중이다.
- `liveness`: 애플리케이션 프로세스가 살아 있는지 확인한다.
- `readiness`: 현재 요청을 받을 준비가 되었는지 확인한다.

```text
Actuator Starter 추가
→ Spring Boot Auto Configuration 동작
→ Health Endpoint 자동 등록
→ 별도의 헬스체크 컨트롤러가 필요하지 않음
```

배포 환경에서는 로드밸런서나 모니터링 시스템이 이 주소를 호출해 애플리케이션 상태를 확인할 수 있다.

### 12. Redis를 적용할 때 함께 고려할 것 ⭐️

#### 데이터 일관성

```text
DB 변경 성공 + 캐시 제거 실패
→ Redis에 오래된 값이 남을 수 있음
```

DB 작업과 Redis 작업이 항상 하나의 트랜잭션으로 처리되는 것은 아니다.

#### Redis 장애

```text
Redis 장애
→ 현재 구현에서는 목록 조회도 실패할 수 있음
→ 운영 환경에서는 타임아웃, DB fallback 등을 검토해야 함
```

#### 캐시 무효화 범위

```text
allEntries = true
→ 구현은 단순함
→ 데이터가 많아지면 불필요한 캐시까지 삭제할 수 있음
```

서비스 규모가 커지면 변경된 데이터와 관련된 키만 제거하는 방법도 검토해야 한다.

#### TTL

```text
TTL이 너무 김 → 오래된 데이터가 남을 가능성 증가
TTL이 너무 짧음 → Cache Miss 증가
```

TTL은 데이터 변경 주기와 허용 가능한 지연을 기준으로 정해야 한다.

#### 캐시 키

```text
새로운 검색·정렬 조건 추가
→ 조회 결과가 달라짐
→ 캐시 키에도 해당 조건 추가
```

예를 들어 정렬 방향을 선택할 수 있다면 `sort`도 키에 포함해야 한다.

### 13. 마무리

```text
반복 조회 → Redis의 목록 캐시 사용
데이터 변경 → 기존 목록 캐시 삭제
상태 확인 → Actuator 헬스체크 사용
```

캐시는 `@Cacheable`을 붙이는 것보다 캐시 키와 무효화 범위를 정하는 과정이 더 중요했다. 운영 환경에서는 Redis 장애 처리와 헬스 정보 공개 범위도 함께 정해야 한다.
