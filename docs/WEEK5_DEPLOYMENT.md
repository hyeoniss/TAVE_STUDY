# 5주차 배포 가이드

## 0. 과제 제출용 Render 간편 배포

AWS/RDS 대신 외부 접속 가능한 시연 서버만 빠르게 만들 때 사용한다. `render.yaml`은
Docker Web Service 한 개와 `render` 프로파일을 사용한다. DB는 H2 인메모리 방식이고
Redis 캐시는 끄므로 별도 인프라가 필요 없다.

1. 변경 내용을 GitHub 저장소에 push한다.
2. Render Dashboard에서 **New > Blueprint**를 선택한다.
3. 저장소를 연결하고 루트의 `render.yaml`을 선택한다.
4. 배포가 끝나면 `/actuator/health`와 `/api/todos`를 확인한다.
5. Render의 배포 성공 화면과 공개 API 응답을 캡처한다.

무료 인스턴스가 재시작되면 데이터가 사라지고 시연용 Todo 65개가 다시 만들어진다.
따라서 이 구성은 과제 시연용이며 운영 환경에는 사용하지 않는다. 캐시 전후 성능 비교는
로컬 Docker 환경에서 측정한 결과를 제출한다.

## 1. 로컬 Docker 배포

```bash
cp .env.example .env
docker compose up -d --build --wait
curl http://localhost:8080/actuator/health
```

구성은 Spring Boot 애플리케이션, MySQL 8.4, Redis 7.4이다. 애플리케이션 이미지는
빌드용 JDK와 실행용 JRE를 분리한 멀티 스테이지 이미지이며 일반 사용자 권한으로 실행된다.
MySQL 스키마는 애플리케이션 시작 시 Flyway가 생성하고, 이후에는 Hibernate가 스키마를
검증한다.

기존 로컬 서비스와 충돌하지 않도록 MySQL은 호스트에 포트를 공개하지 않고, Redis는
호스트의 `6380`을 사용한다. Compose 내부에서 애플리케이션은 각각 기본 포트 `3306`,
`6379`로 연결된다.

종료할 때는 `docker compose down`을 실행한다. DB 데이터까지 초기화하려는 경우에만
`docker compose down -v`를 사용한다.

## 2. AWS EC2 + RDS 배포

### 네트워크와 보안 그룹

- RDS는 퍼블릭 액세스를 끄고 EC2와 같은 VPC의 프라이빗 서브넷에 둔다.
- EC2 보안 그룹은 SSH(22)를 관리자 IP에만, API 포트(8080)를 테스트할 IP 또는 ALB에만 연다.
- RDS 보안 그룹의 MySQL(3306) 인바운드 소스는 IP가 아니라 EC2 보안 그룹으로 지정한다.
- 운영 Redis는 ElastiCache를 사용하거나 EC2 내부 컨테이너로 두며 외부에 6379를 공개하지 않는다.

### RDS 준비

1. MySQL 8 계열 RDS와 `todomate` 데이터베이스를 만든다.
2. 사용자 이름과 비밀번호는 AWS Secrets Manager 또는 EC2 환경 파일에 보관한다.
3. JDBC 주소는 `jdbc:mysql://<RDS-ENDPOINT>:3306/todomate?serverTimezone=Asia/Seoul` 형식이다.

### 이미지 빌드와 실행

```bash
docker build -t todomate:1.0.0 .
docker run -d --name todomate --restart unless-stopped \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_URL='jdbc:mysql://<RDS-ENDPOINT>:3306/todomate?serverTimezone=Asia/Seoul' \
  -e DB_USERNAME='<DB-USER>' \
  -e DB_PASSWORD='<DB-PASSWORD>' \
  -e REDIS_HOST='<REDIS-HOST>' \
  -e REDIS_PORT='6379' \
  todomate:1.0.0
```

비밀번호를 셸 기록에 직접 남기지 않으려면 실제 배포에서는 권한을 제한한 env 파일이나
Secrets Manager를 사용한다. 최초 실행 시 Flyway의 `V1__create_todos.sql`이 테이블과
인덱스를 만든다.

### 확인 및 트러블슈팅

```bash
curl http://<EC2-PUBLIC-IP>:8080/actuator/health
docker logs --tail 200 todomate
```

- DB 연결 실패: RDS 보안 그룹, 엔드포인트, DB 이름, 사용자 권한 순서로 확인한다.
- Redis 연결 실패: Redis 주소와 6379 접근 규칙을 확인한다.
- 외부 접속 실패: EC2 보안 그룹과 애플리케이션 컨테이너의 `8080:8080` 매핑을 확인한다.
- 스키마 검증 실패: Flyway 로그와 `flyway_schema_history`를 확인하며 운영 DB를 수동 수정하지 않는다.

## 3. 제출 증빙

- `docker ps`에서 앱, DB, Redis가 실행 중인 화면
- `/actuator/health`가 `UP`인 화면
- EC2 인스턴스와 RDS 연결 화면(계정 ID, 엔드포인트, IP, 비밀번호는 가림)
- 배포 URL에서 Todo API가 응답하는 화면
- 장애 한 건과 해결 과정을 정리한 로그 또는 메모
