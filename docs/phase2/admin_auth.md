# 관리자 인증 — 결정과 스키마 (PH-49a-①)

관리자를 별도 테이블로 분리하지 않고 `customer`에 `role`·`permissions`를 더하고, 로그인 경로만
`/admin/auth/login`으로 갈라낸다. **이 문서가 PH-49a-② 구현의 정본이다** — 여기 적힌 값 그대로 짜고,
없는 것만 다시 논의한다.

| | |
|---|---|
| 이슈 | [#561](https://github.com/corebank-tech/corebank-server/issues/561) |
| 후속 | PH-49a-②(10/8) · PH-89(`#458`) · FE `#136` · PH-49c · PH-49b |
| Flyway | `V202610061129`(컬럼) · `V202610061130`(공통코드) |

## 1. 결정 5개

**① 저장 모델** — `customer.role`(`CUSTOMER`/`ADMIN`, `VARCHAR(12)` NOT NULL DEFAULT `CUSTOMER`) ·
`customer.permissions`(`VARCHAR(200)` NULL, 권한 CSV).

관리자 테이블을 따로 두지 않는 이유는 비밀번호·잠금·세션 메커니즘을 고객과 공유하고
`SessionLoginManager`를 그대로 쓰기 때문이다. 권한을 조인 테이블로 두지 않는 이유는 값이 5종 고정이고
부여·회수가 계정 단위로만 일어나기 때문이다.

`role=CUSTOMER`인 행의 `permissions`는 **NULL**이다. 빈 문자열을 쓰지 않는다 — "권한 0개인 관리자"와
"관리자가 아닌 고객"을 구분해야 한다. 둘이 모순되는 상태(`CUSTOMER`인데 권한이 있음)를 막는 DB 제약은
그 값을 쓰는 부여·회수 API가 생기는 PH-49b에서 함께 넣는다.

**② 권한 5종 고정**

| 권한 | 의미 | 쓰는 곳 |
|---|---|---|
| `GL_READ` | 정보계 조회 | ADM-03 · PH-77 |
| `GL_WRITE` | 정보계 변경 | 전표·분개 변경 |
| `CUSTOMER_READ` | 고객 조회 | PH-97 검색·상세 |
| `CUSTOMER_WRITE` | 고객 변경 | PH-97 잠금해제·비밀번호 초기화·상태 변경 |
| `AUDIT_READ` | 감사로그 조회 | 대사 불일치(ADM-04·PH-38) · PH-89 |

`api_conventions.md` §5-8에 `CustomerRole`·`AdminPermission`으로 등록했다. 값을 늘리면 `/admin/**`
권한 매핑표(②)와 부여·회수 API(PH-49b)를 함께 고쳐야 한다. ①·② 시점에는 시드와 Runbook SQL로만 넣는다.

**③ 로그인 경로 분리** — `POST /admin/auth/login`. `role=ADMIN`만 통과한다.
**실패 응답은 고객 로그인과 같은 `ATH0101`** 이다. 아이디 없음 · 비밀번호 틀림 · **고객 계정의 관리자
로그인 시도** 셋 다 같은 응답이어서, 관리자 계정의 존재가 드러나지 않는다(AGENTS 규칙 4 · REQ-NFR-017).

**④ 관리자 세션 30분 무조작 만료** (고객은 10분)

전역 `server.servlet.session.timeout: 10m`은 **그대로 두고**, 관리자 로그인에서만 세션 생성 직후
`HttpSession.setMaxInactiveInterval(1800)`으로 덮는다. `SessionLoginManager.establishSession()`이
세션 생성 뒤 `getMaxInactiveInterval()`을 읽어 `sessionExpiresAt`을 계산하므로, 덮어쓰기를 그 읽기보다
앞에 두면 응답의 만료시각도 자동으로 30분이 된다. PH-49c로 저장소가 Redis가 돼도 세션 인터페이스
수준의 호출이라 그대로 동작한다.

**⑤ `GET /customers/me`에 `role`·`permissions[]` 추가**

CSV는 저장 형식이고 API 계약은 **배열**이다. 고객은 `role: "CUSTOMER"`, `permissions: []`로 나간다
(NULL이 아니라 빈 배열).

## 2. 스키마

`V202610061129`가 컬럼 2개와 `ck_customer_role`을, `V202610061130`이 공통코드
(`CUSTOMER_ROLE` 2행 · `ADMIN_PERMISSION` 5행)를 넣는다. 파일을 나눈 이유는 MySQL DDL이 암묵적
커밋이라 한 파일에서 ALTER 성공 후 INSERT가 실패하면 컬럼만 남고 이력은 실패로 기록되기 때문이다.

공통코드가 필요한 이유는 `code`가 서버 Enum 값과 일치해야 하고 행이 없으면 프론트가 표시명 매핑에
실패하기 때문이다(`schema_reference.md` `common_code` 절 · REQ-CMN-023).

엔티티 매핑(`CustomerJpaEntity`)은 ②에서 붙인다. `ddl-auto: validate`는 엔티티에 매핑된 컬럼만
검사하므로 ① 단계에서 컬럼만 늘어나도 기동이 깨지지 않는다.

## 3. 시드 (local 전용)

`db/seed/local-admin-accounts.sql`을 `LocalAdminAccountLoader`가 **`local` 프로필에서만** 적재한다.

| `user_id` | 권한 | 쓰임 |
|---|---|---|
| `adminreader` | `GL_READ,CUSTOMER_READ,AUDIT_READ` | 조회 전용. ②의 DoD "변경 API 403" 확인 |
| `adminoperator` | 5종 전부 | FE 관리자 채널 시연 |

비밀번호는 둘 다 **`Admin1234!`**.

QA 데모 데이터(`local-demo-data.sql`)와 **파일을 나눈 이유**는 `DemoDataLoader`가
`@Profile("!phase2-seed & (local | qa-seed)")`이고 팀 QA 배포는 `prod,qa-seed`로 **운영 RDS**에
시드를 올리기 때문이다(`team_db_architecture_guide.md`). 관리자 비밀번호가 저장소에 평문으로 있어
운영에 들어가면 안 된다.

`test` 프로필에는 시드를 두지 않는다. 시드 파일은 `LocalAdminAccountSeedIntegrationTest`가 로드해
권한 조합과 재실행 멱등성을 검증한다.

## 4. prod 관리자 계정 (10/16 릴리스 이후)

시드로 넣지 않는다. 아래를 운영 DB에 직접 실행한다. 실행 절차는 `docs/personal/p1_ec2_runbook.md`.

```sql
-- 해시는 BCrypt(strength 10)로 미리 만들어 채운다. 평문을 SQL 에 쓰지 않는다.
-- 운영 관리자는 한 명으로 시작하고, 추가는 PH-49b 부여·회수 API 로 만든다.
INSERT INTO customer (
    user_id, password_hash, user_name, birth_date, email, phone_number,
    login_failure_count, account_locked, status, role, permissions,
    joined_at, created_at, updated_at
) VALUES (
    :user_id, :bcrypt_hash, :user_name, :birth_date, :email, :phone_number,
    0, FALSE, 'ACTIVE', 'ADMIN',
    'GL_READ,GL_WRITE,CUSTOMER_READ,CUSTOMER_WRITE,AUDIT_READ',
    NOW(6), NOW(6), NOW(6)
);
-- 확인: SELECT user_id, role, permissions FROM customer WHERE role = 'ADMIN';
```

## 5. FE 계약 (FE `#136`)

**`POST /api/v1/admin/auth/login`** — 요청·응답 형태는 고객 로그인과 같다. 바뀌는 건 경로와
`sessionExpiresAt` 간격(30분)뿐이다.

```json
// 요청
{ "userId": "adminoperator", "password": "Admin1234!" }

// 200
{ "code": "0000", "message": "정상 처리되었습니다.",
  "data": { "customerId": 4, "userName": "운영담당자",
            "sessionExpiresAt": "2026-10-16T14:30:00+09:00" } }

// 401 — 세 실패 경우 모두 동일
{ "code": "ATH0101", "message": "아이디 또는 비밀번호가 일치하지 않습니다." }
```

`JSESSIONID`·CSRF 쿠키는 고객 로그인과 같이 발급되고, 로그아웃은 기존 `POST /auth/logout`을 쓴다.

**`GET /api/v1/customers/me`** — 기존 응답에 두 필드가 붙는다.

```json
{ "code": "0000", "message": "정상 처리되었습니다.",
  "data": { "customerId": 4, "userName": "운영***", "userId": "adminoperator",
            "birthDate": "1983-07-21", "phoneNumber": "010****9002",
            "email": "admin****@example.com", "joinedAt": "2026-07-01T09:00:00+09:00",
            "role": "ADMIN",
            "permissions": ["GL_READ", "GL_WRITE", "CUSTOMER_READ", "CUSTOMER_WRITE", "AUDIT_READ"] } }
```

고객 계정은 `"role": "CUSTOMER"`, `"permissions": []`다. 표시명은 공통코드
`CUSTOMER_ROLE`·`ADMIN_PERMISSION`에서 받아 쓴다 — 화면에 한글을 하드코딩하지 않는다
(`api_conventions.md` §1 · REQ-CMN-008).

## 6. ②가 할 일

- `/admin/auth/login` 컨트롤러와 `role=ADMIN` 검증. `SessionLoginManager`에 만료 초를 넘기고 권한을
  `ROLE_ADMIN`으로 부여한다 (현재 `CUSTOMER_ROLE = "ROLE_CUSTOMER"` 상수 자리)
- **`/admin/auth/login`을 `/admin/**` 규칙보다 앞에 `permitAll` + CSRF 예외로 둔다.** 안 하면 로그인
  요청 자체가 관리자 인가에 걸린다. 구현체는 필터가 아니라 `AuthorizationManager`다
- `/admin/**` 인가: ADMIN이 아니면 403, 변경 메서드는 `*_WRITE` 검사. URL 접두어 → 권한 매핑표 작성
- **S0 임시 가드 제거** — `AdminBootstrapAuthorizationManager`·`AdminBootstrapProperties`·
  `SecurityConfig` 인가 한 줄·`application.yml`의 `app.security.admin.bootstrap-customer-ids`·
  관련 테스트 3건·`SecurityConfigTest` 관리자 3건 재작성
- `CustomerJpaEntity` 매핑과 `CustomerInfoResponse` 필드 추가
- 관리자 로그인·로그아웃 감사로그에 actor role 포함(`AuditEventType.LOGIN`)
