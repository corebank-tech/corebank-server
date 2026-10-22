# PH-49c — JDBC 세션 외부화 구현·검증

## 범위와 선행 작업

PH-101은 일회성 토큰·잠금·약관 이력의 저장소 변경이고, PH-49c는 HTTP 로그인 세션의 저장소 변경이다.
`app.ephemeral-store.provider`와 Redis 구현은 수정하지 않는다. Spring Session JDBC는 공용 MySQL을 사용한다.
`JSESSIONID` 쿠키 이름과 기존 로그인·CSRF·로그아웃 API를 유지한다.

PH-49c는 고객 세션 기본값 10분을 Spring Session JDBC에 적용한다. PH-49a-②(PR #639)의
`SessionLoginManager`는 관리자 로그인에서 `app.security.admin.session-timeout: 30m`을 적용한다.
두 변경을 통합하면 역할별 로그인 세션을 같은 JDBC 테이블에 저장한다.

## 저장과 종료

- `SPRING_SESSION`, `SPRING_SESSION_ATTRIBUTES`는 Flyway로 생성한다. 자동 DDL은 `never`다.
- 세션 시각은 Spring Session 표준인 epoch 밀리초이며 업무 테이블의 KST 벽시각과 구분한다.
- `AuthenticatedCustomer`는 Serializable·Principal을 구현하고 `getName()`은 고객 PK 문자열이다.
- 세션에 비밀번호·OTP 원문을 넣지 않는다. 직렬화 인증 객체의 스키마 변경은 배포 호환성을 검토한다.
- 이전 배포의 세션 속성을 역직렬화할 수 없으면 인증정보 없는 세션으로 처리해 보호 API가 401을 반환한다. 고객은 다시 로그인할 수 있다.
- 로그인 세션 저장이 실패하면 로그인 API는 200을 반환하지 않는다. 비밀번호 변경 중 세션 삭제가 실패하면 변경·감사 기록을 롤백한다.
- JVM `SessionRegistryImpl`과 메모리 만료 플래그를 제거한다. 다음 요청은 DB 삭제·만료 여부로 인증한다.
- 비밀번호 변경 이벤트는 BEFORE_COMMIT에 JDBC 삭제를 실행한다. 삭제는 기존 업무 트랜잭션을 필수로 요구하며, 실패 시 비밀번호·감사 기록도 롤백된다.
- 로그아웃은 해당 세션만 삭제한다.
- 만료 행 정리는 매분 실행한다. 정리 이전에도 만료한 세션으로 인증할 수 없다.
- 이미 진행 중인 요청을 강제로 취소하는 기능은 아니다. 변경 커밋 후 시작한 새 요청의 세션 거부를 검증한다.

## 자동 검증

Docker가 실행된 환경에서 다음을 실행한다.

```powershell
.\gradlew.bat test --tests '*JdbcSessionIntegrationTest' --tests '*LoginApiIntegrationTest' --tests '*SessionLoginManagerTest' --tests '*LoginPasswordChangeSessionFailureIntegrationTest' --tests '*SwaggerUiAccessIntegrationTest'
```

`JdbcSessionIntegrationTest`는 실제 MySQL에서 인증 객체 직렬화·고객 PK 검색·만료·속성 삭제·커밋/롤백을 검증한다.
`DualWasJdbcSessionIntegrationTest`는 **독립 JVM 두 개**를 무작위 로컬 포트로 실행하고 Testcontainers의 같은 MySQL에 연결한다.
로그인은 실제 API를 호출하며 A에서 받은 쿠키를 B로 전송한다. B 로그아웃 후 A 거부,
비밀번호 변경 후 두 세션 거부, A 종료 후 B 로그인 유지, 무조작 만료 연장과 만료 거부를 확인한다.
자식 프로세스는 테스트 종료 시 정리하고 로그는 `build/reports/dual-was/`에 저장한다.

기존 `LoginApiIntegrationTest`는 세션 고정 공격 방지, 고객 비밀번호 변경·재설정, 로그아웃·CSRF 회귀를 검증한다. 손상된 인증 속성의 401·재로그인, 로그인 저장 실패의 비성공 응답, 세션 삭제 실패 시 변경 API의 비성공 응답도 검증한다.
`LoginPasswordChangeSessionFailureIntegrationTest`는 실제 변경 서비스에서 세션 삭제 실패 시 비밀번호와 감사 기록이 롤백되는지 검증한다.
`LoginApiIntegrationTest`는 고객 로그인에 세션 기본값 10분이 적용되는지 검증한다.
관리자 로그인에 대해서는 실제 HTTP 응답의 `sessionExpiresAt`, JDBC 세션 행의 1800초·고객 PK,
다음 보호 API 요청의 인증 복원을 함께 검증한다.
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
- 관리자 30분 만료 정책은 PH-49a-②가, 세션의 JDBC 저장은 PH-49c가 맡는다. 관리자 조치 시 세션 종료는 별도 담당 작업이다.
- 로컬 두 JVM 검증은 AWS ALB·서브넷·AZ 장애 전환 검증을 대체하지 않는다. 실제 인프라가 준비되면 별도 검증한다.

## 운영 성능 재측정 계획

Issue #593의 A′/B′는 PH-49c가 없는 릴리스 1 이미지 `1709c355`에서 `app.ephemeral-store.provider`만 바꿔 비교한 결과다. P1 공유에 따르면 Redis·JDBC 각 4회, 총 8회 측정을 마쳤다(10/16·10/20). 결과 PR의 판정과 원자료는 P1·P4가 기록한다. **B′에는 JDBC 로그인 세션의 비용이 포함되지 않는다.**

- **릴리스 포함 확인:** PH-49c는 10/23 릴리스 2에 포함하기로 했다. 측정 전 PR #641의 `dev` 머지와 실제 배포 이미지 SHA에 해당 커밋이 포함됐는지 확인한다. 포함되지 않은 이미지에서는 아래 측정 창을 PH-49c 적용 후 비교 창으로 사용할 수 없다.
- **측정 창:** 릴리스 2 배포 후 DB 마이그레이션과 캐시 예열이 끝난 시점부터 10/26 전까지 잰다. 2시간 간격 대사 스케줄의 `:30`·`:45` 및 23:20~00:20은 피한다. 이 결과는 **PH-49c 세션 외부화 영향**으로 기록하고 PH-30 개선 전 기준선으로 채택하지 않는다.
- **고정 조건:** A′/B′를 다시 나눠 재지 않고 `provider=jdbc` 한 모드에서 Issue #593의 B′와 동일하게 4회 측정한다. Issue #593 결과 PR에 명시된 확정 `load.js`(dev 보정본)·요청 수준·회차 구성·k6 옵션·측정 명령·지표 산출 방식을 그대로 사용한다. 데이터 규모와 인프라 조건도 최대한 맞춘다. 세션 외부화 외에 릴리스 2에 포함된 이체 경로 변경(PR #640의 이체당 영업일 조회 1회 등)과 운영 이미지 SHA를 함께 기록한다.
- **4회 반복 이유와 판독:** B′가 JDBC 모드에서 4회 측정된 값이므로 재측정도 같은 횟수로 맞춰 회차별 변동 폭을 비교한다. 한 번의 p95·p99 또는 RDS 지표는 캐시 예열, 일시적인 DB 부하, 커넥션·행 잠금 대기에 흔들릴 수 있다. 각 요청률에서 4회 결과를 따로 보존하고 중앙값과 최솟값·최댓값을 함께 제시한다. 특정 1회만 느린지 4회에 걸쳐 차이가 반복되는지 확인하며, 4회 반복만으로 PH-49c의 단독 인과효과나 통계적 유의성을 입증했다고 주장하지 않는다.
- **기록 지표:** 인증된 요청의 엔드포인트별 p95·p99와 성공률에 더해 RDS CPU·연결 수, 커넥션 획득 timeout, 행 잠금 대기, 세션 테이블의 조회·갱신 부하를 가능한 범위에서 기록한다. P4가 이미지 SHA·데이터 규모·RDS 지표를 제공하고, P6은 같은 부하 조건의 PH-49c 적용 후 결과를 정리한다. 지표가 별도로 계측되지 않으면 측정하지 못한 항목으로 명시한다.
- **해석:** 릴리스 2의 다른 변경도 함께 반영되므로 B′와의 차이를 PH-49c만의 효과로 단정하지 않는다. 10/27 PH-70 RDS 이전 후에는 DB 사양이 달라져 이전 RDS 수치를 PH-30 개선 전후 비교에 쓰지 않는다. PH-30의 새 개선 전 기준선은 새 RDS에서 Issue #555·#568·#569·LOCK-TUNE 성능 개선을 머지하기 전에 P1이 PH-30 양식으로 다시 측정한다.
