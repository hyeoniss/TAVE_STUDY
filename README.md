# Todomate

Java 17 + Spring Boot 기반 투두 API 프로젝트입니다. 투두 CRUD, 날짜 조건 조회,
페이지네이션, 공통 응답과 예외 처리, Redis 목록 캐시를 포함합니다.

## 실행

```bash
docker compose up -d redis
./gradlew bootRun
```

Gradle에는 Web MVC, JPA, Validation, Redis/Cache, Actuator, Lombok, MySQL과 테스트용 H2가 등록되어 있습니다.
