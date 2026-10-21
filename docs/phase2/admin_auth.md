# 관리자 인증 — 결정과 스키마 (PH-49a-①)

관리자를 별도 테이블로 분리하지 않고 `customer`에 `role`을 더하고 권한은 `admin_permission`에 두며, 로그인 경로만
`/admin/auth/login`으로 갈라낸다. **이 문서가 PH-49a-② 구현의 정본이다** — 여기 적힌 값 그대로 짜고,
없는 것만 다시 논의한다.

| | |
|---|---|
| 이슈 | [#561](https://github.com/corebank-tech/corebank-server/issues/561) |
| 후속 | PH-49a-②(10/8) · PH-89(`#458`) · FE `#136` · PH-49c · PH-49b |
| Flyway | `V202610152100`(`customer.role`) · `V202610152101`(공통코드) · `V202610152102`(`admin_permission`) |

## 1. 결정 5개

**① 저장 모델** — `customer.role`(`CUSTOMER`/`ADMIN`, `VARCHAR(12)` NOT NULL DEFAULT `CUSTOMER`) ·
`admin_permission`(`customer_id` + `permission` 복합 PK, 행 하나가 권한 하나).

관리자 테이블을 따로 두지 않는 이유는 비밀번호·잠금·세션 메커니즘을 고객과 공유하고
`SessionLoginManager`를 그대로 쓰기 때문이다.

**권한은 조인 테이블로 둔다.** 처음에는 `customer.permissions`에 CSV로 담으려 했으나 PR #572 리뷰에서
바꿨다. 값이 5종 고정이라 CSV로도 담기지만,

- CSV는 글자일 뿐이라 `GL_RAED` 같은 **오타를 DB가 막지 못하고**,
- 표시명을 등록한 `common_code`(`ADMIN_PERMISSION`)와 **값이 이어지지 않으며**,
- PH-49b의 **부여·회수 API가 문자열을 자르고 붙이는 코드**가 된다.

행으로 두면 CHECK가 오타를 막고 부여·회수가 `INSERT`/`DELETE` 한 줄이 된다.

**권한이 없는 관리자는 `admin_permission`에 행이 0개다.** "권한 0개인 관리자"와 "관리자가 아닌 고객"은
`customer.role`로 갈린다. 둘이 모순되는 상태(`CUSTOMER`인데 권한 행이 있음)를 막는 제약은 그 값을 쓰는
부여·회수 API가 생기는 PH-49b에서 함께 넣는다.

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

**`POST /auth/login`은 반대로 `role=CUSTOMER`만 통과한다**(PR #639 리뷰 반영). 고객 경로가 역할을
가리지 않으면 관리자가 그 문으로 들어와 `GET /customers/me`의 `role`·`permissions[]`를 읽을 수 있어,
위 응답 통일이 지키려던 것이 그대로 새어 나간다. 두 경로가 서로를 거절해야 경로 분리가 경계가 된다.
실패 응답은 역시 `ATH0101`이다.

**④ 관리자 세션 30분 무조작 만료** (고객은 10분)

전역 `server.servlet.session.timeout: 10m`과 PH-49c의 `spring.session.timeout`은 고객 세션의
기본값이다. PH-49a-②(PR #639)의 `SessionLoginManager`는 관리자 로그인에
`app.security.admin.session-timeout: 30m`을 적용하고 `ROLE_ADMIN` 인증을 저장한다.
이 매니저는 역할별 만료시간을 세션에 적용하고 같은 값으로 `sessionExpiresAt`을 계산한다.
PH-49c의 Spring Session JDBC는 세션을 공용 MySQL에 저장한다. 두 변경을 통합한
`LoginApiIntegrationTest`는 관리자 로그인 응답의 만료시각과 JDBC 세션 행의 1800초를 검증한다.

**⑤ `GET /customers/me`에 `role`·`permissions[]` 추가**

저장은 행이고 API 계약은 **배열**이다. 고객은 `role: "CUSTOMER"`, `permissions: []`로 나간다
(NULL이 아니라 빈 배열).

## 2. 스키마

`V202610152100`가 `customer.role`과 `ck_customer_role`을, `V202610152101`이 공통코드
(`CUSTOMER_ROLE` 2행 · `ADMIN_PERMISSION` 5행)를, `V202610152102`가 `admin_permission` 테이블을
넣는다. 파일을 나눈 이유는 MySQL DDL이 암묵적 커밋이라 한 파일에서 ALTER 성공 후 INSERT가
실패하면 컬럼만 남고 이력은 실패로 기록되기 때문이다.

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
-- 운영 관리자는 한 명으로 시작한다. 계정을 더 만들 때도 이 SQL 을 쓴다 —
-- PH-49b 는 이미 있는 관리자의 권한을 주고 빼는 API 라 계정 생성과 role 지정은 못 한다.
INSERT INTO customer (
    user_id, password_hash, user_name, birth_date, email, phone_number,
    login_failure_count, account_locked, status, role,
    joined_at, created_at, updated_at
) VALUES (
    :user_id, :bcrypt_hash, :user_name, :birth_date, :email, :phone_number,
    0, FALSE, 'ACTIVE', 'ADMIN',
    NOW(6), NOW(6), NOW(6)
);

-- 권한은 행으로 넣는다. 조회 전용 관리자면 READ 세 줄만 넣는다.
INSERT INTO admin_permission (customer_id, permission, granted_at)
SELECT customer_id, p.permission, NOW(6)
  FROM customer c
  JOIN (
        SELECT 'GL_READ' AS permission
         UNION ALL SELECT 'GL_WRITE'
         UNION ALL SELECT 'CUSTOMER_READ'
         UNION ALL SELECT 'CUSTOMER_WRITE'
         UNION ALL SELECT 'AUDIT_READ'
       ) p
 WHERE c.user_id = :user_id;

-- 확인
SELECT c.user_id, c.role, GROUP_CONCAT(ap.permission ORDER BY ap.permission) AS permissions
  FROM customer c
  LEFT JOIN admin_permission ap ON ap.customer_id = c.customer_id
 WHERE c.role = 'ADMIN'
 GROUP BY c.customer_id;
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

> **FE #136 주의** — ②부터 **관리자는 `POST /auth/login`으로 로그인되지 않는다**(결정 ③). 훅을
> `/admin/auth/login`으로 바꾸기 전까지 관리자 로그인이 `ATH0101`로 막히므로, ② 머지와 훅 교체를
> 같이 붙여야 한다.

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

- `/admin/auth/login` 컨트롤러와 `role=ADMIN` 검증. `SessionLoginManager`를 확장해
  관리자 인증에 `ROLE_ADMIN`을 부여하고 `app.security.admin.session-timeout`을 적용한다(PR #639).
- **`/admin/auth/login`을 `/admin/**` 규칙보다 앞에 `permitAll` + CSRF 예외로 둔다.** 안 하면 로그인
  요청 자체가 관리자 인가에 걸린다. 구현체는 필터가 아니라 `AuthorizationManager`다
- `/admin/**` 인가: ADMIN이 아니면 403, 변경 메서드는 `*_WRITE` 검사. URL 접두어 → 권한 매핑표 작성
- **S0 임시 가드 제거** — `AdminBootstrapAuthorizationManager`·`AdminBootstrapProperties`·
  `SecurityConfig` 인가 한 줄·`application.yml`의 `app.security.admin.bootstrap-customer-ids`·
  관련 테스트 3건·`SecurityConfigTest` 관리자 3건 재작성
- `CustomerJpaEntity` 매핑과 `CustomerInfoResponse` 필드 추가
- 관리자 로그인·로그아웃 감사로그에 actor role 포함(`AuditEventType.LOGIN`).
  **요구 역할도 `detail.requiredRole`로 함께 남긴다** — 실패 응답이 고객 로그인과 같아서(결정 ③)
  이것이 없으면 어느 경로로 두드렸는지 사후에 가릴 수 없다. 계정을 못 찾은 실패는 actor role이
  `UNKNOWN`이고, 역할을 가리지 않는 고객 로그인은 요구 역할이 `ANY`다 (PR #639 리뷰 반영)
