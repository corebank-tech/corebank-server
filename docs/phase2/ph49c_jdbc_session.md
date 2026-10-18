# PH-49c — JDBC 세션 외부화 구현·검증

## 범위와 선행 작업

PH-101은 일회성 토큰·잠금·약관 이력의 저장소 변경이고, PH-49c는 HTTP 로그인 세션의 저장소 변경이다.
`app.ephemeral-store.provider`와 Redis 구현은 수정하지 않는다. Spring Session JDBC는 공용 MySQL을 사용한다.
`JSESSIONID` 쿠키 이름과 기존 로그인·CSRF·로그아웃 API를 유지한다.

PH-49c는 고객 인증 세션에 10분, `ROLE_ADMIN` 인증 세션에 30분 무조작 만료 정책을 적용한다.
관리자 로그인 API는 PH-49a-② 담당이 구현한다. 현재는 역할별 정책을 단위 테스트로 검증하고,
실제 관리자 로그인 API를 통한 종단 검증은 해당 작업과 통합할 때 수행한다.

## 저장과 종료

- `SPRING_SESSION`, `SPRING_SESSION_ATTRIBUTES`는 Flyway로 생성한다. 자동 DDL은 `never`다.
- 세션 시각은 Spring Session 표준인 epoch 밀리초이며 업무 테이블의 KST 벽시각과 구분한다.
- `AuthenticatedCustomer`는 Serializable·Principal을 구현하고 `getName()`은 고객 PK 문자열이다.
- 세션에 비밀번호·OTP 원문을 넣지 않는다. 직렬화 인증 객체의 스키마 변경은 배포 호환성을 검토한다.
- JVM `SessionRegistryImpl`과 메모리 만료 플래그를 제거한다. 다음 요청은 DB 삭제·만료 여부로 인증한다.
- 비밀번호 변경 이벤트는 BEFORE_COMMIT에 JDBC 삭제를 실행한다. 세션 삭제 실패 시 업무도 롤백된다.
- 로그아웃은 해당 세션만 삭제한다.
- 만료 행 정리는 매분 실행한다. 정리 이전에도 만료한 세션으로 인증할 수 없다.
- 이미 진행 중인 요청을 강제로 취소하는 기능은 아니다. 변경 커밋 후 시작한 새 요청의 세션 거부를 검증한다.

## 자동 검증

Docker가 실행된 환경에서 다음을 실행한다.

```powershell
.\gradlew.bat test --tests '*JdbcSessionIntegrationTest' --tests '*LoginApiIntegrationTest' --tests '*RoleSessionTimeoutStrategyTest'
```

`JdbcSessionIntegrationTest`는 실제 MySQL에서 인증 객체 직렬화·고객 PK 검색·만료·속성 삭제·커밋/롤백을 검증한다.
`DualWasJdbcSessionIntegrationTest`는 **독립 JVM 두 개**를 무작위 로컬 포트로 실행하고 Testcontainers의 같은 MySQL에 연결한다.
로그인은 실제 API를 호출하며 A에서 받은 쿠키를 B로 전송한다. B 로그아웃 후 A 거부,
비밀번호 변경 후 두 세션 거부, A 종료 후 B 로그인 유지, 무조작 만료 연장과 만료 거부를 확인한다.
자식 프로세스는 테스트 종료 시 정리하고 로그는 `build/reports/dual-was/`에 저장한다.

기존 `LoginApiIntegrationTest`는 세션 고정 공격 방지, 고객 비밀번호 변경·재설정, 로그아웃·CSRF 회귀를 검증한다.
`RoleSessionTimeoutStrategyTest`는 요청 경로와 무관하게 고객 10분·관리자 30분 정책이 적용되는지 검증한다.
JaCoCo 기본 HTML/XML 리포트는 `build/reports/jacoco/test/`에 생성한다.
자식 WAS JVM은 기본 JaCoCo 대상이 아니므로 그 코드 실행을 커버리지에 포함했다고 보고하지 않는다.

## 로컬 Swagger UI

같은 로컬 MySQL을 사용하는 터미널 두 개에서 실행한다.

```powershell
# 터미널 A
.\gradlew.bat bootRun --args='--spring.profiles.active=local --server.port=8080'
# 터미널 B
.\gradlew.bat bootRun --args='--spring.profiles.active=local --server.port=8081'
```

`http://localhost:8080/api/v1/swagger-ui/index.html`에서 로컬 WAS A를 선택하고 테스트 계정으로 로그인한다.
같은 브라우저의 새 탭에서 `http://localhost:8081/api/v1/swagger-ui/index.html`을 열고 로컬 WAS B를 선택해 `/customers/me`를 호출한다. 브라우저 쿠키는 포트별로 구분되지 않아 동일 세션이 전달된다. 각 UI와 같은 포트의 서버를 선택하면 추가 CORS 허용 없이 검증할 수 있다.
로그아웃은 `/auth/logout`을 실행한다. XSRF-TOKEN 쿠키와 X-XSRF-TOKEN 헤더가 필요하다.
완료 후 A의 보호 API도 401인지 확인한다. Swagger에는 세션 목록이나 관리용 세션 삭제 API를 추가하지 않았다.

## 배포·팀 영향

- 최초 전환 시 이전 메모리 세션은 이전하지 않으므로 재로그인이 필요하다. 구버전·신버전 혼합 롤링 중 세션 호환을 가정하지 않는다.
- 두 WAS는 같은 DB·세션 스키마·호환 가능한 인증 클래스와 쿠키 설정을 사용해야 한다.
- 운영 app_user에는 세션 두 테이블의 SELECT/INSERT/UPDATE/DELETE 권한, Flyway 계정에는 DDL 권한이 필요하다.
- 관리자 로그인 API는 PH-49a-② 담당자 구현과 통합해 실제 30분 만료를 종단 검증한다. 관리자 조치 시 세션 종료는 별도 담당 작업이다.
- 로컬 두 JVM 검증은 AWS ALB·서브넷·AZ 장애 전환 검증을 대체하지 않는다. 실제 인프라가 준비되면 별도 검증한다.
