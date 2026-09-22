# 쇼핑몰 JPA 실습

회원이 여러 상품을 주문하는 쇼핑몰을 예제로 JPA N+1 문제를 재현하고, 세 가지 해결 방법의 쿼리 수를 비교한다.

## 기술 스택

- Java 17
- Spring Boot 4.1.1
- Spring Data JPA, Hibernate
- H2 Database
- Gradle, JUnit 5

## 도메인 관계

```text
Member 1 ─── N Order 1 ─── N OrderItem N ─── 1 Product
```

- `Order.member`, `Order.orderItems`, `OrderItem.product`는 지연 로딩한다.
- 일반 주문 조회 후 응답 DTO로 변환하면서 지연 로딩 연관관계에 접근하면 N+1이 발생한다.

## 실행 및 시연

애플리케이션을 실행하면 회원 3명, 상품 6개, 주문 3개가 자동으로 저장된다. 서버를 한 번만 실행한 뒤 다음 API를 순서대로 호출하면 코드를 수정하거나 재실행하지 않고 결과를 비교할 수 있다.

```bash
./gradlew bootRun
```

| API | 방식 | 쿼리 수 | 동작 |
|---|---|---:|---|
| `GET /api/orders/n-plus-one` | 해결 전 | 13 | 주문 조회 후 회원, 주문상품, 상품을 건별 조회한다. |
| `GET /api/orders/fetch-join` | Fetch Join | 1 | JPQL `join fetch`로 연관관계를 한 번에 조회한다. |
| `GET /api/orders/entity-graph` | EntityGraph | 1 | 조회 그래프로 가져올 연관관계를 선언한다. |
| `GET /api/orders/batch-fetch` | Batch Fetching | 4 | 지연 로딩 대상을 종류별 `IN` 쿼리로 묶어서 조회한다. |

각 응답의 `queryCount`와 콘솔의 SQL 로그를 비교한다.

```bash
curl -s http://localhost:8080/api/orders/n-plus-one
curl -s http://localhost:8080/api/orders/fetch-join
curl -s http://localhost:8080/api/orders/entity-graph
curl -s http://localhost:8080/api/orders/batch-fetch
```

응답에는 사용한 방식, 실제 쿼리 수, 주문 목록이 포함된다.

```json
{
  "strategy": "FETCH_JOIN",
  "queryCount": 1,
  "orders": []
}
```

### N+1에서 13개 쿼리가 발생하는 이유

```text
주문 목록 조회              1개
주문별 회원 조회            3개
주문별 주문상품 조회         3개
주문상품별 상품 조회         6개
──────────────────────────────
합계                       13개
```

### 해결 방법 비교

| 방식 | 장점 | 단점 및 주의점 |
|---|---|---|
| Fetch Join | 필요한 연관관계를 SQL 한 번으로 명확하게 조회한다. | 컬렉션 Fetch Join과 페이징을 함께 사용하기 어렵다. 조회별 JPQL이 필요하다. |
| EntityGraph | JPQL에 Fetch Join을 직접 작성하지 않고 로딩 전략을 선언할 수 있다. | 복잡한 조회 조건을 표현하기 어렵고 과도한 그래프는 큰 조인을 만든다. |
| Batch Fetching | 기존 지연 로딩 구조를 유지하면서 여러 프록시와 컬렉션을 `IN` 쿼리로 묶는다. 페이징과 함께 사용하기 좋다. | 쿼리가 한 번으로 줄지는 않으며 적절한 배치 크기를 정해야 한다. |

> Hibernate Statistics 초기화와 세션별 배치 크기 변경은 네 가지 방식을 한 서버에서 바로 비교하기 위한 학습용 코드다.

## 트랜잭션 격리 수준별 이상 현상

| 격리 수준 | Dirty Read | Non-repeatable Read | Phantom Read | 특징 |
|---|:---:|:---:|:---:|---|
| READ UNCOMMITTED | 발생 가능 | 발생 가능 | 발생 가능 | 커밋되지 않은 변경도 읽을 수 있어 동시성은 높지만 정합성이 가장 낮다. |
| READ COMMITTED | 방지 | 발생 가능 | 발생 가능 | 문장마다 커밋된 최신 데이터를 읽는다. PostgreSQL과 Oracle의 일반적인 기본값이다. |
| REPEATABLE READ | 방지 | 방지 | 표준상 발생 가능 | 한 트랜잭션에서 같은 행을 반복 조회해도 값이 유지된다. MySQL InnoDB에서는 MVCC와 잠금으로 일반적인 Phantom Read도 방지한다. |
| SERIALIZABLE | 방지 | 방지 | 방지 | 트랜잭션을 직렬 실행한 것처럼 격리한다. 정합성은 가장 높지만 동시성과 처리량이 낮다. |

- Dirty Read: 다른 트랜잭션이 아직 커밋하지 않은 값을 읽는 현상
- Non-repeatable Read: 같은 행을 두 번 읽었는데 중간의 수정 또는 삭제로 값이 달라지는 현상
- Phantom Read: 같은 조건으로 다시 조회했을 때 중간의 삽입 또는 삭제로 행 집합이 달라지는 현상

표는 SQL 표준을 기준으로 한다. 실제 발생 여부는 데이터베이스의 MVCC 및 잠금 구현에 따라 달라질 수 있다. 예를 들어 MySQL InnoDB의 REPEATABLE READ는 일반적인 Phantom Read도 방지한다.

## 입력값 검증과 오류 응답

회원명과 상품명은 비어 있을 수 없고, 상품 가격과 주문 수량은 0보다 커야 한다. 잘못된 요청, 존재하지 않는 회원·상품, 재고 부족은 공통 형식으로 반환한다.

```json
{
  "status": 400,
  "message": "주문 수량은 0보다 커야 합니다.",
  "timestamp": "2026-09-22T15:30:00"
}
```

## 테스트

```bash
./gradlew test
```

`OrderQueryDemoTest`가 동일한 데모 데이터에서 쿼리 수가 각각 `13`, `1`, `1`, `4`인지 자동으로 검증한다.
