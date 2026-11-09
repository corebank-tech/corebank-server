# 📐 CoreBank 미니 코어뱅킹 — 테이블 스키마 레퍼런스

**DBMS**: MySQL 8.4 · InnoDB · `utf8mb4_0900_ai_ci`
**대상**: 58개 비즈니스 테이블 + 9개 비즈니스 외 테이블 (`ledger_entry_id_sequence`, `batch_execution_lock`, `batch_execution_history`, `business_date_cutoff`, `cob_checkpoint`, `audit_archive_execution`, `SPRING_SESSION`, `SPRING_SESSION_ATTRIBUTES`, `EVENT_PUBLICATION`), 테이블 목록 기준 592개 컬럼
**근거 DDL**: `src/main/resources/db/migration/` 내 V 파일들

> 순수 스키마 레퍼런스입니다. 개정 이력·감축 근거·확인 필요 항목은 [DB_ERD_v3.md](corebank_erd.md)에 있습니다.

## 표기 규약

| 표기 | 의미 |
| --- | --- |
| **PK** | 기본키 |
| **FK** | 외래키. `Null` 열 옆에 참조 대상을 적었다 |
| **UK** | 유니크 키 |
| `Null` = X | `NOT NULL` |
| `Null` = O | `NULL` 허용 |

금액은 전부 `BIGINT`(원 단위 정수), 금리는 `DECIMAL(5,2)`(연 %), 계좌번호는 `CHAR(12)`, 거래번호는 `CHAR(20)`입니다.

시각은 `DATETIME(6)`입니다. MySQL의 `DATETIME`은 시간대 정보를 갖지 않는 벽시각(wall-clock) 그대로를 저장하며 자동 UTC 변환이 없습니다 — KST는 애플리케이션(Hibernate `hibernate.jdbc.time_zone`, JDBC `connectionTimeZone`, `JpaAuditingConfig`의 `Clock`)이 값을 쓰고 읽을 때 지키는 저장·표시 계약입니다.

---

## 테이블 목록

| # | 테이블 | 설명 | 담당 | 컬럼 |
| --- | --- | --- | --- |----|
| 1 | `customer` | 고객 | P6 | 23 |
| 2 | `terms` | 약관 | P6 | 10 |
| 3 | `customer_terms_agreement` | 회원가입 약관 동의 | P6 | 4  |
| 4 | `verification_request` | 인증 요청 | P6 | 13 |
| 5 | `notification` | 알림 | P1 | 9  |
| 6 | `product` | 상품 | P3 | 25 |
| 7 | `product_rate_tier` | 상품 기간별 금리 | P3 | 3  |
| 8 | `product_preferential_rate` | 상품 우대금리 | P3 | 4  |
| 9 | `product_terms` | 상품-약관 연결 | P3 | 3  |
| 10 | `account` | 계좌 | P2 | 23 |
| 11 | `account_number_sequence` | 계좌번호 채번 규칙 | P2 | 8  |
| 12 | `transaction_sequence` | 거래번호 일련번호 채번 | P4 | 4  |
| 13 | `transfer` | 이체 거래 | P4 | 26 |
| 14 | `ledger_entry` | 원장 | P4 | 14 |
| 15 | `ledger_entry_id_sequence` | 원장 PK 전용 채번 | P4 | 1  |
| 16 | `favorite_account` | 자주 쓰는 계좌 | P4 | 6  |
| 17 | `transfer_limit` | 이체한도 | P1 | 5  |
| 18 | `transfer_limit_daily_usage` | 일별 한도 사용액 | P1 | 5  |
| 19 | `transfer_limit_history` | 이체한도 변경 이력 | P1 | 6  |
| 20 | `product_subscription` | 상품가입 | P3 | 19 |
| 21 | `subscription_terms_agreement` | 상품 약관 동의 | P3 | 5  |
| 22 | `scheduled_transfer` | 예약이체 | P3 | 18 |
| 23 | `auto_transfer` | 자동이체 등록 | P5 | 18 |
| 24 | `auto_transfer_execution` | 자동이체 회차 실행결과 | P5 | 8  |
| 25 | `idempotency_key` | 멱등키 | P5 | 9  |
| 26 | `audit_log` | 감사 로그 | P5 | 10 |
| 27 | `common_code` | 공통코드 | P5 | 8  |
| 28 | `batch_execution_lock` | 배치 중복 트리거 방지 락 | P5 | 3  |
| 29 | `gl_account` | 계정과목 | P3 | 6  |
| 30 | `gl_voucher` | 전표 | P3 | 6  |
| 31 | `gl_journal_entry` | 분개 | P3 | 8  |
| 32 | `business_date` | 현재 영업일 | P5 | 4  |
| 33 | `holiday` | 휴일 달력 | P5 | 4  |
| 34 | `gl_voucher_sequence` | 전표번호 일련번호 채번 | P3 | 4  |
| 35 | `auth_token` | 일회성 인증 토큰 | P4 | 9  |
| 36 | `otp_issue_lock` | OTP 발급 잠금 | P4 | 3  |
| 37 | `terms_view_history` | 상품 약관 열람 이력 | P4 | 4  |
| 38 | `batch_execution_history` | 배치 스텝 실행 기록 | P5 | 8  |
| 39 | `ledger_reconciliation_mismatch` | 원장-잔액 대사 불일치 목록 | P4 | 6  |
| 40 | `admin_permission` | 관리자 권한 | P1 | 4  |
| 41 | `account_status_history` | 계좌 상태 전이 이력 | P2 | 8 |
| 42 | `business_date_cutoff` | 영업일 마감 cutoff | P5 | 4  |
| 43 | `interest_payment` | 예적금 이자 지급 이력 | P2 | 9 |
| 44 | `SPRING_SESSION` | 공용 HTTP 세션 | P6 | 7 |
| 45 | `SPRING_SESSION_ATTRIBUTES` | 직렬화된 세션 속성 | P6 | 3 |
| 46 | `EVENT_PUBLICATION` | 이벤트 발행 기록 (Spring Modulith) | P1 | 9 |
| 47 | `cob_checkpoint` | COB 재시작 체크포인트 | P5 | 5 |
| 48 | `audit_archive_execution` | 감사 로그 WORM 적재 상태 | P1 | 9 |
| 49 | `audit_integrity_mismatch` | 감사 로그 무결성 불일치 목록 | P1 | 5 |
| 50 | `transfer_hold` | 이체 보류 | P4 | 20 |
| 51 | `delayed_transfer_setting` | 고객별 지연이체 설정 | P4 | 6 |
| 52 | `account_payment_stop` | 계좌 사건별 지급정지 기록 | P3 | 10 |
| 53 | `unregistered_login_failure` | 미가입 아이디 로그인 실패 횟수 | P1 | 4 |
| 54 | `payment_stop_case` | 지급정지 사건 | P3 | 8 |
| 55 | `case_disputed_transfer` | 지급정지 사건의 다툼 거래 | P3 | 5 |
| 56 | `objection` | 지급정지 이의제기 | P3 | 18 |
| 57 | `objection_decision` | 이의제기 판정 | P3 | 19 |
| 58 | `gate_evaluation` | 해제 게이트 계산 기록 | P3 | 9 |
| 59 | `agent_run` | 이의제기 Agent 실행 | P3 | 15 |
| 60 | `agent_run_step` | Agent 실행 단계 이벤트 | P3 | 10 |
| 61 | `review_draft` | Agent 판정안 | P3 | 6 |
| 62 | `objection_supplement_request` | 이의제기 보완 요청 | P3 | 10 |
| 63 | `objection_review_session` | 이의제기 심사 시작 기록 | P3 | 4 |
| 64 | `objection_external_check` | 이의제기 외부 확인(모의) | P3 | 8 |
| 65 | `objection_notice` | 이의제기 통지 기록 | P3 | 7 |
| 66 | `objection_document` | 이의제기 첨부 서류 | P3 | 13 |
| 67 | `objection_document_ocr` | 이의제기 증빙 OCR 결과 | P3 | 5 |

---

# 1. 고객 · 인증 — P6 (`admin_permission` · `notification`은 P1)

## `SPRING_SESSION` / `SPRING_SESSION_ATTRIBUTES` (PH-49c)

Spring Session JDBC 표준 스키마. Flyway `V202610221420`으로만 생성하며 JPA 엔티티를 만들지 않는다.
세션의 시각은 프레임워크 계약에 따라 **UTC epoch 밀리초 BIGINT**이고 업무 테이블의 KST DATETIME(6) 규약의 예외다.

| 테이블 | 컬럼 | 타입 | 키·NULL | 의미 |
|---|---|---|---|---|
| SPRING_SESSION | PRIMARY_ID | CHAR(36) | PK, NOT NULL | 내부 세션 행 UUID |
| SPRING_SESSION | SESSION_ID | CHAR(36) | UK, NOT NULL | 쿠키로 연결하는 세션 ID |
| SPRING_SESSION | CREATION_TIME | BIGINT | NOT NULL | 생성 epoch ms |
| SPRING_SESSION | LAST_ACCESS_TIME | BIGINT | NOT NULL | 마지막 접근 epoch ms |
| SPRING_SESSION | MAX_INACTIVE_INTERVAL | INT | NOT NULL | 무조작 만료 초: 고객 600, 관리자 1800(PH-49a-② 로그인 정책) |
| SPRING_SESSION | EXPIRY_TIME | BIGINT | INDEX, NOT NULL | 만료 epoch ms |
| SPRING_SESSION | PRINCIPAL_NAME | VARCHAR(100) | INDEX, NULL 허용 | 인증 고객 PK 문자열, 익명 세션은 NULL |
| SPRING_SESSION_ATTRIBUTES | SESSION_PRIMARY_ID | CHAR(36) | 복합 PK·FK, NOT NULL | PRIMARY_ID 참조, ON DELETE CASCADE |
| SPRING_SESSION_ATTRIBUTES | ATTRIBUTE_NAME | VARCHAR(200) | 복합 PK, NOT NULL | SPRING_SECURITY_CONTEXT 등 속성 이름 |
| SPRING_SESSION_ATTRIBUTES | ATTRIBUTE_BYTES | BLOB | NOT NULL | 직렬화한 속성 값 |

인덱스: `SPRING_SESSION_IX1(SESSION_ID)` UNIQUE, `SPRING_SESSION_IX2(EXPIRY_TIME)`,
`SPRING_SESSION_IX3(PRINCIPAL_NAME)`. PRINCIPAL_NAME은 고객 FK가 아닌 프레임워크 검색 인덱스다.
로그아웃은 해당 세션만 삭제하고 고객 비밀번호 변경은 고객 PK로 모든 세션을 삭제한다.
세션 삭제는 해당 업무 트랜잭션에 참여하므로 업무 롤백 시 함께 롤백한다. 만료 행 정리는 매분이며 인증 만료는 즉시 적용된다.

## `customer`

> 고객

접속 이력(구 `login_history`)을 흡수했다. 대시보드가 쓰는 값이 직전·현재 2건뿐이라 별도 테이블을 두지 않는다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `customer_id` | `BIGINT` | **PK** | X |  | 고객 내부 식별자. 명세의 `customerId`. 클라이언트에 노출되는 유일한 고객 키 |
| `user_id` | `VARCHAR(20)` | **UK** | X |  | 고객이 직접 정한 로그인 아이디. 내부 PK인 `customer_id`와 다른 화면 입력값이다 |
| `existing_bank_customer_id` | `VARCHAR(100)` | **UK** | O |  | 실명계좌 인증으로 확인한 기존 은행 원장의 고객 식별자. 재가입 판정 기준(ATH0303). 실서비스의 CI 대리 키이며, 원장 인증 없이 만들어진 기존 행은 NULL |
| `password_hash` | `CHAR(60)` |  | X |  | 로그인 비밀번호의 BCrypt 해시. 평문·복호화 가능 암호화 금지 |
| `user_name` | `VARCHAR(50)` |  | X |  | 고객 실명. 응답 시 이름 길이별 마스킹 적용 (2자 `이*` / 3자 `홍*동` / 4자 이상 `남**수`) |
| `birth_date` | `DATE` |  | X |  | 생년월일. 실명계좌 인증·아이디 찾기의 대조값. 로그 평문 기록 금지 |
| `email` | `VARCHAR(100)` | **UK** | X |  | 이메일 주소. 연락처 겸 인증 수단. 중복 가입 시도 시 ATH0302 |
| `phone_number` | `VARCHAR(11)` |  | X |  | 휴대폰 번호. 하이픈 없이 숫자만 저장하고 응답 시 중간 4자리를 마스킹한다 |
| `login_failure_count` | `TINYINT` |  | X | `0` | 로그인 비밀번호 연속 오류 횟수. 5회 도달 시 `account_locked`가 TRUE로 바뀐다 (ATH0102) |
| `account_locked` | `BOOLEAN` |  | X | `FALSE` | 계정 잠금 여부. `login_failure_count` 5회 도달 시 TRUE, 관리자 잠금 해제로 FALSE 복귀 |
| `status` | `VARCHAR(12)` |  | X | `ACTIVE` | 계정 상태. `ACTIVE`(정상) / `SUSPENDED`(이용정지). 관리자만 바꾼다. SUSPENDED면 비밀번호가 맞아도 로그인을 거부한다(ATH0106). 비밀번호 5회 오류 잠금(`account_locked`)과 별개이며 둘이 동시에 걸릴 수 있다 |
| `role` | `VARCHAR(12)` |  | X | `CUSTOMER` | 계정 구분. `CUSTOMER`(고객) / `ADMIN`(관리자). 관리자 로그인은 `/admin/auth/login`으로 분리되고 이 값이 `ADMIN`인 계정만 통과한다 |
| `last_login_at` | `DATETIME(6)` |  | O |  | 가장 최근 로그인 시각. 대시보드의 `currentLoginAt` |
| `last_login_ip` | `VARCHAR(45)` |  | O |  | 가장 최근 로그인 IP. 대시보드의 `currentLoginIp` |
| `previous_login_at` | `DATETIME(6)` |  | O |  | 직전 로그인 시각. 대시보드의 `previousLoginAt`. 부정 접속을 고객이 알아채는 단서 |
| `password_changed_at` | `DATETIME(6)` |  | O |  | 로그인 비밀번호 최종 변경 일시. 직전 비밀번호 재사용 제한(ATH0003) 판정 보조 |
| `joined_at` | `DATETIME(6)` |  | X |  | 회원가입 완료 일시. 고객정보 조회의 `joinedAt` |
| `created_at` | `DATETIME(6)` |  | X |  | 행 생성 일시 |
| `updated_at` | `DATETIME(6)` |  | X |  | 행 최종 수정 일시 |
| `email_ciphertext` | `VARCHAR(768)` | | O | | 이메일 AES-256-GCM 암호문. PH-18 전환 중에는 NULL 허용 |
| `phone_number_ciphertext` | `VARCHAR(128)` | | O | | 휴대폰 번호 AES-256-GCM 암호문. PH-18 전환 중에는 NULL 허용 |
| `email_search_hash` | `CHAR(64)` | **UK** | O | | 정규화 이메일의 HMAC-SHA256 검색 해시. 암호화 키와 별도 키 사용 |
| `admin_permission_version` | `BIGINT` | | X | `0` | 관리자 권한 회수 때마다 1씩 증가. 로그인 때 세션에 실은 값과 다르면 그 세션은 낡은 권한을 들고 있어 거부한다 |

**인덱스**

| 종류 | 이름 | 컬럼 |
| --- | --- | --- |
| UNIQUE | `uk_customer_user_id` | `user_id` |
| UNIQUE | `uk_customer_email` | `email` |
| UNIQUE | `uk_customer_existing_bank_customer_id` | `existing_bank_customer_id` |
| UNIQUE | `uk_customer_email_search_hash` | `email_search_hash` |
| UNIQUE | `uk_customer_id_role` | `(customer_id, role)` — `admin_permission`의 복합 FK 대상(PH-49b) |

**CHECK 제약**

| 이름 | 조건 | 설명 |
| --- | --- | --- |
| `ck_customer_status` | `status IN ('ACTIVE', 'SUSPENDED')` | 계정 상태는 정상·이용정지 중 하나여야 한다. |
| `ck_customer_role` | `role IN ('CUSTOMER', 'ADMIN')` | 계정 구분은 고객·관리자 중 하나여야 한다. |

---

## `admin_permission`

> 관리자 권한 (#561)

행 하나가 권한 하나다. 권한이 없는 관리자는 행이 0개이며, 관리자인지 아닌지는 `customer.role`로 가린다.
CSV 한 칸에 담지 않는 이유는 오타를 DB가 막지 못하고 `common_code`의 표시명과 값이 이어지지 않기
때문이다(PR #572 리뷰). 부여는 `POST /admin/customers/{customerId}/permissions`, 회수는 `DELETE /admin/customers/{customerId}/permissions/{permission}`(PH-49b)가 맡는다.

| 컬럼 | 타입 | 키 | NULL | 기본값 | 설명 |
| --- | --- | --- | --- | --- | --- |
| `customer_id` | `BIGINT` | **PK, FK** | X |  | 권한을 가진 관리자. `customer(customer_id, role)` 참조 |
| `role` | `VARCHAR(12)` | FK | X | `'ADMIN'` | 항상 `ADMIN`. `customer.role`과 함께 복합 FK를 이뤄 `CUSTOMER` 계정에 권한 행이 붙는 것을 막는다. 엔티티에 매핑하지 않고 DB 기본값을 쓴다 |
| `permission` | `VARCHAR(20)` | **PK** | X |  | 권한 하나. `GL_READ`·`GL_WRITE`·`CUSTOMER_READ`·`CUSTOMER_WRITE`·`AUDIT_READ`·`PAYMENT_STOP_READ`·`PAYMENT_STOP_WRITE`·`PAYMENT_STOP_APPROVE`. `common_code`의 `ADMIN_PERMISSION` 그룹과 값이 같다 |
| `granted_at` | `DATETIME(6)` |  | X |  | 권한을 준 시각. 회수는 행 삭제라 이력은 `audit_log`가 맡는다 |

**기본키** — `(customer_id, permission)` 복합. 같은 권한이 두 번 들어가지 않는다.

**외래키**

| 이름 | 컬럼 | 참조 |
| --- | --- | --- |
| `fk_admin_permission_admin` | `(customer_id, role)` | `customer(customer_id, role)` — 권한 행이 있는 관리자는 `CUSTOMER`로 바꿀 수 없다 |

**CHECK 제약**

| 이름 | 조건 | 설명 |
| --- | --- | --- |
| `ck_admin_permission_role` | `role = 'ADMIN'` | 권한은 관리자에게만 붙는다. |
| `ck_admin_permission` | `permission COLLATE utf8mb4_0900_as_cs IN ('GL_READ', 'GL_WRITE', 'CUSTOMER_READ', 'CUSTOMER_WRITE', 'AUDIT_READ', 'PAYMENT_STOP_READ', 'PAYMENT_STOP_WRITE', 'PAYMENT_STOP_APPROVE')` | 고정 8종만 허용한다(지급정지 3종은 `V202611091400`). `common_code`는 `code_group`으로 여러 종류를 함께 담아 FK를 걸 수 없어 CHECK로 고정하고, 권한이 늘면 둘을 함께 바꾼다. |

---

## `terms`

> 약관 (회원가입·상품 공용)

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `terms_id` | `BIGINT` | **PK** | X |  | 약관 내부 식별자. **버전마다 별도 행**이므로 개정하면 새 ID가 발급된다 |
| `terms_code` | `VARCHAR(30)` | **UK** | X |  | 약관 종류 코드. 버전이 달라도 동일한 값 (예: `TERMS_PRIVACY`) |
| `version` | `VARCHAR(10)` | **UK** | X |  | 약관 버전 문자열. 상품가입 시 동의 버전 대조(PRD0006)의 기준 |
| `terms_type` | `VARCHAR(20)` |  | X |  | 약관 쓰임새 구분. `SIGNUP`(회원가입 약관) / `PRODUCT`(상품 가입 약관) |
| `title` | `VARCHAR(200)` |  | X |  | 약관 제목. 목록 화면 표시용 |
| `content` | `TEXT` |  | X |  | 약관 전문. 약관 본문 조회 응답의 `content` |
| `is_required` | `BOOLEAN` |  | X | `FALSE` | 필수 동의 여부. FALSE면 선택 약관이라 미동의해도 가입이 진행된다 |
| `view_required` | `BOOLEAN` |  | X | `FALSE` | 전문 열람 강제 여부. TRUE면 동의 전에 본문을 열어야 하며 이력이 없으면 PRD0005 |
| `created_at` | `DATETIME(6)` |  | X |  | 행 생성 일시 |
| `updated_at` | `DATETIME(6)` |  | X |  | 행 최종 수정 일시 |

**인덱스**

| 종류 | 이름 | 컬럼 |
| --- | --- | --- |
| UNIQUE | `uk_terms_code_version` | `terms_code, version` |

---

## `customer_terms_agreement`

> 회원가입 약관 동의

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `agreement_id` | `BIGINT` | **PK** | X |  | 동의 이력 내부 식별자 |
| `customer_id` | `BIGINT` | **FK** **UK** | X |  | 동의한 고객 → `customer.customer_id` |
| `terms_id` | `BIGINT` | **FK** **UK** | X |  | 동의 대상 약관. **버전이 포함된 행**을 가리킨다 → `terms.terms_id` |
| `agreed_at` | `DATETIME(6)` |  | X |  | 동의 시각. 분쟁 시 증거가 되므로 갱신하지 않고 행을 새로 쌓는다 |

**인덱스**

| 종류 | 이름 | 컬럼 |
| --- | --- | --- |
| UNIQUE | `uk_cta` | `customer_id, terms_id` |

---

## `verification_request`

> 인증 요청 (이메일·비밀번호재설정·OTP 통합). *AuthToken 은 `auth_token`

이메일 인증·비밀번호 재설정·OTP를 하나로 합쳤다. 셋 다 `코드 발급 → 검증 → 만료 → 1회용` 구조가 같고 식별자가 모두 String이라 통합이 가능했다. 검증 결과로 발급되는 `*AuthToken`은 이 테이블이 아니라 [`auth_token`](#auth_token)에 있다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `verification_request_id` | `VARCHAR(64)` | **PK** | X |  | 인증 요청 식별자. 용도별 접두어(`EVF_` 이메일 / `PRR_` 비밀번호 재설정 / `OTP_REQ_` OTP) + CSPRNG 난수. 명세의 `emailVerificationId`·`passwordResetRequestId`·`otpRequestId`가 모두 이 컬럼이다 |
| `purpose` | `VARCHAR(24)` |  | X |  | 인증 요청의 용도. `SIGN_UP` / `EMAIL_CHANGE` / `PASSWORD_RESET` / `OTP_TRANSACTION`. 세 요청을 한 테이블에 합친 구분 축 |
| `customer_id` | `BIGINT` | **FK** | O |  | 요청 고객. 회원가입 전 이메일 인증은 고객이 아직 없어 NULL → `customer.customer_id` |
| `target` | `VARCHAR(100)` |  | O |  | 인증 대상 값. 이메일 인증은 수신 이메일 주소가 들어가고 OTP는 비어 있다 |
| `code_hash` | `CHAR(60)` |  | X |  | 인증번호·OTP 6자리의 단방향 해시. 평문 저장·로그 기록 금지 |
| `transaction_type` | `VARCHAR(32)` |  | O |  | **OTP 전용.** 어떤 거래를 승인하려는 OTP인지 (`IMMEDIATE_TRANSFER` 등) |
| `transaction_data` | `JSON` |  | O |  | **OTP 전용.** 출금계좌·입금계좌·금액 등 거래 핵심 정보. 최종 거래 API가 요청 본문과 대조해 변조를 잡는다 (OTP0102) |
| `error_count` | `TINYINT` |  | X | `0` | 인증번호 연속 오류 횟수. 5회에 도달하면 `locked`가 TRUE가 된다 (OTP0103) |
| `locked` | `BOOLEAN` |  | X | `FALSE` | 잠금 여부. `error_count`가 5이면 TRUE. 재발급 전까지 검증 불가 (OTP0103) |
| `used` | `BOOLEAN` |  | X | `FALSE` | 사용 완료 여부. 검증 성공 시 TRUE로 바꿔 재사용을 막는다 |
| `verified_at` | `DATETIME(6)` |  | O |  | 검증 성공 시각. 미검증이면 NULL |
| `expires_at` | `DATETIME(6)` |  | X |  | 유효시간 만료 시각. 발급 시각 + 180초 |
| `created_at` | `DATETIME(6)` |  | X |  | 발급 요청 시각 |

**인덱스**

| 종류 | 이름 | 컬럼 |
| --- | --- | --- |
| INDEX | `ix_vr_customer` | `customer_id, purpose, created_at DESC` |
| INDEX | `ix_vr_target` | `target, purpose, created_at DESC` |

이메일 인증번호 발급은 `target(이메일) + purpose` 단위로 직렬화합니다. 같은 조합의 동시 재발급도 기존 활성 요청 무효화와 신규 요청 저장을 순서대로 수행하여, 마지막으로 처리된 요청 하나만 `used = FALSE` 상태로 남겨야 합니다. `SIGN_UP`과 `EMAIL_CHANGE`처럼 목적이 다른 요청은 서로 무효화하지 않습니다.

---

## `notification`

> 알림

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `notification_id` | `BIGINT` | **PK** | X |  | 알림 내부 식별자. 읽음 처리 API의 경로 변수 |
| `customer_id` | `BIGINT` | **FK** | X |  | 수신 고객 → `customer.customer_id` |
| `type` | `VARCHAR(24)` | **UK** | X |  | 알림을 발생시킨 업무 구분. `TRANSFER`(즉시 · 타행이체) / `SCHEDULED_TRANSFER`(예약이체) / `AUTO_TRANSFER`(자동이체) / `PRODUCT_SUBSCRIPTION`(상품가입) |
| `ref_id` | `BIGINT` | **UK** | O |  | 다형 참조: TRANSFER->transfer.transfer_id / SCHEDULED_TRANSFER->scheduled_transfer_id / AUTO_TRANSFER->auto_transfer_execution.execution_id / PRODUCT_SUBSCRIPTION->subscription_id |
| `title` | `VARCHAR(100)` |  | X |  | 알림 제목. 목록 화면의 한 줄 요약 |
| `content` | `VARCHAR(500)` |  | X |  | 알림 본문 문구 |
| `is_read` | `BOOLEAN` |  | X | `FALSE` | 읽음 여부. 미읽음 건수 조회의 집계 대상 |
| `read_at` | `DATETIME(6)` |  | O |  | 읽음 처리 시각. 미읽음이면 NULL |
| `occurred_at` | `DATETIME(6)` |  | X |  | 알림 발생 시각. 목록 정렬 기준 |

**인덱스**

| 종류 | 이름 | 컬럼 |
| --- | --- | --- |
| INDEX | `ix_noti_customer` | `customer_id, is_read, occurred_at DESC` — 미읽음 건수 집계 |
| INDEX | `ix_noti_customer_occurred` | `customer_id, occurred_at DESC, notification_id DESC` — 알림 목록 최신순 정렬 |
| UNIQUE | `uk_noti_type_ref` | `type, ref_id` — 구독자가 같은 이벤트를 두 번 받아도 알림은 1건(#706) |

---

# 2. 상품 — P3

## `product`

> 상품

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `product_id` | `BIGINT` | **PK** | X |  | 상품 내부 식별자. 상품 상세·가입 API의 `productId` |
| `product_code` | `VARCHAR(20)` | **UK** | X |  | 상품 업무 코드. 화면·시드 데이터가 상품을 지칭하는 안정적 키 |
| `product_name` | `VARCHAR(100)` |  | X |  | 화면에 노출하는 상품명 |
| `product_group` | `VARCHAR(12)` |  | X |  | SAVINGS(정기적금) / DEPOSIT(정기예금) |
| `deposit_type` | `VARCHAR(20)` |  | X |  | 납입 방식. `LUMP_SUM`(한 번에 예치) / `INSTALLMENT`(매월 납입) |
| `summary` | `VARCHAR(200)` |  | O |  | 상품 한 줄 요약. 목록 화면용 |
| `description` | `TEXT` |  | O |  | 상품 상세 설명. 상세 화면용 |
| `eligibility` | `TEXT` |  | O |  | 가입 자격 조건 안내 문구. 상세조회 `data.eligibility` |
| `subscription_restrictions` | `TEXT` |  | O |  | 가입 제한 사항 목록(JSON 배열 문자열). 상세조회 `data.subscriptionRestrictions`. 애플리케이션의 `StringListJsonConverter`가 `List<String>`으로 변환 |
| `notices` | `TEXT` |  | O |  | 유의사항 목록(JSON 배열 문자열). 상세조회 `data.notices`. `subscription_restrictions`와 동일한 컨버터 사용 |
| `base_rate` | `DECIMAL(5,2)` |  | X |  | 기본금리(연 %). 우대금리를 제외한 기준값 |
| `max_rate` | `DECIMAL(5,2)` |  | X |  | 최고금리(연 %). 우대 조건을 모두 채웠을 때의 상한. 목록 화면 표시용 |
| `min_amount` | `BIGINT` |  | X |  | 최소 가입금액. 미만이면 PRD0001 |
| `max_amount` | `BIGINT` |  | X |  | 최대 가입금액. 초과하면 PRD0001 |
| `amount_unit` | `BIGINT` |  | X |  | 가입금액 입력 단위. 이 값의 배수가 아니면 PRD0004 |
| `min_term_months` | `SMALLINT` |  | X |  | 최소 가입기간(개월). 미만이면 PRD0002 |
| `max_term_months` | `SMALLINT` |  | X |  | 최대 가입기간(개월). 초과하면 PRD0002 |
| `interest_pay_type` | `VARCHAR(20)` |  | X |  | 이자 계산 방식. `SIMPLE`(단리) / `COMPOUND`(복리) |
| `sale_status` | `VARCHAR(12)` |  | X |  | 판매 상태. `ON_SALE`(판매중) / `SUSPENDED`(판매중지). 판매중지 상품은 목록 조회에서는 빠지지만 상세 조회는 200으로 응답한다(즐겨찾기·공유 링크 진입 지원 목적, `saleStatus`로 프론트가 구분) |
| `sale_start_date` | `DATE` |  | O |  | 판매 시작일 |
| `sale_end_date` | `DATE` |  | O |  | 판매 종료일 |
| `new_flag` | `BOOLEAN` |  | X | `FALSE` | 신상품 뱃지 표시 여부 |
| `single_account_limit` | `BOOLEAN` |  | X | `FALSE` | 1인 1계좌 제한 상품인지. TRUE인 상품만 중복 가입 시 PRD0301을 던진다 |
| `created_at` | `DATETIME(6)` |  | X |  | 행 생성 일시 |
| `updated_at` | `DATETIME(6)` |  | X |  | 행 최종 수정 일시 |

**인덱스**

| 종류 | 이름 | 컬럼 |
| --- | --- | --- |
| UNIQUE | `uk_product_code` | `product_code` |
| INDEX | `ix_product_group_status` | `product_group, sale_status` |

**CHECK 제약**

| 이름 | 조건 |
| --- | --- |
| `ck_product_amount` | `min_amount <= max_amount AND min_amount > 0` |
| `ck_product_term` | `min_term_months <= max_term_months AND min_term_months > 0` |
| `ck_product_rate` | `base_rate <= max_rate AND base_rate >= 0` |

---

## `product_rate_tier`

> 상품 기간별 금리

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `product_id` | `BIGINT` | **PK** **FK** | X |  | 대상 상품 → `product.product_id` |
| `term_months` | `SMALLINT` | **PK** | X |  | 가입기간(개월). 이 기간을 골랐을 때 적용될 금리를 정의한다 |
| `rate` | `DECIMAL(5,2)` |  | X |  | 해당 기간에 적용되는 기본금리(연 %) |

---

## `product_preferential_rate`

> 상품 우대금리

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `product_id` | `BIGINT` | **PK** **FK** | X |  | 대상 상품 → `product.product_id` |
| `condition_code` | `VARCHAR(30)` | **PK** | X |  | 우대 조건 코드. 급여이체·자동이체 등록 등 조건을 식별한다 |
| `condition_name` | `VARCHAR(100)` |  | X |  | 우대 조건 설명. 화면 표시용 |
| `rate` | `DECIMAL(5,2)` |  | X |  | 조건 충족 시 가산되는 금리(연 %) |

---

## `product_terms`

> 상품-약관 연결

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보                                             |
| --- | --- | --- | --- | --- |----------------------------------------------------|
| `product_id` | `BIGINT` | **PK** **FK** | X |  | 대상 상품 → `product.product_id`                       |
| `terms_id` | `BIGINT` | **PK** **FK** | X |  | 연결된 약관 (버전 포함) → `terms.terms_id`                  |
| `display_order` | `SMALLINT` |  | X |  | 상품 상세조회에서 이 약관을 보여줄 순서(오름차순). — `terms` 자체의 속성이 아님 |

필수 동의 여부는 상품별 오버라이드 없이 `terms.is_required` 단일 기준으로 판단한다.

---

# 3. 계좌 — P2

## `account`

> 계좌 (별명·표시순서·출금등록 흡수)

계좌별명·표시순서(구 `account_preference`)와 출금계좌 등록(구 `withdrawal_account`)을 흡수했다. `balance`는 조회용 캐시이며 진실의 원천은 `ledger_entry`다.

| 컬럼                         | 타입            | 키      | Null | 기본값      | 담기는 정보                                                                            |
|----------------------------|---------------|--------|------|----------|-----------------------------------------------------------------------------------|
| `account_id`               | `BIGINT`      | **PK** | X    |          | 계좌 내부 식별자. 본인 계좌에만 사용한다 (타 고객 계좌는 번호로 지칭)                                         |
| `account_number`           | `CHAR(12)`    | **UK** | X    |          | 하이픈 없는 숫자 12자리                                                                    |
| `customer_id`              | `BIGINT`      | **FK** | X    |          | 예금주 → `customer.customer_id`                                                      |
| `product_id`               | `BIGINT`      | **FK** | O    |          | 입출금계좌는 NULL → `product.product_id`                                                |
| `account_type`             | `VARCHAR(24)` |        | X    |          | 계좌 종류. `DEMAND_DEPOSIT`(입출금) / `TIME_DEPOSIT`(정기예금) / `INSTALLMENT_SAVINGS`(정기적금) |
| `balance`                  | `BIGINT`      |        | X    | `0`      | 현재 잔액. **조회 성능용 캐시**이며 진실의 원천은 `ledger_entry` 합계다. 배치로 대사한다                       |
| `hold_amount` | `BIGINT` | | X | `0` | 지급정지 보류액. 출금가능액 계산 시 잔액에서 차감한다. 유효한 `account_payment_stop` 일부 지급정지 행의 `applied_amount` 합계로 계산한다(D-36) |
| `status`                   | `VARCHAR(12)` |        | X    | `ACTIVE` | 계좌 상태. `ACTIVE`(정상) / `SUSPENDED`(거래정지) / `MATURED`(만기 도달) / `CLOSED`(해지) / `DORMANT`(휴면)                            |
| `password_hash`            | `CHAR(60)`    |        | X    |          | 계좌비밀번호 4자리의 BCrypt 해시                                                             |
| `password_failure_count`   | `TINYINT`     |        | X    | `0`      | 계좌비밀번호 연속 오류 횟수. 검증 실패 응답의 `errorCount`                                           |
| `password_locked`          | `BOOLEAN`     |        | X    | `FALSE`  | 계좌비밀번호 잠금 여부. 5회 오류 시 TRUE가 되어 거래가 정지된다 (APW0101)                                 |
| `alias`                    | `VARCHAR(24)` |        | O    |          | 고객이 붙인 계좌별명. 계좌명 표시 시 상품명보다 우선한다                                                  |
| `display_order`            | `INT`         |        | O    |          | 고객이 지정한 계좌 목록 표시 순서                                                               |
| `withdrawal_registered`    | `BOOLEAN`     |        | X    | `FALSE`  | 출금계좌로 등록됐는지. 이체·상품가입의 출금 원천이 될 수 있는지를 가른다                                         |
| `withdrawal_registered_at` | `DATETIME(6)` |        | O    |          | 출금계좌 등록 시각. 미등록이면 NULL                                                            |
| `opened_date`              | `DATETIME(6)` |        | X    |          | 계좌 개설일                                                                            |
| `maturity_date`            | `DATE`        |        | O    |          | 만기일. 예·적금 계좌만 값이 있다                                                               |
| `closed_date`              | `DATETIME(6)` |        | O    |          | 해지일.                                                                              |
| `closed_trade_date`        | `DATE`        |        | O    |          | 해지 논리 영업일. 해지 트랜잭션이 영업일 gate(`BusinessDateGate`)로 읽은 값(기표를 다른 서비스에 맡기면 그 결과의 `tradeDate`)이며 `today()`로 따로 읽지 않는다. 정보계 CLOSE 이벤트 기준일이며 해지 구현(PH-15·16)이 채운다 |
| `last_transaction_at`      | `DATETIME(6)` |        | O    |          | 최근 거래 일시. 전체 계좌 조회의 `lastTransactionAt`                                           |
| `version`                  | `BIGINT`      |        | X    | `0`      | 낙관적 락 버전. 배치·조회 경로의 갱신 충돌을 잡는다 (이체 실행 경로는 비관적 락)                                  |
| `created_at`               | `DATETIME(6)` |        | X    |          | 행 생성 일시. JPA Auditing이 KST 기준으로 자동 설정                                             |
| `updated_at`               | `DATETIME(6)` |        | X    |          | 행 최종 수정 일시. JPA Auditing이 KST 기준으로 자동 설정                                          |

**인덱스**

| 종류 | 이름 | 컬럼 |
| --- | --- | --- |
| UNIQUE | `uk_account_number` | `account_number` |
| INDEX | `ix_account_customer` | `customer_id, status` |
| INDEX | `ix_account_withdrawal` | `customer_id, withdrawal_registered` |

**CHECK 제약**

| 이름                                    | 조건                                                                                                                                                   | 설명                                                 |
|---------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------|
| `ck_account_balance`                  | `balance >= 0`                                                                                                                                       | 계좌 잔액은 0원 이상이어야 한다.                                |
| `ck_account_hold_amount` | `hold_amount >= 0 AND hold_amount <= balance` | 보류액은 0원 이상이며 원장잔액을 초과하지 않는다. |
| `ck_account_number`                   | `account_number REGEXP '^[0-9]{12}$'`                                                                                                                | 계좌번호는 하이픈 없는 숫자 12자리여야 한다.                         |
| `ck_account_type`                     | `account_type IN ('DEMAND_DEPOSIT', 'TIME_DEPOSIT', 'INSTALLMENT_SAVINGS')`                                                                          | 계좌 유형은 입출금·정기예금·정기적금 중 하나여야 한다.                    |
| `ck_account_status`                   | `status IN ('ACTIVE', 'SUSPENDED', 'MATURED', 'CLOSED', 'DORMANT')`                                                                                    | 계좌 상태는 정상·거래정지·만기 도달·해지·휴면 중 하나여야 한다.                       |
| `ck_account_password_lock_state`      | 오류 횟수가 `0~4`이면 `password_locked = FALSE`, 오류 횟수가 `5`이면 `password_locked = TRUE`                                                                      | 계좌비밀번호 연속 오류 횟수와 잠금 상태가 항상 일치해야 한다.                |
| `ck_account_withdrawal_registered`    | `withdrawal_registered IN (FALSE, TRUE)`                                                                                                             | 출금계좌 등록 여부는 Boolean 값이어야 한다.                       |
| `ck_account_product`                  | `DEMAND_DEPOSIT`이면 `product_id IS NULL`<br>`TIME_DEPOSIT` 또는 `INSTALLMENT_SAVINGS`이면 `product_id IS NOT NULL`                                        | 입출금계좌에는 상품이 연결되지 않고, 예·적금계좌에는 상품이 반드시 연결되어야 한다.    |
| `ck_account_maturity`                 | `DEMAND_DEPOSIT`이면 `maturity_date IS NULL`<br>`TIME_DEPOSIT` 또는 `INSTALLMENT_SAVINGS`이면 `maturity_date IS NOT NULL AND maturity_date >= opened_date` | 입출금계좌에는 만기일이 없고, 예·적금계좌의 만기일은 개설일 이후여야 한다.         |
| `ck_account_withdrawal_type`          | `withdrawal_registered = FALSE OR account_type = 'DEMAND_DEPOSIT'`                                                                                   | 출금계좌 등록은 입출금계좌에만 허용한다.                             |
| `ck_account_withdrawal_registered_at` | 미등록이면 `withdrawal_registered_at IS NULL`<br>등록이면 `withdrawal_registered_at IS NOT NULL`                                                              | 출금계좌 등록 여부와 등록 일시의 존재 여부가 일치해야 한다.                 |
| `ck_account_closed_date`              | `CLOSED`이면 `closed_date IS NOT NULL AND closed_date >= opened_date`<br>`CLOSED`가 아니면 `closed_date IS NULL`                                           | 해지 계좌에는 개설일 이후의 해지일이 있어야 하며, 미해지 계좌에는 해지일이 없어야 한다. |

---

## `account_status_history`

> 계좌 상태 전이 감사 이력 (PH-15, #671)

계좌 상태 변경 시 전이 전·후 상태와 처리 시각을 기록하는 append-only 테이블이다.
COB의 `ACTIVE → MATURED` 전이, 고객 요청 만기해지의 `MATURED → CLOSED` 전이, 지급정지 적용과 전환과 해제로 바뀐 `ACTIVE ↔ SUSPENDED` 전이를 기록한다. 지급정지로 상태가 바뀌지 않으면(원래 `SUSPENDED` 계좌 등) 남기지 않는다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `history_id` | `BIGINT` | **PK** | X | `AUTO_INCREMENT` | 이력 식별자 |
| `account_id` | `BIGINT` | INDEX | X | | 상태가 변경된 계좌 ID |
| `previous_status` | `VARCHAR(12)` | | X | | 변경 전 계좌 상태. 만기 전이는 `ACTIVE`, 만기해지는 `MATURED` |
| `new_status` | `VARCHAR(12)` | | X | | 변경 후 계좌 상태. 만기 전이는 `MATURED`, 만기해지는 `CLOSED` |
| `maturity_date` | `DATE` | | O | | 계약 만기일 |
| `effective_business_date` | `DATE` | | O | | 만기 전이는 COB에서 계산한 만기 전이 기준일, 만기해지는 MATURITY 출금 원장의 실제 거래일(`tradeDate`) |
| `changed_at` | `DATETIME(6)` | INDEX | X | | 실제 상태 전이 처리 시각 |
| `trigger_type` | `VARCHAR(20)` | | X | | 전이 발생 원인. `COB`(배치 만기 전이) / `CUSTOMER`(고객 요청 만기해지) / `PAYMENT_STOP`(지급정지 적용, 전환, 해제) |

**인덱스**

| 종류 | 이름 | 컬럼 |
| --- | --- | --- |
| PRIMARY | `PRIMARY` | `history_id` |
| INDEX | `ix_account_status_history_account_time` | `account_id, changed_at` |

---

## `account_payment_stop`

> 계좌 사건별 지급정지 기록 (이의제기 AI 심사 지원, [D-36](phase2/README.md#d-36--113--리드))

`account.api.PaymentStopUseCase`가 쓰는 사건별 지급정지 기록이다. 계좌의 `status`와 `hold_amount`는 이 테이블의 유효한 행으로 계산한다.
전부 지급정지(`FULL`)가 하나라도 있으면 `SUSPENDED`, 일부 지급정지(`PARTIAL`)만 있으면 정지 전 상태에 `applied_amount` 합계만큼 보류한다. 마지막 행이 해제되면 `status_before`로 돌아간다.
해제해도 행을 지우지 않는다. 다른 사건의 지급정지 이력을 보는 데 쓴다.

- **이력 책임 경계:** 이 테이블은 사건별 최종 상태와 생성, 해제 시각만 둔다. 일부 지급정지 전환 시각, 확정한 담당자, 판정 근거는 paymentstop 도메인의 판정 이력과 감사 로그(`audit_log`)가 남긴다. `account_status_history`에는 계좌 상태가 실제로 바뀐 경우만 남아, 여러 사건이 겹쳐 상태가 그대로인 전환과 해제는 남지 않는다.
- **선행 조건:** 일부 지급정지를 운영에서 쓰려면 #521(락 이후 재검증을 출금가능액 기준으로)이 먼저 머지돼야 한다. 그 전에는 이체와 보류액 변경이 경합하면 `ck_account_hold_amount` 위반으로 이체가 DB 오류로 끝난다.
- **입금 정책:** 전부 지급정지 계좌는 `SUSPENDED`라 출금뿐 아니라 입금(이체 수취, 상품 입금)도 `TRF0301`로 거부한다. 법령상 지급정지(「전기통신금융사기 피해 방지 및 피해금 환급에 관한 특별법」 제4조)는 지급을 막는 조치라 금융권은 입금을 받는다. 그래도 `SUSPENDED`를 쓰는 근거는 둘이다. 입금을 받으려면 `transfer`의 수취 계좌 검사(`TransferExecutionService`, `LedgerDepositService`의 `ACTIVE` 검사)가 지급정지만 통과시키도록 상태나 표시를 새로 나눠야 해 이 계약의 범위(이체와 입금 경로 무변경)를 넘는다. 또 이의제기 심사와 해제에 필요한 것은 출금 차단이고, 입금 차단은 명의인의 정상 입금을 막는 불편이 있어도 돈이 빠져나가는 경로를 열지 않는다. 알려진 한계로 공개하며, 1원 송금 뒤 신고로 계좌를 묶는 수법은 시연에서 합성 데이터로 재현한다. 운영에서 입금을 받아야 하면 출금만 막는 표현을 P2(계좌 상태), P4(이체 수취 검사)와 정하고 두 경로를 함께 고친다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `payment_stop_id` | `BIGINT` | **PK** | X | `AUTO_INCREMENT` | 기록 식별자 |
| `account_id` | `BIGINT` | **FK** **UK** | X | | 지급정지된 계좌 → `account.account_id` |
| `case_id` | `BIGINT` | **UK** | X | | 지급정지 사건 ID → `payment_stop_case.case_id`(논리 관계). 이 테이블은 `account` 도메인 소유이고 사건은 `paymentstop` 도메인 소유라, 도메인 간에는 `api`로만 잇는다는 규칙(AGENTS.md 규칙 2)에 따라 FK를 걸지 않는다 |
| `stop_type` | `VARCHAR(10)` | | X | | `FULL`(전부 지급정지) / `PARTIAL`(일부 지급정지) |
| `requested_amount` | `BIGINT` | | X | `0` | 일부 지급정지 요청 보류액. `FULL`은 0 |
| `applied_amount` | `BIGINT` | | X | `0` | 실제로 묶은 보류액. 잔액에서 다른 사건 보류액을 뺀 금액이 상한이다. `FULL`은 0 |
| `status_before` | `VARCHAR(12)` | | X | | 첫 지급정지를 걸기 전 계좌 상태(`ACTIVE` / `SUSPENDED`). 같은 계좌의 유효한 행은 모두 같은 값이다 |
| `active` | `BOOLEAN` | | X | `TRUE` | 유효 여부. 해제하면 `FALSE` |
| `created_at` | `DATETIME(6)` | | X | | 지급정지를 건 시각 |
| `released_at` | `DATETIME(6)` | | O | | 해제 시각. 유효한 행은 NULL |

**인덱스**

| 종류 | 이름 | 컬럼 / 조건 |
| --- | --- | --- |
| PRIMARY | `PRIMARY` | `payment_stop_id` |
| UNIQUE | `uk_account_payment_stop_account_case` | `account_id, case_id` |
| FK | `fk_aps_account` | `account_id` → `account.account_id` |
| CHECK | `ck_aps_stop_type` | `stop_type IN ('FULL', 'PARTIAL')` |
| CHECK | `ck_aps_status_before` | `status_before IN ('ACTIVE', 'SUSPENDED')` |
| CHECK | `ck_aps_amount` | `requested_amount >= 0 AND applied_amount >= 0 AND applied_amount <= requested_amount` |
| CHECK | `ck_aps_released` | 유효하면 `released_at IS NULL`, 해제됐으면 `released_at IS NOT NULL` |

---

## `payment_stop_case` · `case_disputed_transfer` · `objection` · `objection_decision` · `gate_evaluation`

> 지급정지 사건과 이의제기 심사 (D-36, PA-04)

`paymentstop` 도메인의 사건과 심사 기록이다. 계좌 상태와 보류액의 정본은 `account_payment_stop`이고 이 표들은 `account.api`로만 계좌를 바꾼다. `account`에는 FK를 걸지 않는다.
판정 확정은 해제 게이트를 계좌 락 밖에서 계산한 뒤, `objection` 행을 잠그고 판정, 계좌 반영, 게이트 기록, 감사, 통지 기록을 한 트랜잭션에 남긴다.
수용과 일부 지급정지는 작성하면 승인 대기(`PENDING_APPROVAL`)로만 저장되고, 작성자와 다른 관리자가 승인할 때 계좌에 반영된다(PA-10, 2인 승인). 불수용은 작성 즉시 확정된다.

**`payment_stop_case`** 지급정지 사건

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `case_id` | `BIGINT` | **PK** | X | `AUTO_INCREMENT` | 사건 ID. `account_payment_stop.case_id`가 이 값이다 |
| `account_id` | `BIGINT` | INDEX | X | | 지급정지 계좌 |
| `sender_account_id` | `BIGINT` | | O | | 다툼 거래 송금인의 당행 계좌. 타행이거나 송금인이 여럿이면 NULL(관계 인정 불가) |
| `objection_deadline` | `DATE` | | X | | 이의제기 신청 기한. 담당자가 등록 때 넣는다 |
| `status` | `VARCHAR(12)` | | X | | `STOPPED` 전부 지급정지 / `PARTIAL` 일부 지급정지 / `RELEASED` 해제 |
| `registered_by` | `BIGINT` | | X | | 등록한 관리자 `customer_id` |
| `created_at` | `DATETIME(6)` | | X | | 생성 시각 |
| `updated_at` | `DATETIME(6)` | | X | | 수정 시각 |

**`case_disputed_transfer`** 사건의 다툼 거래. 등록 때 그 계좌의 최근 180일 성공 입금인지 확인하고 원장 값을 남긴다

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `disputed_transfer_id` | `BIGINT` | **PK** | X | `AUTO_INCREMENT` | 식별자 |
| `case_id` | `BIGINT` | FK | X | | `payment_stop_case.case_id` |
| `transaction_number` | `CHAR(20)` | **UK** | X | | 다툼 입금 거래번호. 한 거래는 한 사건에만 |
| `amount` | `BIGINT` | | X | | 등록 시점 원장 금액 |
| `occurred_at` | `DATETIME(6)` | | X | | 등록 시점 원장 시각 |

**`objection`** 이의제기. 사건당 1건

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `objection_id` | `BIGINT` | **PK** | X | `AUTO_INCREMENT` | 이의제기 ID |
| `case_id` | `BIGINT` | **UK**, FK | X | | `payment_stop_case.case_id` |
| `clause` | `TINYINT` | | X | | 사유 조항(법 제7조제1항 1~3호). 해제 범위를 정한다 |
| `statement` | `TEXT` | | X | | 명의인 소명 원문. 상세 조회는 조회 사유와 감사(`OBJECTION_DETAIL_VIEW`)를 남긴다 |
| `simplified_requested` | `BOOLEAN` | | X | | 간소화(일부 지급정지) 절차 신청 |
| `id_copy_confirmed` | `BOOLEAN` | | X | | 신분증 사본 확인(시행령 제7조). 확인 없이 접수하지 않는다 |
| `litigation_pending` | `BOOLEAN` | | X | | 명의인과 피해자 사이 소송 계속. 참이면 수용과 일부 지급정지를 닫는다 |
| `status` | `VARCHAR(20)` | | X | | `RECEIVED` 접수 / `UNDER_REVIEW` 심사 중 / `SUPPLEMENT_REQUESTED` 보완 요청 / `SUPPLEMENT_SUBMITTED` 보완 제출 / `PENDING_APPROVAL` 승인 대기 / `DECIDED` 확정 |
| `received_by` | `BIGINT` | | X | | 접수한 사람의 `customer_id`. `BRANCH`면 관리자, `ONLINE`이면 명의인 |
| `received_channel` | `VARCHAR(10)` | | X | `'BRANCH'` | `BRANCH` 영업점 대리 접수 / `ONLINE` 고객 비대면 접수(D34) |
| `received_at` | `DATETIME(6)` | | X | | 접수 시각. 신청 기한과 비교한다 |
| `completeness_confirmed_by` | `BIGINT` | | O | | 마지막으로 소명 완비를 확인한 관리자 `customer_id` |
| `completeness_confirmed_at` | `DATETIME(6)` | | O | | 마지막 소명 완비 확인 시각. 심사 기한의 기산점(D12) |
| `review_deadline` | `DATE` | | O | | 심사 기한. 완비 확인일 + 5영업일. 보완 요청 중에는 NULL이고 완비를 다시 확인하면 새로 건다 |
| `supplement_round` | `TINYINT` | | X | `0` | 지금까지 보완 요청 횟수. 2회까지 |
| `supplement_due` | `DATE` | | O | | 마지막 보완 요청의 기한. 1회째 + 5영업일, 2회째 + 3영업일 |
| `created_at` | `DATETIME(6)` | | X | | 생성 시각 |
| `updated_at` | `DATETIME(6)` | | X | | 수정 시각 |

**`objection_decision`** 판정. 반려된 판정은 남겨 두고 다시 쓰므로 이의제기당 여러 행일 수 있지만, 반려되지 않은 판정(승인 대기 또는 확정)은 하나뿐이다

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `decision_id` | `BIGINT` | **PK** | X | `AUTO_INCREMENT` | 식별자 |
| `objection_id` | `BIGINT` | INDEX, FK | X | | `objection.objection_id` |
| `result` | `VARCHAR(10)` | | X | | `ACCEPTED` 수용 / `PARTIAL` 일부 지급정지 / `REJECTED` 불수용 |
| `reason` | `VARCHAR(500)` | | X | | 판정 사유 |
| `early_release_basis` | `VARCHAR(500)` | | O | | 조기 해제 근거. 수용과 일부 지급정지는 필수(법 제8조제2항제2호 단서) |
| `requested_hold_amount` | `BIGINT` | | O | | 일부 지급정지 요청 보류액(다툼 입금액 합). 확정된 `PARTIAL`만 |
| `applied_hold_amount` | `BIGINT` | | O | | 실제로 묶은 보류액. 확정된 `PARTIAL`만 |
| `status` | `VARCHAR(16)` | | X | | `PENDING_APPROVAL` 승인 대기 / `CONFIRMED` 확정 / `RETURNED` 반려 |
| `decided_by` | `BIGINT` | | X | | 판정을 작성한 관리자 `customer_id` |
| `decided_at` | `DATETIME(6)` | | X | | 작성 시각. 불수용은 확정 시각과 같다 |
| `approved_by` | `BIGINT` | | O | | 승인한 관리자 `customer_id`. 작성자와 달라야 한다. 불수용은 NULL |
| `approved_at` | `DATETIME(6)` | | O | | 승인 시각. 계좌 반영 시각과 같다 |
| `returned_by` | `BIGINT` | | O | | 반려한 관리자 `customer_id` |
| `returned_at` | `DATETIME(6)` | | O | | 반려 시각 |
| `return_reason` | `VARCHAR(500)` | | O | | 반려 사유 |
| `discretionary` | `BOOLEAN` | | X | `FALSE` | 담당자 재량 판정인지. 게이트가 닫은 수용, 일부 지급정지를 사유와 2인 승인으로 쓴 판정(ADR-0011) |
| `discretionary_reason` | `VARCHAR(1000)` | | O | | 재량 사유. 재량 판정만 갖고 20~1000자는 애플리케이션이 검사한다 |
| `gate_branch_at_write` | `VARCHAR(24)` | | O | | 작성 시점 게이트 갈래. `V202611090100` 전에 쓴 판정은 NULL |
| `active_objection_id` | `BIGINT` | **UK** | O | 생성 열 | `status`가 `RETURNED`가 아니면 `objection_id`, 반려면 NULL. JPA 엔티티는 매핑하지 않는다 |

**`gate_evaluation`** 해제 게이트 계산 기록. 같은 판정을 재현하려고 규칙 버전과 설정값을 함께 남긴다(D10)

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `gate_evaluation_id` | `BIGINT` | **PK** | X | `AUTO_INCREMENT` | 식별자 |
| `objection_id` | `BIGINT` | INDEX, FK | X | | `objection.objection_id` |
| `purpose` | `VARCHAR(12)` | | X | | `DECISION` 판정 확정 직전 / `ANALYSIS` Agent 분석 시점(PA-05) |
| `branch` | `VARCHAR(16)` | | X | | `FRAUD_PATTERN` / `NORMAL_ACCEPT` / `NORMAL_PARTIAL` / `UNDETERMINED` |
| `simplified_excluded` | `BOOLEAN` | | X | | 간소화 신청이었지만 지급정지 이력으로 일반 심사가 됐는지 |
| `rules_version` | `VARCHAR(20)` | | X | | 게이트 규칙 버전 |
| `settings` | `JSON` | | X | | 계산에 쓴 설정값(`app.payment-stop.gate`) |
| `rule_results` | `JSON` | | X | | 규칙별 성립 여부, 근거 구분, 근거 거래번호, 한 줄 설명 |
| `evaluated_at` | `DATETIME(6)` | | X | | 계산 시각 |

**인덱스와 제약**

| 테이블 | 종류 | 이름 | 컬럼·조건 |
| --- | --- | --- | --- |
| `payment_stop_case` | INDEX | `ix_payment_stop_case_account` | `account_id` |
| `payment_stop_case` | CHECK | `ck_payment_stop_case_status` | `status IN ('STOPPED', 'PARTIAL', 'RELEASED')` |
| `case_disputed_transfer` | UNIQUE | `uk_case_disputed_transfer_txno` | `transaction_number` |
| `case_disputed_transfer` | CHECK | `ck_case_disputed_transfer_amount` | `amount > 0` |
| `objection` | UNIQUE | `uk_objection_case` | `case_id` |
| `objection` | CHECK | `ck_objection_clause`, `ck_objection_status` | `clause BETWEEN 1 AND 3`, 상태 값 6종 |
| `objection` | CHECK | `ck_objection_received_channel`, `ck_objection_supplement_round`, `ck_objection_supplement_due` | `BRANCH`, `ONLINE` / `supplement_round BETWEEN 0 AND 2` / 보완 요청 전에는 `supplement_due`가 NULL |
| `objection_decision` | INDEX | `ix_objection_decision_objection` | `objection_id, decision_id` |
| `objection_decision` | UNIQUE | `uk_objection_decision_active` | `active_objection_id`. 반려되지 않은 판정은 이의제기당 하나 |
| `objection_decision` | CHECK | `ck_objection_decision_basis` | `result = 'REJECTED' OR early_release_basis IS NOT NULL` |
| `objection_decision` | CHECK | `ck_objection_decision_status` | `PENDING_APPROVAL`, `CONFIRMED`, `RETURNED` |
| `objection_decision` | CHECK | `ck_objection_decision_hold` | 확정된 `PARTIAL`이면 두 보류액이 모두 있고(NULL 불가) `requested_hold_amount > 0`, `0 ≤ applied_hold_amount ≤ requested_hold_amount`, 그 밖은 둘 다 NULL. 처음 만든 식은 NULL 비교가 UNKNOWN이 되어 통과해 `V202611060600`에서 `IS NOT NULL`을 넣어 다시 만들었다 |
| `objection_decision` | CHECK | `ck_objection_decision_reject_single` | 불수용은 언제나 `CONFIRMED` |
| `objection_decision` | CHECK | `ck_objection_decision_approval` | 확정된 수용과 일부 지급정지만 `approved_by`, `approved_at`이 있고 `approved_by <> decided_by` |
| `objection_decision` | CHECK | `ck_objection_decision_return` | `RETURNED`면 `returned_by`, `returned_at`, `return_reason`이 모두 있고 아니면 모두 NULL |
| `objection_decision` | CHECK | `ck_objection_decision_discretionary` | `discretionary = FALSE OR discretionary_reason IS NOT NULL` |
| `gate_evaluation` | INDEX | `ix_gate_evaluation_objection` | `objection_id, evaluated_at` |
| `gate_evaluation` | CHECK | `ck_gate_evaluation_purpose`, `ck_gate_evaluation_branch` | 위 값 목록 |



## `agent_run` · `agent_run_step` · `review_draft`

> 이의제기 Agent 실행, 단계 이벤트, 판정안 (D-36, PA-05)

형식 정본은 [agent_run_step_schema.md](phase2/agent_run_step_schema.md), 내부 API는 [objection_agent_contract.md](phase2/objection_agent_contract.md)다. 대기열은 별도 큐 없이 `agent_run`의 `QUEUED` 행이고, 워커는 PK 조건부 UPDATE로 가져간다. 텍스트와 JSON 값은 저장 전에 가명 처리한다.

**`agent_run`** Agent 실행 한 번

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `run_id` | `BIGINT` | **PK** | X | `AUTO_INCREMENT` | 실행 ID |
| `objection_id` | `BIGINT` | INDEX, FK | X | | `objection.objection_id` |
| `status` | `VARCHAR(12)` | INDEX | X | | `QUEUED` / `RUNNING` / `COMPLETED` / `FAILED` |
| `attempt` | `INT` | | X | `0` | 가져간 횟수. 3회째도 끝나지 않으면 `FAILED` |
| `lease_until` | `DATETIME(6)` | | O | | 점유 만료 시각. `RUNNING`일 때만 있다 |
| `model` | `VARCHAR(60)` | | O | | LLM 모델명 |
| `prompt_version` | `VARCHAR(30)` | | O | | 프롬프트 버전 |
| `input_tokens` | `BIGINT` | | O | | 입력 토큰 수 |
| `output_tokens` | `BIGINT` | | O | | 출력 토큰 수 |
| `cost_micro_usd` | `BIGINT` | | O | | 비용, 백만분의 1달러 정수 |
| `requested_by` | `BIGINT` | | X | | 분석을 넣은 사람의 `customer_id`. 영업점 접수와 재분석 요청은 관리자, 비대면 신청과 보완 제출은 명의인 |
| `queued_at` | `DATETIME(6)` | INDEX | X | | 대기열에 들어간 시각 |
| `started_at` | `DATETIME(6)` | | O | | 처음 가져간 시각 |
| `ended_at` | `DATETIME(6)` | | O | | 끝난 시각 |
| `failure_reason` | `VARCHAR(200)` | | O | | 실패하거나 대기열로 돌아간 사유 |

**`agent_run_step`** 실행 안의 단계

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `step_id` | `BIGINT` | **PK** | X | `AUTO_INCREMENT` | 단계 행 ID |
| `run_id` | `BIGINT` | **UK**, FK | X | | `agent_run.run_id` |
| `seq` | `INT` | **UK** | X | | 실행 안 순번. 1부터 빈 번호 없이 |
| `type` | `VARCHAR(20)` | | X | | 단계 종류(형식 문서 §4) |
| `title` | `VARCHAR(200)` | | X | | 화면 한 줄 |
| `summary` | `VARCHAR(500)` | | O | | 짧은 설명 |
| `detail` | `JSON` | | O | | 종류별 상세 |
| `status` | `VARCHAR(10)` | | X | | `RUNNING` / `DONE` / `FAILED` |
| `started_at` | `DATETIME(6)` | | X | | 시작 시각 |
| `ended_at` | `DATETIME(6)` | | O | | 종료 시각 |

**`review_draft`** Agent 판정안. 실행당 1건

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `review_draft_id` | `BIGINT` | **PK** | X | `AUTO_INCREMENT` | 식별자 |
| `run_id` | `BIGINT` | **UK**, FK | X | | `agent_run.run_id` |
| `objection_id` | `BIGINT` | INDEX, FK | X | | `objection.objection_id` |
| `branch` | `VARCHAR(16)` | | X | | Agent 갈래. 해제 근거가 아니다 |
| `content` | `JSON` | | X | | 근거 거래, 누락 소명자료, 주장, 모순, 추가 확인, 안내문 초안 |
| `submitted_at` | `DATETIME(6)` | | X | | 제출 시각 |

**인덱스와 제약**

| 테이블 | 종류 | 이름 | 컬럼·조건 |
| --- | --- | --- | --- |
| `agent_run` | INDEX | `ix_agent_run_objection`, `ix_agent_run_status` | `objection_id, run_id` / `status, queued_at` |
| `agent_run` | CHECK | `ck_agent_run_status`, `ck_agent_run_attempt`, `ck_agent_run_lease` | 상태 값 목록 / `attempt BETWEEN 0 AND 3` / `RUNNING`이면 `lease_until`이 있고 아니면 없다 |
| `agent_run_step` | UNIQUE | `uk_agent_run_step_seq` | `run_id, seq` |
| `agent_run_step` | CHECK | `ck_agent_run_step_seq`, `ck_agent_run_step_type`, `ck_agent_run_step_status` | `seq >= 1` / 종류 목록 / 상태 목록 |
| `review_draft` | UNIQUE | `uk_review_draft_run` | `run_id` |
| `review_draft` | CHECK | `ck_review_draft_branch` | 갈래 값 목록 |

## `objection_supplement_request`, `objection_review_session`, `objection_external_check`, `objection_notice`

> 이의제기 심사 진행 기록 (D-36, PA-10)

보완 요청, 심사 시작, 외부 확인(모의), 통지 기록이다. 상태를 바꾸는 기록은 `objection` 행을 잠그고 쓴다. 통지는 실제로 보내지 않고 기록과 화면 표시만 한다(Plan D30).

**`objection_supplement_request`** 보완 요청 한 회차

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `supplement_request_id` | `BIGINT` | **PK** | X | `AUTO_INCREMENT` | 식별자 |
| `objection_id` | `BIGINT` | **UK**, FK | X | | `objection.objection_id` |
| `round` | `TINYINT` | **UK** | X | | 보완 회차. 1 또는 2 |
| `reason` | `VARCHAR(500)` | | X | | 보완 요청 사유. 명의인에게 보인다 |
| `missing_documents` | `JSON` | | X | | 요청한 서류 이름 배열(최대 20개) |
| `due_date` | `DATE` | | X | | 보완 기한 |
| `requested_by` | `BIGINT` | | X | | 요청한 관리자 `customer_id` |
| `requested_at` | `DATETIME(6)` | | X | | 요청 시각 |
| `submitted_at` | `DATETIME(6)` | | O | | 명의인이 보완 자료를 낸 시각 |
| `submit_note` | `VARCHAR(1000)` | | O | | 명의인이 보완 제출 때 남긴 메모 |

**`objection_review_session`** 담당자가 상세 화면을 연 기록. 처리 시간 평가에 쓴다

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `review_session_id` | `BIGINT` | **PK** | X | `AUTO_INCREMENT` | 식별자 |
| `objection_id` | `BIGINT` | INDEX, FK | X | | `objection.objection_id` |
| `admin_id` | `BIGINT` | | X | | 상세 화면을 연 관리자 `customer_id` |
| `started_at` | `DATETIME(6)` | INDEX | X | | 연 시각 |

**`objection_external_check`** 외부 확인 요청과 회신(모의). 회신은 해제 게이트의 외부 확인 사실이다

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `external_check_id` | `BIGINT` | **PK** | X | `AUTO_INCREMENT` | 식별자 |
| `objection_id` | `BIGINT` | **UK**, FK | X | | `objection.objection_id` |
| `agency` | `VARCHAR(10)` | **UK** | X | | `POLICE` 수사기관 / `PEER_BANK` 관련 금융회사. 이의제기당 기관별 1건 |
| `status` | `VARCHAR(10)` | | X | | `REQUESTED` / `REPLIED`. 모의라 요청 즉시 `REPLIED` |
| `reply` | `VARCHAR(24)` | | O | | `INVOLVEMENT_SUSPECTED` 가담 의심 / `DOCUMENT_FORGED` 서류 위조 판명 / `OWNER_UNRELATED` 명의인 무관 / `NO_RECORD` 해당 없음(게이트 영향 없음) |
| `requested_by` | `BIGINT` | | X | | 요청한 관리자 `customer_id` |
| `requested_at` | `DATETIME(6)` | | X | | 요청 시각 |
| `replied_at` | `DATETIME(6)` | | O | | 회신 시각 |

**`objection_notice`** 통지 기록

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `notice_id` | `BIGINT` | **PK** | X | `AUTO_INCREMENT` | 식별자 |
| `objection_id` | `BIGINT` | INDEX, FK | X | | `objection.objection_id` |
| `target` | `VARCHAR(12)` | | X | | `HOLDER` 명의인 / `VICTIM` 피해자 / `FSS` 금감원 / `RELATED_BANK` 관련 금융회사 |
| `kind` | `VARCHAR(20)` | | X | | `RECEIPT` / `SUPPLEMENT_REQUEST` / `DECISION_ACCEPTED` / `DECISION_PARTIAL` / `DECISION_REJECTED` / `STOP_TERMINATED` |
| `channel` | `VARCHAR(10)` | | X | | `SMS`(명의인) / `DOCUMENT`(그 밖) |
| `basis` | `VARCHAR(100)` | | X | | 통지 근거(법 조항, 당국 표준, 내부 기준) |
| `recorded_at` | `DATETIME(6)` | | X | | 기록 시각 |

**인덱스와 제약**

| 테이블 | 종류 | 이름 | 컬럼과 조건 |
| --- | --- | --- | --- |
| `objection_supplement_request` | UNIQUE | `uk_objection_supplement_round` | `objection_id, round` |
| `objection_supplement_request` | CHECK | `ck_objection_supplement_request_round` | `round BETWEEN 1 AND 2` |
| `objection_review_session` | INDEX | `ix_objection_review_session_objection` | `objection_id, started_at` |
| `objection_external_check` | UNIQUE | `uk_objection_external_check_agency` | `objection_id, agency` |
| `objection_external_check` | CHECK | `ck_objection_external_check_agency`, `ck_objection_external_check_status`, `ck_objection_external_check_reply`, `ck_objection_external_check_replied` | 값 목록 / `REPLIED`면 `reply`와 `replied_at`이 있다 |
| `objection_notice` | INDEX | `ix_objection_notice_objection` | `objection_id, notice_id` |
| `objection_notice` | CHECK | `ck_objection_notice_target`, `ck_objection_notice_kind`, `ck_objection_notice_channel` | 값 목록 |

## `objection_document`, `objection_document_ocr`

> 이의제기 첨부 서류와 OCR (D-36, PA-12, PA-20)

파일 본문은 DB에 두지 않고 저장소(`app.payment-stop.documents.root` 아래 `{objectionId}/{documentId}`)에 둔다. 신분증 사본(`ID_COPY`)은 AES-256-GCM으로 암호화해 저장하고(키 `PAYMENT_STOP_DOCUMENT_KEY`), Agent 내부 API의 목록, 내용, OCR에서 빠진다(D37). 형식은 매직 바이트로 PDF, PNG, JPEG만 받고 파일 하나 10MB, 이의제기당 합계 30MB, 10개까지다.

**`objection_document`** 첨부 서류

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `document_id` | `BIGINT` | **PK** | X | `AUTO_INCREMENT` | 문서 ID |
| `objection_id` | `BIGINT` | INDEX, FK | X | | `objection.objection_id` |
| `kind` | `VARCHAR(10)` | | X | | `EVIDENCE` 증빙 / `ID_COPY` 신분증 사본 |
| `file_name` | `VARCHAR(255)` | | X | | 올린 파일 이름. 경로와 제어 문자는 떼고 저장한다 |
| `content_type` | `VARCHAR(30)` | | X | | 매직 바이트로 판정한 형식 |
| `size_bytes` | `BIGINT` | | X | | 원본 크기 |
| `sha256` | `CHAR(64)` | | X | | 원본 SHA-256(16진수) |
| `storage_key` | `VARCHAR(100)` | | X | | 저장소 안 상대 경로 |
| `encrypted` | `BOOLEAN` | | X | | 암호화 저장 여부. `ID_COPY`는 항상 참 |
| `round` | `TINYINT` | | X | | 0 접수, 1~2 보완 회차 |
| `uploaded_by` | `BIGINT` | | X | | 올린 사람의 `customer_id` |
| `uploader_role` | `VARCHAR(10)` | | X | | `HOLDER` 명의인 / `ADMIN` 관리자 대리 업로드 |
| `uploaded_at` | `DATETIME(6)` | | X | | 업로드 시각 |

**`objection_document_ocr`** 증빙 OCR 결과. 증빙만 행이 있다

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `document_id` | `BIGINT` | **PK**, FK | X | | `objection_document.document_id` |
| `status` | `VARCHAR(10)` | | X | | `PENDING` / `DONE` / `FAILED` |
| `pages` | `JSON` | | O | | 페이지별 글자 영역(`index`, `text`, `bbox`, `confidence`). 텍스트는 서버가 가명 처리한 값 |
| `engine` | `VARCHAR(40)` | | O | | OCR 엔진 이름과 버전 |
| `updated_at` | `DATETIME(6)` | | X | | 갱신 시각 |

**인덱스와 제약**

| 테이블 | 종류 | 이름 | 컬럼과 조건 |
| --- | --- | --- | --- |
| `objection_document` | INDEX | `ix_objection_document_objection` | `objection_id, document_id` |
| `objection_document` | CHECK | `ck_objection_document_kind`, `ck_objection_document_type`, `ck_objection_document_size`, `ck_objection_document_round`, `ck_objection_document_id_copy`, `ck_objection_document_uploader` | 값 목록 / `0 < size_bytes ≤ 10485760` / `round BETWEEN 0 AND 2` / `ID_COPY`면 `encrypted` |
| `objection_document_ocr` | CHECK | `ck_objection_document_ocr_status`, `ck_objection_document_ocr_pages` | 값 목록 / `DONE`이면 `pages`가 있다 |

---

## `interest_payment`

> 예적금 이자 지급 이력 (PH-14, #687)

예적금 만기해지 또는 중도해지 시 계산된 이자와 원천세, 실제 원장 기표 결과를 기록한다.

한 계좌에 대해 이자 지급 이력은 최대 1건이다. `account_id`의 UNIQUE 제약으로 중복 지급을 방지하며, 원장·GL·계좌 해지 처리와 동일한 트랜잭션에서 저장한다.

세후이자가 0원이면 원장을 기표하지 않지만 지급 이력은 남긴다. 이 경우 `transaction_number`와 `trade_date`는 NULL이다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `interest_payment_id` | `BIGINT` | **PK** | X | `AUTO_INCREMENT` | 이자 지급 이력 식별자 |
| `account_id` | `BIGINT` | **UK** | X | | 지급 대상 상품 계좌 ID. 계좌당 이자 지급 1회 보장. 물리적 FK 없음 |
| `payment_reason` | `VARCHAR(12)` | | X | | 지급 사유. `MATURITY`(만기해지) / `TERMINATION`(중도해지) |
| `gross_amount` | `BIGINT` | | X | | 세전이자. 원 단위 정수 |
| `tax_amount` | `BIGINT` | | X | | 원천징수 세금 합계. 원 단위 정수 |
| `net_amount` | `BIGINT` | | X | | 세후이자. `gross_amount - tax_amount` |
| `transaction_number` | `CHAR(20)` | **UK** | O | | `INTEREST` 원장 기표 거래번호. 지급액 0원이면 NULL |
| `trade_date` | `DATE` | | O | | 이자 원장 거래일. `LedgerDepositResult.tradeDate` 사용. 지급액 0원이면 NULL |
| `paid_at` | `DATETIME(6)` | | X | | 이자 지급 처리 일시 (KST) |

**인덱스**

| 종류 | 이름 | 컬럼 |
| --- | --- | --- |
| PRIMARY | `PRIMARY` | `interest_payment_id` |
| UNIQUE | `uk_interest_payment_account` | `account_id` |
| UNIQUE | `uk_interest_payment_tx` | `transaction_number` |

**CHECK 제약**

| 이름 | 조건 | 설명 |
| --- | --- | --- |
| `ck_interest_payment_amount` | `gross_amount >= 0 AND tax_amount >= 0 AND tax_amount <= gross_amount AND net_amount = gross_amount - tax_amount` | 세전이자·세금·세후이자 정합성 |
| `ck_interest_payment_ledger` | `net_amount = 0`이면 거래번호·거래일 모두 NULL, `net_amount > 0`이면 모두 NOT NULL | 실제 원장 기표 여부와 거래 식별자 일치 |

---

## `account_number_sequence`

> 은행·상품별 계좌번호 채번

은행·계좌 유형·상품별 계좌번호 발급 규칙과 마지막 발급 일련번호를 관리한다.

### 계좌번호 구성
```text
은행코드 3자리 + 상품 Prefix 2자리 + 일련번호 7자리
```

| 컬럼명              | 데이터 타입      | NULL 허용 |      기본값       | 키  | 설명                                                              |
|:-----------------|:------------|:-------:|:--------------:|:--:|:----------------------------------------------------------------|
| `sequence_id`    | BIGINT      |    X    | AUTO_INCREMENT | PK | 계좌번호 채번 규칙 식별자                                                  |
| `bank_code`      | CHAR(3)     |    X    |       없음       |    | 계좌번호 앞 3자리에 포함되는 은행코드                                           |
| `account_type`   | VARCHAR(24) |    X    |       없음       |    | 계좌 유형 (`DEMAND_DEPOSIT`, `TIME_DEPOSIT`, `INSTALLMENT_SAVINGS`) |
| `product_id`     | BIGINT      |    O    |      NULL      | FK | 연결 상품 식별자. 입출금계좌는 `NULL`                                        |
| `product_prefix` | CHAR(2)     |    X    |       없음       |    | 계좌번호에 포함되는 상품별 Prefix                                           |
| `last_sequence`  | BIGINT      |    X    |      `0`       |    | 마지막으로 발급한 7자리 일련번호                                              |
| `created_at`     | DATETIME(6) |    X    |       없음       |    | 채번 규칙 생성 일시                                                     |
| `updated_at`     | DATETIME(6) |    X    |       없음       |    | 채번 규칙 최종 수정 일시                                                  |

**인덱스**

| 종류 | 이름                                   | 컬럼                                        | 용도                               |
| :--- |:-------------------------------------|:------------------------------------------|:---------------------------------|
| PRIMARY | `PRIMARY`                            | `sequence_id`                             | 채번 규칙 식별 및 PK 조회                 |
| UNIQUE | `uk_account_number_sequence_prefix`  | `bank_code`, `product_prefix`             | 같은 은행 내 Prefix 중복 방지             |
| UNIQUE | `uk_account_number_sequence_rule` | `bank_code`, `COALESCE(product_id, 0)`                 | 같은 입출금, 예·적금 상품의 채번 규칙 중복 방지     |
| INDEX | `ix_account_number_sequence_lookup`  | `bank_code`, `account_type`, `product_id` | 계좌번호 발급 시 채번 행 조회 및 비관적 락 범위 최소화 |
| INDEX | `ix_account_number_sequence_product` | `product_id`                              | 상품 FK 검사 및 상품 기준 조회 지원           |

**CHECK 제약**

| 이름 | 조건 | 설명 |
| :--- | :--- | :--- |
| `PRIMARY` | `sequence_id` (PK) | 행별 고유값 (채번 규칙 행 식별) |
| `fk_account_number_sequence_product` | `product_id` (FK -> `product.product_id`) | 존재하는 상품만 채번 규칙 연결 가능 |
| `ck_account_number_sequence_bank_code` | `bank_code REGEXP '^[0-9]{3}$'` (CHECK) | 은행코드 형식 검증 (숫자 3자리) |
| `ck_account_number_sequence_prefix` | `product_prefix REGEXP '^[0-9]{2}$'` (CHECK) | 상품 Prefix 형식 검증 (숫자 2자리) |
| `ck_account_number_sequence_type` | `account_type IN ('DEMAND_DEPOSIT', 'TIME_DEPOSIT', 'INSTALLMENT_SAVINGS')` (CHECK) | 허용된 계좌 유형 중 하나인지 검증 |
| `ck_account_number_sequence_product` | `DEMAND_DEPOSIT이면 product_id IS NULL, 예·적금이면 product_id IS NOT NULL` (CHECK) | 입출금은 상품 없음, 예·적금은 상품 필수 (정합성 보장) |
| `ck_account_number_sequence_value` | `last_sequence BETWEEN 0 AND 9999999` (CHECK) | 일련번호가 7자리 범위(0 ~ 9,999,999)를 초과하지 않도록 제한 |

---

# 4. 이체 · 원장 — P4

## `transaction_sequence`

> 거래번호 일련번호 채번 (비관적 락 대상 0순위)

거래번호 일련 10자리를 채번한다. 일자·채널별로 매일 0부터 다시 시작하므로 `AUTO_INCREMENT`로 만들 수 없다. 전 고객이 같은 행을 두고 경합하는 유일한 지점이라, 서버는 행을 잠글 때 번호를 `app.transaction-sequence.block-size`(기본 100)개씩 예약하고 메모리에서 나눠 준다(#786).

예약분은 서버(WAS 인스턴스)마다 따로 들고 있어서, 다음 경우 예약하고 쓰지 않은 번호가 빈칸(결번)으로 남는다.

- 서버 재시작·종료: 그 서버가 들고 있던 남은 번호. 예약 직후 종료하면 묶음 전체(최대 100개)가 빈다
- 거래일 변경: 이전 일자의 묶음에 남은 번호. 일자가 바뀌면 새 일자 행에서 새로 예약한다
- 서버가 여러 대: 위 빈칸이 서버마다 생긴다. 일자·채널 하나에서 서버 수 × 100개까지 빌 수 있다

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `seq_date` | `DATE` | **PK** | X |  | 채번 기준 영업일. KST 기준이며 거래번호 앞 8자리와 같다 |
| `channel` | `CHAR(2)` | **PK** | X |  | 채널 코드. `WB`(인터넷뱅킹) / `BT`(배치·예약이체). 거래번호 9~10번째 자리 |
| `last_seq` | `BIGINT` |  | X | `0` | 해당 일자·채널에서 서버가 예약한 마지막 일련번호. 다음 예약은 이 값 + 1부터다. 앱이 떠 있는 동안 SQL로 바꾸면 서버 메모리의 예약분과 겹칠 수 있어 모든 WAS 인스턴스를 재시작해 예약분을 버린다 |
| `updated_at` | `DATETIME(6)` |  | X |  | 마지막 채번 시각 |

**CHECK 제약**

| 이름 | 조건 |
| --- | --- |
| `ck_txseq_range` | `last_seq BETWEEN 0 AND 9999999999` |

---

## `transfer`

> 이체 거래 (즉시·예약·자동 3종의 단일 수렴점)

즉시·예약·자동이체 3종이 모두 이 테이블 1행으로 수렴한다. 예약·자동은 `source_type`·`source_id`로 원본을 역추적한다. `status = ERROR`인 행은 원장 기표가 0행이다. 예약·자동이체는 `uk_transfer_source_execution_date`(`source_type, source_id, execution_date`)로 같은 회차의 중복 실행을 상태(SUCCESS/ERROR)와 무관하게 DB 레벨에서 막는다 — 즉시이체는 세 컬럼 모두 NULL이라 이 제약의 영향을 받지 않는다(MySQL은 NULL을 서로 다른 값으로 취급).

**타행이체(PH-33-②)도 이 테이블에 남는다.** `payee_bank_code`가 있으면 타행, NULL이면 당행이다. 타행은 수취 계좌가 당행 `account`에 없어 `deposit_account_id`가 NULL이고, 예금주는 상대 은행 응답(SUCCESS)으로만 받아 `ERROR`·`TIMEOUT` 건은 `payee_name`도 NULL이다. 당행 행은 `ck_transfer_payee_account`·`ck_transfer_payee_name`이 지금처럼 두 값을 강제한다. 타행 예약·자동이체는 없다(즉시이체만).

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `transfer_id` | `BIGINT` | **PK** | X |  | 이체 거래 내부 식별자. 대외 식별자는 `transaction_number` |
| `transaction_number` | `CHAR(20)` | **UK** | X |  | 대외 거래 식별자. `YYYYMMDD`(8) + 채널(2) + 일련(10) = 20자. 이체 상세 조회의 경로 변수이자 원장 2행을 잇는 키 |
| `withdrawal_account_id` | `BIGINT` | **FK** | X |  | 출금계좌 → `account.account_id` |
| `deposit_account_id` | `BIGINT` | **FK** | O |  | 입금계좌. 원장 입금 행의 `account_id` 근거 → `account.account_id`. 당행 거래는 항상 값이 있고, 타행 거래는 NULL이다(수취 계좌가 당행에 없다) |
| `deposit_account_number` | `VARCHAR(16)` |  | X |  | 입금 계좌번호. 숫자만. 당행 12자리, 타행 1~16자리(전문 계좌번호 칸, [전문 규격](phase2/fixed_length_message_spec.md) §2) |
| `payee_bank_code` | `CHAR(3)` |  | O |  | 타행 입금 은행코드(`common_code` `BANK_CODE`, 전문 기관코드). **NULL이면 당행 거래**다 |
| `payee_name` | `VARCHAR(50)` |  | O |  | 입금계좌 예금주명. **거래 시점 스냅샷**이라 이후 상대 정보가 바뀌어도 남는다. 당행은 항상 있고, 타행은 SUCCESS 응답이 돌려준 이름이며 `ERROR`·`TIMEOUT`이면 NULL이다 |
| `amount` | `BIGINT` |  | X |  | 이체금액 |
| `fee` | `BIGINT` |  | X | `0` | 수수료. 당행 이체는 0으로 고정 |
| `transfer_type` | `VARCHAR(12)` |  | X |  | 이체 종류. `IMMEDIATE`(즉시) / `SCHEDULED`(예약) / `AUTO`(자동) |
| `channel` | `CHAR(2)` |  | X |  | 거래 채널. `WB`(인터넷뱅킹) / `BT`(배치). 거래번호의 채널 2자리와 같은 값 |
| `status` | `VARCHAR(12)` |  | X |  | 처리 결과. `SUCCESS`(정상) / `ERROR`(오류) / `TIMEOUT`(처리 불명 — 대외 전문 무응답으로 상대 처리 여부를 모른다. 실패가 아니며 조회거래로 확정한다, PH-80). `PROCESSING`은 INSERT 직후의 트랜잭션 내부 상태라 커밋된 행에는 나타나지 않는다 (#377) |
| `source_type` | `VARCHAR(12)` |  | O |  | 이 이체를 만든 원본 구분. `SCHEDULED`(예약이체) / `AUTO`(자동이체) / `HELD`(이체 보류 실행, `transfer_type`은 `IMMEDIATE`). 고객이 바로 실행한 즉시이체는 NULL |
| `source_id` | `BIGINT` |  | O |  | 예약·자동이체 원본 PK, 또는 이체 보류 `transfer_hold_id`. `source_type`과 함께 역추적에 쓴다 |
| `execution_date` | `DATE` |  | O |  | `source_id`와 함께 멱등키를 구성하는 회차 실행일자. SCHEDULED/AUTO와 HELD 전용(고객이 바로 실행한 즉시이체는 NULL). HELD는 보류한 날짜(`transfer_hold.held_at`)다. 배치가 실제로 호출된 날짜가 아니라 그 회차의 논리적 실행일(`scheduled_transfer.scheduled_date` / `auto_transfer_execution.execution_date`)이 들어가야 크래시 후 재시도가 같은 회차로 식별된다 |
| `my_passbook_memo` | `VARCHAR(10)` |  | O |  | 내 통장에 찍히는 문구. 최대 10자 |
| `recipient_passbook_memo` | `VARCHAR(10)` |  | O |  | 받는 분 통장에 찍히는 문구 |
| `withdrawal_balance_after` | `BIGINT` |  | O |  | 출금 직후 출금계좌 잔액. 성공 시에만 값이 있다 |
| `error_code` | `VARCHAR(10)` |  | O |  | 실패 시 오류코드 |
| `error_message` | `VARCHAR(200)` |  | O |  | 실패 시 사유 문구 |
| `transferred_at` | `DATETIME(6)` |  | X |  | 이체 처리 일시 |
| `trade_date` | `DATE` |  | O |  | **거래일** — 이 이체가 귀속되는 영업일. 거래 트랜잭션이 영업일 gate(`BusinessDateGate`)로 읽은 값이다. 휴일 거래는 다음 영업일이 된다([glossary](phase2/glossary.md) 7번). **영업일 전환(COB) 전에 gate를 잡은 거래는 전환 이전 영업일, 전환 뒤 거래는 다음 영업일이다 — 전환은 진행 중 거래가 끝난 뒤에만 일어난다(ADR-0008).** 성공·실패(ERROR) 행 모두 채운다 — 실패 행은 원장 없이 이 테이블에만 남고, 거래일은 ERROR 기록 트랜잭션이 gate로 읽은 값이다. 자동·예약이체는 휴일에도 지정일에 실행되므로(REQ-AUTO-001), 휴일에 실행된 회차의 거래일도 다음 영업일이다 — 실행일(`execution_date`, 달력 날짜)과 거래일이 다를 수 있다. 정정 거래(취소정정·정상거래)는 원거래일이 아니라 정정한 날의 영업일이다. #516 대입 전 행은 NULL이다 |
| `invalidated_at` | `DATETIME(6)` |  | O |  | 정정 체인으로 **무효화된 시각**. NULL이면 유효한 거래다. 무효화는 `status`를 바꾸지 않는다 — `status`는 처리 결과(성공·실패)이고 무효화는 그 뒤에 일어나는 별개의 사건이다. SUCCESS인 원거래만 무효화되며 한 번 무효화되면 되돌리지 않는다(PH-80) |
| `ref_transfer_id` | `BIGINT` | **FK** | O |  | 정정 체인의 **원거래** → `transfer.transfer_id`. 취소정정·정상거래 모두 원거래를 가리키므로 원거래 하나로 체인 전체를 찾는다. 일반 거래는 NULL |
| `correction_type` | `VARCHAR(12)` |  | O |  | 정정 체인에서의 역할. `REVERSAL`(취소정정 — 원거래와 반대 방향) / `REPOST`(정상거래 — 원래 의도한 거래). 일반 거래는 NULL. `ref_transfer_id`와 함께 있거나 함께 없다 |
| `created_at` | `DATETIME(6)` |  | X |  | 행 생성 일시 |

**인덱스**

| 종류 | 이름 | 컬럼 |
| --- | --- | --- |
| UNIQUE | `uk_transfer_txno` | `transaction_number` |
| INDEX | `ix_transfer_wacc` | `withdrawal_account_id, transferred_at DESC` |
| INDEX | `ix_transfer_status_created` | `status, created_at` — 조회거래 스케줄러가 1분마다 처리 불명(TIMEOUT) 타행 건을 찾는다(PH-33-②) |
| UNIQUE | `uk_transfer_source_execution_date` | `source_type, source_id, execution_date` — 예약·자동이체 처리결과의 거래일 조회(`transfer.api.TransferTradeDateQuery`, #679)도 이 인덱스로 읽는다 |
| UNIQUE | `uk_transfer_correction` | `ref_transfer_id, correction_type` — 원거래 하나에 취소정정·정상거래는 각각 1건. 동시 정정을 DB가 막는다 |

**CHECK 제약**

| 이름 | 조건 |
| --- | --- |
| `ck_transfer_amount` | `amount > 0` |
| `ck_transfer_fee` | `fee >= 0` |
| `ck_transfer_selfsend` | `withdrawal_account_id <> deposit_account_id` — 타행은 `deposit_account_id`가 NULL이라 해당 없음 |
| `ck_transfer_payee_account` | `(payee_bank_code IS NULL) = (deposit_account_id IS NOT NULL)` — 당행은 입금계좌 ID 필수, 타행은 없음 |
| `ck_transfer_payee_name` | `payee_bank_code IS NOT NULL OR payee_name IS NOT NULL` — 당행은 예금주 필수 |
| `ck_transfer_correction_type` | `correction_type IN ('REVERSAL', 'REPOST')` |
| `ck_transfer_correction_ref` | `(correction_type IS NULL) = (ref_transfer_id IS NULL)` — 반쪽 정정 금지 |

---

## `ledger_entry`

> 원장 (APPEND-ONLY. UPDATE/DELETE 금지)

**APPEND-ONLY.** `UPDATE`·`DELETE`를 금지한다. 취소는 반대 방향 기표를 새로 쌓는다. **예외는 `reversed` 하나다** — 반대기표를 쌓을 때 원거래 행의 이 값만 `FALSE`→`TRUE`로 한 번 세운다(정정 체인, PH-80). 금액·잔액·시각은 어떤 경우에도 바꾸지 않는다. `occurred_at` 기준 RANGE 파티션이라 FK를 선언할 수 없고, 파티션 키가 PK에 포함돼야 해서 PK가 복합키다. 파티션 범위와 유지보수 절차는 [flyway_guide.md](flyway_guide.md) §5를 따른다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `ledger_entry_id` | `BIGINT` | **PK** | X |  | 원장 행 식별자. 거래내역 조회 응답의 `ledgerEntryId` |
| `account_id` | `BIGINT` |  | X |  | 기표 대상 계좌 |
| `transfer_id` | `BIGINT` |  | O |  | 원본 이체 거래. 상품가입 초입금·이자 지급처럼 이체가 아닌 기표는 NULL |
| `transaction_number` | `CHAR(20)` |  | X |  | 이 기표가 속한 거래번호. **출금 행과 입금 행이 같은 값**을 갖는다. 짝 없는 1행(`OPENING`·`INTEREST`)은 이 번호를 혼자 쓴다 |
| `direction` | `VARCHAR(10)` |  | X |  | 기표 방향. `DEPOSIT`(입금) / `WITHDRAWAL`(출금). 금액은 늘 양수라 부호 역할을 이 컬럼이 한다 |
| `amount` | `BIGINT` |  | X |  | 기표 금액. 항상 양수 |
| `balance_after` | `BIGINT` |  | X |  | 이 기표 직후의 계좌 잔액 스냅샷. 통장 형태로 보여줄 때 쓴다 |
| `transaction_type` | `VARCHAR(32)` |  | X |  | 기표를 일으킨 업무. `OPENING`(개시 잔액, 짝 없는 1행이며 상대편은 GL 개시 전표) / `IMMEDIATE_TRANSFER` / `SCHEDULED_TRANSFER` / `AUTO_TRANSFER` / `PRODUCT_SUBSCRIPTION`(가입 초입금) / `INTEREST`(이자, 짝 없는 1행이며 상대편은 GL 이자 전표) / `MATURITY`(만기해지, 상품 계좌 → 고객 계좌 2행) / `TERMINATION`(중도해지, 상품 계좌 → 고객 계좌 2행) / `REVERSAL`(반대기표) / `INTERBANK_TRANSFER`(타행이체 출금, 짝 없는 1행이며 상대편은 GL 미결제 전표. 실패로 확정되면 같은 거래번호로 `reversal_id`가 원 출금을 가리키는 되돌림 입금 1행이 붙고 원 출금은 `reversed`가 선다). `INTEREST`·`MATURITY`·`TERMINATION`은 입금 계약 `transfer.api.LedgerDepositUseCase`(#595)만 남긴다 |
| `transaction_content` | `VARCHAR(10)` |  | O |  | 통장에 찍히는 적요. 최대 10자 |
| `channel` | `CHAR(2)` |  | X |  | 거래 채널. `WB` / `BT` |
| `reversed` | `BOOLEAN` |  | X | `FALSE` | 반대기표로 취소된 원거래인지 여부. 원본을 지우지 않고 이 값만 세운다 |
| `reversal_id` | `BIGINT` |  | O |  | 반대기표 행이 가리키는 원거래의 `ledger_entry_id` |
| `occurred_at` | `DATETIME(6)` | **PK** | X |  | 기표 발생 일시. RANGE 파티션 키이며 거래내역 조회의 기간 조건이 이 컬럼에 걸린다. **온라인 거래 경로(이체·상품가입 입금)는 계좌 락을 쥔 뒤 이 시각을 찍으므로, 시계가 정상적으로 흐르는 동안에는 같은 계좌의 잔액 반영 순서와 같다.** 적수·거래내역은 `occurred_at DESC, ledger_entry_id DESC` 순으로 마지막 잔액을 고른다. 락을 거치지 않고 직접 적재하는 시드(`Phase2MinimumSeedService`·`local-demo-data.sql`)는 이 계약의 대상이 아니다(#545) |
| `trade_date` | `DATE` |  | O |  | **거래일** — 이 기표가 귀속되는 영업일. `occurred_at`(발생 시각)과 다르다. 값 규칙은 `transfer.trade_date`와 같다. **원장을 쓰는 모든 경로(이체·정정·상품가입 입금·예적금 입금 계약)가 채운다.** #516 대입 전 행은 NULL이다. P6 시드(PH-60b)는 GL과 같은 거래일을 넣는다 |

**`trade_date` 는 NULL 을 허용한다(Expand).** `NOT NULL` 제약은 걸지 않는다 — 300만 행 파티션 테이블 재구성을 피하려고, 새 행의 필수 여부는 도메인 팩토리가 막는다(#515 A안). 기존 행 채우기는 #515에서 한다. 거래일로 조회하는 곳이 아직 없어 인덱스는 두지 않는다. `gl_journal_entry` 는 시산표가 거래일로 집계해서 GL-IDX 에서 거래일 인덱스를 붙였다.

**`trade_date` 로 거를 때는 `occurred_at` 범위도 넉넉히 같이 건다.** 파티션 키가 `occurred_at` 이라 `trade_date` 조건만 걸면 모든 파티션을 읽는다. 거래일과 발생일의 간격은 일정하지 않다 — 연휴면 며칠 뒤이고, COB 가 늦어지면 하루 앞설 수도 있다.

**인덱스**

| 종류 | 이름 | 컬럼 |
| --- | --- | --- |
| INDEX | `ix_le_account_time` | `account_id, occurred_at DESC` |
| INDEX | `ix_le_txno` | `transaction_number` |
| INDEX | `ix_le_account_dir` | `account_id, direction, occurred_at` |

**CHECK 제약**

| 이름 | 조건 |
| --- | --- |
| `ck_le_amount` | `amount > 0` |
| `ck_le_direction` | `direction IN ('DEPOSIT','WITHDRAWAL')` |
| `ck_le_txno` | `transaction_number REGEXP '^[0-9]{8}[A-Z]{2}[0-9]{10}$'` |

---

## `ledger_entry_id_sequence`

> `ledger_entry.ledger_entry_id` 전용 채번 카운터

`ledger_entry`는 파티션 키(`occurred_at`)를 포함한 복합 PK `(ledger_entry_id, occurred_at)`를 쓰는데, Hibernate는 `@IdClass` 복합키 구성 필드에 IDENTITY 채번 전략을 지원하지 않는다(`docs/flyway_guide.md` 4-3절 참고). 그래서 `ledger_entry_id` 컬럼 자체의 `AUTO_INCREMENT`는 애플리케이션이 직접 활용할 수 없고, 이 전용 테이블에 빈 행을 저장해 생성되는 `AUTO_INCREMENT` 값만 취해 `ledger_entry_id`로 사용한다. 다른 컬럼은 두지 않는다.

| 컬럼명 | 데이터 타입 | NULL 허용 | 기본값 | 키 | 설명 |
| --- | --- | --- | --- | --- | --- |
| `sequence_id` | `BIGINT` | X | `AUTO_INCREMENT` | PK | 생성 즉시 `ledger_entry.ledger_entry_id`로 그대로 쓰인다 |

---

## `ledger_reconciliation_mismatch`

> 원장-잔액 대사 불일치 목록 (PH-38)

COB 대사 스텝이 원장 누적 합계와 `account.balance`가 다른 계좌를 영업일별로 남긴다. 관리자 목록 API(FE ADM-04)가 읽는다. **같은 영업일로 대사가 다시 돌면 그날 행이 이미 있을 때는 저장하지 않고 처음 목록을 그대로 둔다(#749).** 멈춘 COB를 재개하면 같은 영업일로 대사가 다시 도는데, 그 결과는 지금 원장을 대조한 다른 시점의 목록이라 마감을 멈춘 원인을 지우게 되기 때문이다. 마감을 계속할지는 다시 돈 대사 결과로 판정하고, 그 건수는 `[LEDGER_RECONCILIATION_KEPT]` 로그에 남는다. 처음 실행이 0건이라 행이 없던 날은 다시 돈 결과를 저장한다. 재실행 이력은 `batch_execution_history`가 남긴다. 대사는 보고만 하고 정정하지 않으므로 처리 상태 컬럼이 없다. 감사용 기록이라 계좌가 해지·정리돼도 남도록 `account`에 FK를 걸지 않는다. 차액(`ledger_balance - account_balance`)은 저장하지 않고 조회할 때 계산한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `ledger_reconciliation_mismatch_id` | `BIGINT` | **PK** | X | `AUTO_INCREMENT` | 불일치 행 식별자 |
| `business_date` | `DATE` | **UK** | X |  | 대사한 COB의 마감 대상 영업일(ADR-0004 `COB_DATE`). `batch_execution_history.business_date`와 같은 값이다 |
| `account_id` | `BIGINT` | **UK** | X |  | 불일치 계좌 |
| `ledger_balance` | `BIGINT` |  | X |  | 원장 누적 합계 `SUM(DEPOSIT) - SUM(WITHDRAWAL)`. 원장이 없는 계좌는 0 |
| `account_balance` | `BIGINT` |  | X |  | 대사 시점의 `account.balance` |
| `detected_at` | `DATETIME(6)` |  | X |  | 대사가 불일치를 확인한 시각 |

**인덱스**

| 종류 | 이름 | 컬럼 |
| --- | --- | --- |
| UNIQUE | `uk_ledger_reconciliation_mismatch_date_account` | `business_date, account_id` — 영업일별 목록 조회와 최근 영업일 조회도 이 인덱스를 쓴다 |

---

## `favorite_account`

> 자주 쓰는 계좌 (최대 20건은 앱 검증)

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `favorite_account_id` | `BIGINT` | **PK** | X |  | 등록 건 내부 식별자. 별칭 수정·삭제 API의 경로 변수 |
| `customer_id` | `BIGINT` | **FK** **UK** | X |  | 등록한 고객 → `customer.customer_id` |
| `deposit_account_number` | `CHAR(12)` | **UK** | X |  | 등록한 입금 계좌번호 |
| `payee_name` | `VARCHAR(50)` |  | X |  | 등록 시점 예금주 스냅샷 |
| `alias` | `VARCHAR(24)` |  | O |  | 미지정 시 예금주명 (FAV0001) |
| `registered_at` | `DATETIME(6)` |  | X |  | 등록 일시 |

**인덱스**

| 종류 | 이름 | 컬럼 |
| --- | --- | --- |
| UNIQUE | `uk_fav` | `customer_id, deposit_account_number` |

---

## `transfer_hold`

> 이체 보류 — 기표하지 않고 잡아 둔 즉시이체 원본 요청 (#772)

훅 C(`TransferHoldPolicy`)가 보류로 판정한 즉시이체를 원본 요청 그대로 담는다([transfer_seam.md](phase2/transfer_seam.md) §9, [glossary](phase2/glossary.md) 12번). 보류 중에는 원장·잔액·한도가 바뀌지 않는다. 실행하면 원본 그대로 이체를 한 번 실행하고 `transfer`(`source_type = HELD`, `source_id = transfer_hold_id`) 1행이 생긴다. 계좌비밀번호·OTP 토큰은 보류 전에 소비되고 원문은 저장하지 않는다.

상태는 `HELD → EXECUTING → EXECUTED / FAILED`, `HELD → REJECTED / CANCELLED / EXPIRED`다. 전이는 조건부 UPDATE(`WHERE status = 이전 상태`)로 해서 같은 건을 두 곳에서 동시에 처리해도 한 곳만 성공한다. 그래서 `version` 컬럼이 없다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `transfer_hold_id` | `BIGINT` | **PK** | X |  | 보류 건 식별자. 실행한 `transfer.source_id` |
| `customer_id` | `BIGINT` |  | X |  | 요청한 고객. 고객 취소는 본인 건만 된다 |
| `withdrawal_account_id` | `BIGINT` |  | X |  | 출금계좌 ID |
| `deposit_account_number` | `VARCHAR(16)` |  | X |  | 입금 계좌번호(당행, 숫자만) |
| `amount` | `BIGINT` |  | X |  | 이체금액 |
| `my_passbook_memo` | `VARCHAR(10)` |  | O |  | 내 통장 표시내용 |
| `recipient_passbook_memo` | `VARCHAR(10)` |  | O |  | 받는 분 통장 표시내용 |
| `channel` | `CHAR(2)` |  | X |  | 요청 채널. 실행할 때도 이 채널로 채번한다 |
| `flow_id` | `VARCHAR(64)` |  | O |  | FE 행동 흐름 식별자 |
| `policy_code` | `VARCHAR(30)` |  | X |  | 보류를 정한 정책 코드 |
| `release_mode` | `VARCHAR(12)` |  | X |  | 푸는 방식. `AT_TIME`(시각 도래, 지연이체) / `ON_APPROVAL`(관리자 승인, FDS) |
| `release_at` | `DATETIME(6)` |  | O |  | `AT_TIME`일 때 실행할 시각. 그 밖에는 NULL |
| `status` | `VARCHAR(10)` |  | X |  | `HELD`(보류 중) / `EXECUTING`(실행 선점) / `EXECUTED`(실행 성공) / `FAILED`(실행 시점 검사에서 거부) / `REJECTED`(관리자 거절) / `CANCELLED`(고객 취소) / `EXPIRED`(승인 기한 지남) |
| `authenticated_at` | `DATETIME(6)` |  | X |  | 계좌비밀번호·OTP 토큰을 소비한 시각 |
| `held_at` | `DATETIME(6)` |  | X |  | 보류 저장 시각. 실행한 `transfer.execution_date`는 이 날짜다 |
| `transfer_id` | `BIGINT` | **UK** | O |  | 실행으로 생긴 `transfer` 행. 실행 전이거나 실행 없이 끝났으면 NULL |
| `actor_id` | `BIGINT` |  | O |  | 실행·거절·취소한 주체(관리자 또는 고객). 스케줄러 실행은 NULL |
| `reason_code` | `VARCHAR(30)` |  | O |  | 거절 사유 코드 |
| `executing_at` | `DATETIME(6)` |  | O |  | `EXECUTING`으로 선점한 시각. 서버가 실행 중 멈춘 건을 찾는 기준 |
| `completed_at` | `DATETIME(6)` |  | O |  | 종료 상태가 된 시각 |

**인덱스**

| 종류 | 이름 | 컬럼 |
| --- | --- | --- |
| UNIQUE | `uk_transfer_hold_transfer` | `transfer_id` — 보류 한 건은 이체 행 하나로만 끝난다 |
| INDEX | `ix_transfer_hold_status_release` | `status, release_at` — 해제 스케줄러가 해제 시각이 지난 `HELD`와 멈춘 `EXECUTING`을 찾는다 |
| INDEX | `ix_transfer_hold_customer` | `customer_id, held_at` — 고객의 보류 건 조회 |

**CHECK 제약**

| 이름 | 조건 |
| --- | --- |
| `ck_transfer_hold_amount` | `amount > 0` |
| `ck_transfer_hold_release` | `(release_mode = 'AT_TIME') = (release_at IS NOT NULL)` |

FK는 두지 않는다. 원장 근거가 아니고, 출금계좌 FK는 INSERT 때 계좌 행에 공유 락을 요구한다. 소유·수취인 검증은 보류 판정 전에 끝난다.

---

## `delayed_transfer_setting`

> 고객별 지연이체 설정 (PH-06, #772)

근거는 전자금융거래법 제13조제2항과 시행령 제9조의2다(이용자가 원하면 거래지시부터 일정 시간 뒤에 지급 효력이 생기게 해야 한다). 훅 C 구현체(`DelayedTransferHoldPolicy`)가 고객 락을 쥔 채 이 행을 PK로 한 번 읽는다. 전역 스위치(`app.delayed-transfer.enabled`)가 꺼져 있으면 읽지 않는다.

**행이 없으면 꺼진 것으로 본다.** 처음 저장할 때 INSERT하므로 기존 고객 백필이 없다. 고객 본인만 바꾸는 값이라 `version`을 두지 않는다(동시에 바꾸면 나중 요청이 남는다).

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `customer_id` | `BIGINT` | **PK** | X |  | 고객. 고객당 1행 |
| `enabled` | `BOOLEAN` |  | X |  | 지연이체 사용 여부 |
| `threshold_amount` | `BIGINT` |  | X |  | 기준금액(원). 이 금액 **이상**인 당행 즉시이체를 지연한다. 화면 기본값 100만 원은 은행들의 "건당 100만 원 이하 즉시이체 허용" 예외에 맞춘 값이다 |
| `delay_hours` | `INT` |  | X |  | 지연시간(시간 단위). 최소 3시간은 금감원 안내·은행 공통 기준, 최대 24시간은 우리 기준이다 |
| `created_at` | `DATETIME(6)` |  | X |  | 처음 저장한 시각 |
| `updated_at` | `DATETIME(6)` |  | X |  | 마지막으로 바꾼 시각 |

**CHECK 제약**

| 이름 | 조건 |
| --- | --- |
| `ck_delayed_transfer_setting_threshold` | `threshold_amount > 0` |
| `ck_delayed_transfer_setting_delay` | `delay_hours BETWEEN 3 AND 24` |

---

# 5. 이체한도 — P1

## `transfer_limit`

> 이체한도 (P1 소유 경계 유지)

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `customer_id` | `BIGINT` | **PK** **FK** | X |  | 대상 고객. 고객당 1행 → `customer.customer_id` |
| `one_time_limit` | `BIGINT` |  | X |  | 1회 이체한도. 초과하면 LMT0002 |
| `daily_limit` | `BIGINT` |  | X |  | 1일 이체한도. 초과하면 LMT0003 |
| `created_at` | `DATETIME(6)` |  | X |  | 한도 최초 부여 일시. REQ-TRSF-029로 가입 시 생성된다 |
| `updated_at` | `DATETIME(6)` |  | X |  | 행 최종 수정 일시 |

**CHECK 제약**

| 이름 | 조건 |
| --- | --- |
| `ck_tl_order` | `one_time_limit <= daily_limit` |
| `ck_tl_positive` | `one_time_limit > 0 AND daily_limit > 0` |

**낙관적 락을 쓰지 않는다**

`version` 컬럼은 `V202608201115`로 제거했다. 한도 변경과 이체 실행 모두 비관적 락(`SELECT ... FOR UPDATE`)으로 간다. 이체는 이 테이블을 읽기만 하므로 버전 검증을 하려면 커밋 직전에 의미 없는 `UPDATE`를 넣어야 하고, 그러면 같은 고객의 동시 이체가 서로 충돌해 재시도가 생긴다.

---

## `transfer_limit_daily_usage`

> 일별 한도 사용액 (비관적 락 대상 1순위)

이체 실행 시 가장 먼저 잠그는 자원 중 하나다. `SUCCESS`로 확정된 건만 합산하며 오류·취소·미실행 예약은 제외한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `customer_id` | `BIGINT` | **PK** **FK** | X |  | 대상 고객 → `customer.customer_id` |
| `usage_date` | `DATE` | **PK** | X |  | 사용액 집계 기준일. KST 영업일이며 매일 새 행이 생긴다 |
| `used_amount` | `BIGINT` |  | X | `0` | 해당 일자에 `SUCCESS`로 확정된 이체금액 누계. 오류·취소·미실행 예약은 빠진다 |
| `created_at` | `DATETIME(6)` |  | X |  | 해당 일자 첫 이체로 행이 생성된 시각 |
| `updated_at` | `DATETIME(6)` |  | X |  | 마지막 사용액 갱신 시각 |

**CHECK 제약**

| 이름 | 조건 |
| --- | --- |
| `ck_tldu_used` | `used_amount >= 0` |

---

## `transfer_limit_history`

> 이체한도 변경 이력 (P1 소유 경계 유지)

REQ-TRSF-025의 "변경 이력을 저장한다"를 담는다. 한도 변경 1건당 1행이 쌓이는 append-only 테이블이다. 변경자·요청 IP 같은 감사 정보는 `audit_log`의 책임이므로 여기에는 값의 변화만 남긴다.

**변경 후 값은 저장하지 않는다.** 이력 N의 변경 후 값은 이력 N+1의 `before_*`와 같고, 마지막 이력의 변경 후 값은 `transfer_limit`의 현재값과 같다. 같은 값을 두 군데 두면 어긋날 여지만 생긴다. 조회할 때는 원하는 건수보다 한 건 더 읽어 인접 행끼리 짝을 맞추고, 마지막 행만 `transfer_limit`을 읽어 채운다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `history_id` | `BIGINT` | **PK** | X | `AUTO_INCREMENT` | 이력 식별자 |
| `customer_id` | `BIGINT` | **FK** | X |  | 대상 고객 → `customer.customer_id` |
| `before_one_time_limit` | `BIGINT` |  | X |  | 변경 직전 1회 이체한도 |
| `before_daily_limit` | `BIGINT` |  | X |  | 변경 직전 1일 이체한도 |
| `created_at` | `DATETIME(6)` |  | X |  | 변경이 일어난 시각 |
| `updated_at` | `DATETIME(6)` |  | X |  | 행 최종 수정 일시. append-only라 `created_at`과 같은 값이 유지된다 |

**제약·인덱스를 두지 않은 이유**

`CHECK` 제약은 두지 않는다 — 적재되는 값은 `transfer_limit`의 `ck_tl_positive`·`ck_tl_order`를 이미 통과한 값이다. 별도 인덱스도 두지 않는다 — 이력 조회 요구사항이 아직 없고, FK가 `customer_id` 인덱스를 자동 생성한다. 조회 API가 생기면 그때 추가한다.

---

# 6. 상품가입 — P3

## `product_subscription`

> 상품가입

PRD0301(1인 1계좌 제한)은 `product.single_account_limit = TRUE`인 상품에만 적용되므로 `(customer_id, product_id)`에 UNIQUE를 걸지 않는다. 애플리케이션이 조회 후 판정한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `subscription_id` | `BIGINT` | **PK** | X |  | 가입 건 내부 식별자. 가입 결과 조회의 경로 변수 |
| `customer_id` | `BIGINT` | **FK** | X |  | 가입 고객 → `customer.customer_id` |
| `product_id` | `BIGINT` | **FK** | X |  | 가입 상품 → `product.product_id` |
| `account_id` | `BIGINT` | **FK** **UK** | O |  | 가입으로 새로 개설된 계좌. 가입 실패 시 NULL → `account.account_id` |
| `withdrawal_account_id` | `BIGINT` | **FK** | X |  | 초입금을 빼갈 출금계좌 → `account.account_id` |
| `subscription_amount` | `BIGINT` |  | X |  | 가입금액. 적립식은 월 납입액 |
| `term_months` | `SMALLINT` |  | X |  | 가입기간(개월) |
| `payment_day` | `TINYINT` |  | O |  | 매월 자동 납입일. 1~28 사이이며 적립식 상품만 값이 있다 |
| `base_rate` | `DECIMAL(5,2)` |  | X |  | 가입 시점의 기본금리 스냅샷. 이후 상품 금리가 바뀌어도 유지된다 |
| `preferential_rate` | `DECIMAL(5,2)` |  | X | `0.00` | 적용된 우대금리 합계 |
| `applied_rate` | `DECIMAL(5,2)` |  | X |  | 최종 적용금리 = `base_rate` + `preferential_rate` |
| `maturity_handling` | `VARCHAR(12)` |  | X |  | 만기 처리 방식. `TRANSFER`(출금계좌로 입금) / `RENEW`(재예치) |
| `expected_maturity_amount` | `BIGINT` |  | O |  | 예상 만기 수령액(세전) |
| `status` | `VARCHAR(12)` |  | X |  | 가입 처리 결과. `SUCCESS`(정상) / `ERROR`(오류) / `PROCESSING`(처리중) |
| `transaction_number` | `CHAR(20)` |  | O |  | 초입금 기표의 거래번호. 실패 시 NULL |
| `opened_date` | `DATE` |  | O |  | 개설일 |
| `maturity_date` | `DATE` |  | O |  | 만기일 |
| `subscribed_at` | `DATETIME(6)` |  | X |  | 가입 실행 일시 |
| `subscription_trade_date` | `DATE` |  | O |  | 가입 논리 영업일. 가입 트랜잭션이 영업일 gate(`BusinessDateGate`)로 읽은 값이다. 정보계 SUBSCRIBE 이벤트 기준일. 기존 행은 가입 시각을 영업일로 올려 채웠다 |

**인덱스**

| 종류 | 이름 | 컬럼 |
| --- | --- | --- |
| UNIQUE | `uk_sub_account` | `account_id` |
| INDEX | `ix_sub_customer_product` | `customer_id, product_id, status` |

---

## `subscription_terms_agreement`

> 상품 약관 동의 (버전·열람 이력)

`terms_version`과 `read_at`이 PRD0005(전문 열람)·PRD0006(버전 불일치) 판정의 물리적 근거다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `subscription_id` | `BIGINT` | **PK** **FK** | X |  | 대상 가입 건 → `product_subscription.subscription_id` |
| `terms_id` | `BIGINT` | **PK** **FK** | X |  | 동의한 약관 → `terms.terms_id` |
| `terms_version` | `VARCHAR(10)` |  | X |  | 동의 시점의 약관 버전. 현재 버전과 다르면 PRD0006 |
| `read_at` | `DATETIME(6)` |  | O |  | 약관 전문을 연 시각. `view_required = TRUE` 약관인데 NULL이면 PRD0005 |
| `agreed_at` | `DATETIME(6)` |  | X |  | 동의 시각 |

---

# 7. 예약 · 자동이체 — P3·P5

## `scheduled_transfer`

> 예약이체

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `scheduled_transfer_id` | `BIGINT` | **PK** | X |  | 예약 건 내부 식별자. 상세 조회·취소 API의 경로 변수 |
| `customer_id` | `BIGINT` | **FK** | X |  | 예약한 고객 → `customer.customer_id` |
| `withdrawal_account_id` | `BIGINT` | **FK** | X |  | 출금계좌 → `account.account_id` |
| `payee_bank_code` | `CHAR(3)` |  | X |  | 입금 은행 코드. 1차는 당행 전용이라 상수이며 별도 은행 테이블을 두지 않는다 |
| `payee_account_number` | `CHAR(12)` |  | X |  | 입금 계좌번호 |
| `payee_name` | `VARCHAR(50)` |  | X |  | 예금주명. **등록 시점 스냅샷**이라 이후 상대 계좌가 바뀌어도 유지된다 |
| `amount` | `BIGINT` |  | X |  | 이체금액 |
| `scheduled_date` | `DATE` |  | X |  | 예약 실행일. 등록일 익일부터 1년 이내여야 한다 (SCD0001) |
| `my_passbook_memo` | `VARCHAR(10)` |  | O |  | 내 통장에 찍히는 문구 |
| `recipient_passbook_memo` | `VARCHAR(10)` |  | O |  | 받는 분 통장에 찍히는 문구 |
| `status` | `VARCHAR(12)` |  | X |  | 예약 건 생명주기 상태. `WAITING`(대기) / `PROCESSING`(처리중) / `SUCCESS`(정상) / `FAILED`(오류) / `CANCELED`(취소) |
| `transaction_number` | `CHAR(20)` |  | O |  | 실행 성공 시 생성된 거래번호. 대기·실패 건은 NULL |
| `registered_at` | `DATETIME(6)` |  | X |  | 예약 등록 일시 |
| `executed_at` | `DATETIME(6)` |  | O |  | 배치 실행 일시. 미실행이면 NULL |
| `canceled_at` | `DATETIME(6)` |  | O |  | 취소 일시. 미취소면 NULL |
| `failure_reason` | `VARCHAR(200)` |  | O |  | 실행 실패 사유 |
| `version` | `BIGINT` |  | X | `0` | 낙관적 락 버전. 다건 취소가 읽어둔 건을 배치가 `PROCESSING`으로 선점하면 취소 저장이 CMN0303으로 걸린다 |
| `active_dup_key` | `VARCHAR(80)` *GEN* | **UK** | O |  | **생성 컬럼.** `WAITING`일 때만 값이 생겨 중복 예약을 막는다 (SCD0301) |

**인덱스**

| 종류 | 이름 | 컬럼 |
| --- | --- | --- |
| UNIQUE | `uk_sched_active_dup` | `active_dup_key` |
| INDEX | `ix_sched_batch` | `status, scheduled_date` |
| INDEX | `ix_sched_payee_status` | `payee_account_number, status` |

**CHECK 제약**

| 이름 | 조건 |
| --- | --- |
| `ck_sched_amount` | `amount > 0` |

---

## `auto_transfer`

> 자동이체 등록

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `auto_transfer_id` | `BIGINT` | **PK** | X |  | 자동이체 등록 건 내부 식별자. 변경·해지 API의 경로 변수 |
| `customer_id` | `BIGINT` | **FK** | X |  | 등록한 고객 → `customer.customer_id` |
| `withdrawal_account_id` | `BIGINT` | **FK** | X |  | 출금계좌 → `account.account_id` |
| `deposit_account_number` | `CHAR(12)` |  | X |  | 입금 계좌번호. 상대 계좌라 내부 PK가 아닌 번호로 지칭한다 |
| `payee_name` | `VARCHAR(50)` |  | X |  | 예금주명. 등록 시점 스냅샷 |
| `amount` | `BIGINT` |  | X |  | 회차마다 이체할 금액 |
| `cycle_months` | `TINYINT` |  | X |  | 이체 주기(개월). `1` / `3` / `6` 중 하나 |
| `transfer_day` | `TINYINT` |  | X |  | 매월 이체 지정일. 1~31 (AUT0001) |
| `start_date` | `DATE` |  | X |  | 이체 시작일 |
| `end_date` | `DATE` |  | X |  | 이체 종료일. 시작일 이후 60개월 이내 (AUT0002) |
| `next_execution_date` | `DATE` |  | X |  | 다음 실행 예정일. 배치가 이 값으로 대상을 고른다 |
| `my_passbook_memo` | `VARCHAR(10)` |  | O |  | 내 통장에 찍히는 문구 |
| `recipient_passbook_memo` | `VARCHAR(10)` |  | O |  | 받는 분 통장에 찍히는 문구 |
| `status` | `VARCHAR(12)` |  | X |  | **등록 건** 생명주기 상태. `NORMAL`(운용 중) / `EXPIRED`(기간 만료로 시스템 자동 종료) / `TERMINATED`(고객 직접 해지). 회차 실행결과와는 전혀 다른 개념이다 |
| `registered_at` | `DATETIME(6)` |  | X |  | 등록 일시 |
| `terminated_at` | `DATETIME(6)` |  | O |  | 해지·만료 전환 시각 |
| `updated_at` | `DATETIME(6)` |  | X |  | 행 최종 수정 일시 |
| `active_dup_key` | `VARCHAR(64)` *GEN* | **UK** | O |  | **생성 컬럼.** `NORMAL`일 때만 값이 생겨 중복 등록을 막는다 (AUT0301) |

**인덱스**

| 종류 | 이름 | 컬럼 |
| --- | --- | --- |
| UNIQUE | `uk_auto_active_dup` | `active_dup_key` |
| INDEX | `ix_auto_batch` | `status, next_execution_date` |
| INDEX | `ix_auto_deposit_status` | `deposit_account_number, status` |

**CHECK 제약**

| 이름 | 조건 |
| --- | --- |
| `ck_auto_cycle` | `cycle_months IN (1, 3, 6)` |
| `ck_auto_day` | `transfer_day BETWEEN 1 AND 31` |
| `ck_auto_period` | `end_date >= start_date` |
| `ck_auto_amount` | `amount > 0` |

---

## `auto_transfer_execution`

> 자동이체 회차 실행결과

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `execution_id` | `BIGINT` | **PK** | X |  | 회차 내부 식별자 |
| `auto_transfer_id` | `BIGINT` | **FK** **UK** | X |  | 상위 자동이체 등록 건 → `auto_transfer.auto_transfer_id` |
| `execution_date` | `DATE` | **UK** | X |  | 회차 실행 예정일. 등록 건의 `transfer_day`로 계산된다 |
| `amount` | `BIGINT` |  | X |  | 회차 이체금액. 등록 건 금액이 나중에 바뀌어도 실행 시점 값을 남긴다 |
| `status` | `VARCHAR(12)` |  | X |  | **회차 실행결과.** `SUCCESS`(정상) / `ERROR`(오류) / `PROCESSING`(처리중). 등록 건 상태와 별개 Enum이다 |
| `transaction_number` | `CHAR(20)` |  | O |  | 성공 시 생성된 거래번호. 실패 건은 NULL |
| `failure_reason` | `VARCHAR(200)` |  | O |  | 실패 사유 |
| `executed_at` | `DATETIME(6)` |  | X |  | 실제 실행 일시 |

**인덱스**

| 종류 | 이름 | 컬럼 |
| --- | --- | --- |
| UNIQUE | `uk_ate_dup` | `auto_transfer_id, execution_date` |

---

# 8. 공통 인프라 — P5

## `idempotency_key`

> 멱등키 (common_rev 7-2)

`request_hash` 계산 시 `*AuthToken`으로 끝나는 필드는 제외한다. 네트워크 지연 재요청에서 토큰만 갱신돼 다른 요청으로 오탐지되는 것을 막는다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `idempotency_key` | `CHAR(36)` | **PK** | X |  | 클라이언트가 보낸 `Idempotency-Key` 헤더값(UUID v4). 같은 요청의 중복 실행을 막는 기준 |
| `customer_id` | `BIGINT` | **FK** | O |  | 요청 고객 → `customer.customer_id`. 고객 생성 전 회원가입 완료 멱등키 예약은 `NULL` |
| `endpoint` | `VARCHAR(120)` |  | X |  | 요청 대상 엔드포인트. 같은 키가 다른 API에 재사용되는 것을 걸러낸다 |
| `request_hash` | `CHAR(64)` |  | X |  | 요청 본문의 SHA-256 해시. 같은 키인데 값이 다르면 CMN0302 |
| `state` | `VARCHAR(12)` |  | X |  | 처리 상태. `PROCESSING`(처리 중) / `COMPLETED`(완료). 재요청 시 CMN0301과 200 반환을 가르는 기준 |
| `http_status` | `SMALLINT` |  | O |  | 저장된 응답의 HTTP 상태코드 |
| `response_snapshot` | `JSON` |  | O |  | 저장된 응답 본문. 재요청 시 그대로 되돌려준다 |
| `created_at` | `DATETIME(6)` |  | X |  | 최초 요청 수신 시각 |
| `expires_at` | `DATETIME(6)` |  | X |  | 만료 시각. 24시간 경과 건은 배치가 지운다 |

**인덱스**

| 종류 | 이름 | 컬럼 |
| --- | --- | --- |
| INDEX | `ix_idem_expires` | `expires_at` |

---

## `audit_log`

> 감사 로그 (REQ-NFR-010). 조회 API 없으나 규제 근거로 유지

조회 API는 없지만 REQ-NFR-010이 기록 항목을 특정하고 있어 정형 테이블로 유지한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `audit_log_id` | `BIGINT` | **PK** | X |  | 감사 로그 내부 식별자 |
| `customer_id` | `BIGINT` |  | O |  | 행위 고객. 비로그인 시도는 NULL |
| `transaction_number` | `CHAR(20)` |  | O |  | 원장 변경 거래의 거래번호. 거래와 로그를 잇는 키 |
| `event_type` | `VARCHAR(40)` |  | X |  | 감사 이벤트 종류. 이체 실행·한도 변경 등 원장에 영향을 준 행위를 구분한다 |
| `request_ip` | `VARCHAR(45)` |  | X |  | 요청 클라이언트 IP. IPv6까지 담기도록 45자 |
| `result` | `VARCHAR(12)` |  | X |  | 처리 결과. `SUCCESS` / `FAILURE` |
| `detail` | `JSON` |  | O |  | 부가 정보. 비밀번호·전체 계좌번호 등은 마스킹 후 저장한다 |
| `access_reason` | `VARCHAR(20)` |  | O |  | 관리자 개인정보 조회·변경 사유(PH-89, D-10). `CUSTOMER_REQUEST` · `INCIDENT` · `AUDIT` · `OPERATION`. 그 밖의 행은 NULL |
| `row_hash` | `CHAR(64)` |  | O |  | 무결성 해시(PH-82). `audit_log_id`를 뺀 전 컬럼을 키 정렬 JSON으로 만든 SHA-256(64자 hex). 매일 WORM 버킷에 올린 사본과 대조한다. 도입 전 행은 NULL이며 적재 대상이 아니다 |
| `requested_at` | `DATETIME(6)` |  | X |  | 요청 일시 |

**인덱스**

| 종류 | 이름 | 컬럼 |
| --- | --- | --- |
| INDEX | `ix_audit_customer` | `customer_id, requested_at DESC` |
| INDEX | `ix_audit_txno` | `transaction_number` |
| INDEX | `ix_audit_requested_at` | `requested_at` — WORM 적재 · 검증 배치의 하루 범위 조회(PH-82) |

---

## `audit_archive_execution`

> 감사 로그 WORM 적재 상태 (PH-82, #788)

`AuditLogArchiveService`가 날짜 · 경로(`LOG` / `ACCESS`)마다 업로드 전에 `PENDING`을 커밋하고, 업로드가 끝나면 `SUCCESS`, 실패하면 `FAILURE`와 사유를 남긴다. 매일 00:30 배치가 최근 7일 중 `SUCCESS`가 아닌 날만 다시 올린다. 7일을 넘긴 미완료 날은 자동으로 다시 올리지 않는다. 저장소는 같은 키를 덮어쓰지 않으므로(S3 `If-None-Match: *`), 사본이 이미 있으면 내용이 같을 때만 `SUCCESS`로 확정한다. **`SUCCESS`인데 사본이 없으면 다시 올리지 않는다** — 지워진 증거를 현재 DB 내용으로 덮지 않기 위해서다. 다른 테이블과 FK는 없다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `audit_archive_execution_id` | `BIGINT` | **PK** | X |  | 내부 식별자 (AUTO_INCREMENT) |
| `archive_date` | `DATE` | **UK** | X |  | 적재 대상 날짜(`audit_log.requested_at` 기준) |
| `archive_type` | `VARCHAR(10)` | **UK** | X |  | `LOG` / `ACCESS` |
| `status` | `VARCHAR(10)` |  | X |  | `PENDING` / `SUCCESS` / `FAILURE` |
| `object_key` | `VARCHAR(100)` |  | X |  | 저장소 객체 키. 예: `audit/log/2026/10/28.jsonl` |
| `row_count` | `INT` |  | O |  | 적재한 행 수. 성공 때만 채우고 0건도 남긴다 |
| `failure_reason` | `VARCHAR(500)` |  | O |  | 마지막 실패 사유 |
| `completed_at` | `DATETIME(6)` |  | O |  | 성공 시각(KST) |
| `updated_at` | `DATETIME(6)` |  | X |  | 마지막 상태 변경 시각(KST, 애플리케이션이 채움) |

| 구분 | 인덱스 | 컬럼 |
| --- | --- | --- |
| UNIQUE | `uk_audit_archive_execution_date_type` | `archive_date, archive_type` |

---

## `audit_integrity_mismatch`

> 감사 로그 무결성 불일치 목록 (PH-82)

검증 배치(매일 01:00, 최근 7일)가 WORM 사본과 DB 감사 행을 대조해 찾은 불일치를 날짜별로 남긴다. **증거라 지우지 않는다. 같은 날을 다시 검증해도 이미 남긴 불일치는 건너뛰고 새로 찾은 것만 쌓는다.** 지워진 감사 행도 기록해야 하므로 `audit_log`에 FK를 걸지 않는다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `audit_integrity_mismatch_id` | `BIGINT` | **PK** | X | `AUTO_INCREMENT` | 불일치 행 식별자 |
| `archive_date` | `DATE` | **UK** | X |  | 검증한 사본의 날짜(`audit_log.requested_at` 기준) |
| `audit_log_id` | `BIGINT` | **UK** | X |  | 불일치 감사 행 |
| `mismatch_type` | `VARCHAR(20)` | **UK** | X |  | `TAMPERED`(다시 계산한 해시가 사본과 다름) · `MISSING_IN_DB`(사본에만 있음) · `NOT_ARCHIVED`(DB에만 있음) |
| `detected_at` | `DATETIME(6)` |  | X |  | 검증 배치가 불일치를 확인한 시각 |

**인덱스**

| 종류 | 이름 | 컬럼 |
| --- | --- | --- |
| UNIQUE | `uk_audit_integrity_mismatch_date_log_type` | `archive_date, audit_log_id, mismatch_type` |

---

## `common_code`

> 공통코드 (REQ-CMN-023). 코드값 → 화면 표시명 매핑

`code` 값은 서버 Enum 값과 문자열이 정확히 일치해야 한다. Enum에는 있는데 이 테이블에 행이 없으면 프론트가 표시명 매핑에 실패한다. P5 테이블 중 유일하게 `created_at`/`updated_at`을 모두 가져 `BaseEntity`를 상속한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `code_group` | `VARCHAR(50)` | **PK** | X |  | 코드 그룹명. 예: `ACCOUNT_STATUS` |
| `code` | `VARCHAR(50)` | **PK** | X |  | 코드값. 서버 Enum 값과 일치. 예: `ACTIVE` |
| `code_name` | `VARCHAR(100)` |  | X |  | 화면 표시 한글명. 예: `정상` |
| `sort_order` | `INT` |  | X |  | 드롭다운 표시 순서 |
| `use_yn` | `CHAR(1)` |  | X | `Y` | 사용 여부. `Y`/`N`. 물리 삭제 대신 사용 |
| `description` | `VARCHAR(200)` |  | O |  | 관리자용 설명 |
| `created_at` | `DATETIME(6)` |  | X |  | JPA Auditing |
| `updated_at` | `DATETIME(6)` |  | X |  | JPA Auditing |

**인덱스**

| 종류 | 이름 | 컬럼 |
| --- | --- | --- |
| INDEX | `ix_common_code_group` | `code_group, use_yn, sort_order` |

---

## `batch_execution_lock`

> 배치 중복 트리거 방지 락 (#189). Apache Fineract `SchedulerTriggerListener.vetoJobExecution()` 패턴 참고

다중 인스턴스 배포 시 같은 배치(예: `DailyTransferBatchScheduler.runDailyBatch()`)가 동시에 두 번 트리거되는 걸 막는다. `SELECT ... FOR UPDATE`로 행을 잠그고 플래그만 확인·갱신한 뒤 즉시 커밋하는 짧은 트랜잭션이라, 배치 실행 전체 동안 이 테이블을 잠그지 않는다. `currently_running=TRUE`인 채로 `updated_at`이 stale 임계값(6시간)보다 오래되면 서버 크래시로 락 해제가 못 불린 것으로 보고 강제로 재획득한다. 배치 종류마다 `job_name`을 하나씩 등록해 재사용한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `job_name` | `VARCHAR(50)` | **PK** | X |  | 배치 잡 식별자. `DAILY_TRANSFER_BATCH`, `IDEMPOTENCY_KEY_CLEANUP`, `LEDGER_RECONCILIATION_BATCH`, `AUTH_TOKEN_CLEANUP`, `TERMS_VIEW_HISTORY_CLEANUP`, `AUTO_TRANSFER_RECONCILE`, `SCHEDULED_TRANSFER_RECONCILE`, `COB`, `AUDIT_LOG_ARCHIVE`, `AUDIT_INTEGRITY_CHECK` 10행 |
| `currently_running` | `BOOLEAN` |  | X | `FALSE` | 이 배치가 지금 실행 중인지. 트리거 시작 시 `TRUE`, 종료 시(성공/실패 무관) `FALSE`로 되돌림 |
| `updated_at` | `DATETIME(6)` |  | X |  | 마지막 상태 변경 시각. stale(크래시로 방치됨) 판단 기준 |

---

## `batch_execution_history`

> COB 등 배치가 스텝을 실행한 기록 (#603)

`CobRunnerService`가 스텝(`CobStep`)을 하나 실행할 때마다 결과를 1행 추가한다(append-only, UPDATE·DELETE 없음). 스텝이 시작하기 전에는 행이 없고, 끝난 뒤(성공·실패·중단 중 하나로 확정된 뒤) 한 번만 INSERT한다. 관리자 조회 API는 아직 없다(#603 범위 밖).

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `batch_execution_history_id` | `BIGINT` | **PK** | X |  | 내부 식별자 (AUTO_INCREMENT) |
| `job_name` | `VARCHAR(50)` |  | X |  | 배치 식별자. 예: `COB` |
| `step_name` | `VARCHAR(100)` |  | X |  | `CobStep.name()` |
| `business_date` | `DATE` |  | X |  | 스텝이 처리한 영업일(`COB_DATE`) |
| `started_at` | `DATETIME(6)` |  | X |  | 스텝 시작 시각 |
| `ended_at` | `DATETIME(6)` |  | X |  | 스텝 종료(성공/실패/중단 확정) 시각 |
| `status` | `VARCHAR(10)` |  | X |  | `SUCCESS` / `FAILURE` / `HALTED`(`CobStepHaltException`, #600) |
| `failure_reason` | `TEXT` |  | O |  | 실패·중단 시 예외 메시지. `SUCCESS`면 `NULL` |

---

## `auth_token`

> 일회성 인증 토큰 (#580). Redis에 있던 토큰 7종을 옮겼다

약관 동의 · 아이디 확인 · 이메일 인증 · 계좌 인증 · 임시가입(회원가입 5종)과 OTP 인증 · 계좌비밀번호 인증 토큰을 한 테이블에 둔다. 토큰 원문은 저장하지 않고 SHA-256 해시로 찾는다. 소비는 `consumed_at IS NULL AND claim_id IS NULL AND expires_at > now` 조건부 `UPDATE` 한 번이고, 영향받은 행이 1일 때만 성공이다. 임시가입 토큰만 예외로, 선점한 가입 완료 요청이 `claim_id = 자기 요청 ID` 조건으로 소비한다. 저장·소비 모두 호출자 트랜잭션에 참여하므로 업무가 롤백되면 소비도 롤백된다(#498).

유효성은 항상 `expires_at`으로 판정한다. `AUTH_TOKEN_CLEANUP` 배치는 만료 행을 지워 공간을 회수할 뿐이다. 길어야 30분 사는 데이터라 `customer`에 FK를 걸지 않는다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `auth_token_id` | `BIGINT` | **PK** | X |  | 내부 식별자 (AUTO_INCREMENT) |
| `purpose` | `VARCHAR(30)` | **UK** | X |  | 토큰 종류. `TERMS_AUTH` / `USER_ID_CHECK` / `EMAIL_VERIFICATION` / `ACCOUNT_AUTH` / `TEMP_SIGNUP` / `OTP_AUTH` / `ACCOUNT_PASSWORD_AUTH` |
| `token_hash` | `CHAR(64)` | **UK** | X |  | 토큰 원문의 SHA-256 16진수 |
| `customer_id` | `BIGINT` |  | O |  | 로그인 고객 토큰의 고객. 회원가입 토큰은 `NULL` |
| `payload` | `TEXT` |  | X |  | 토큰에 묶인 값(JSON 문자열). `utf8mb4_bin`이라 값 일치 소비가 바이트 단위로 비교된다 |
| `claim_id` | `CHAR(36)` |  | O |  | 임시가입 토큰을 선점한 가입 완료 요청 ID. 선점 전·복구 후는 `NULL` |
| `created_at` | `DATETIME(6)` |  | X |  | 발급 시각 |
| `expires_at` | `DATETIME(6)` |  | X |  | 만료 시각. 이 시각이 지나면 조회·소비되지 않는다 |
| `consumed_at` | `DATETIME(6)` |  | O |  | 소비 시각. `NULL`이면 아직 쓰지 않은 토큰 |

**인덱스**

| 종류 | 이름 | 컬럼 |
| --- | --- | --- |
| UNIQUE | `uk_auth_token_hash_purpose` | `token_hash, purpose` |
| INDEX | `ix_auth_token_expires` | `expires_at` |

---

## `otp_issue_lock`

> 고객별 OTP 발급 잠금 (#580). Redis `SET NX` 잠금을 옮겼다

같은 고객의 OTP 발급 요청을 여러 인스턴스에서 한 번에 하나만 통과시킨다. 잠금은 발급 트랜잭션에 참여하지 않고(`NOT_SUPPORTED`) 문장마다 바로 커밋해 다른 인스턴스에 즉시 보이게 한다. 획득(`INSERT IGNORE` → 만료 잠금 `UPDATE`)을 한 트랜잭션에 묶으면 교착이 날 수 있어서다. 만료된 잠금은 다른 요청이 다시 잡을 수 있고, 해제는 `owner_id`가 같을 때만 된다. 고객당 1행이라 정리 배치가 없다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `customer_id` | `BIGINT` | **PK** | X |  | 잠금 대상 고객 |
| `owner_id` | `CHAR(36)` |  | X |  | 잠금을 잡은 발급 요청 ID(UUID) |
| `expires_at` | `DATETIME(6)` |  | X |  | 잠금 만료 시각 |

---

## `terms_view_history`

> 상품 약관 열람 이력 (#580). Redis TTL 30분 키를 옮겼다

상품가입 전 약관을 열람했는지 30분 동안 인정한다. 다시 열람하면 같은 행의 `viewed_at`·`expires_at`을 덮어쓴다. 유효성은 `expires_at`으로 판정하므로 만료 즉시 열람으로 인정되지 않는다. 행 삭제는 매시 45분에 도는 `TERMS_VIEW_HISTORY_CLEANUP` 배치가 맡아서, 만료된 행이 최대 약 1시간(열람 시점부터 약 90분) DB에 남을 수 있다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `customer_id` | `BIGINT` | **PK** | X |  | 열람 고객 |
| `terms_id` | `BIGINT` | **PK** | X |  | 열람한 약관 |
| `viewed_at` | `DATETIME(6)` |  | X |  | 마지막 열람 시각 |
| `expires_at` | `DATETIME(6)` |  | X |  | 열람 인정 만료 시각 (열람 후 30분) |

**인덱스**

| 종류 | 이름 | 컬럼 |
| --- | --- | --- |
| INDEX | `ix_terms_view_history_expires` | `expires_at` |

---

## `unregistered_login_failure`

> 가입되지 않은 아이디의 로그인 실패 횟수 (PH-08, #900)

가입 계정은 `customer.login_failure_count`로 아이디 기준 실패를 세고 5회째에 `ATH0102`로 잠긴다. 없는 아이디도 같은 기준으로 세어 5회째에 같은 응답을 주므로 로그인 응답으로는 반복 요청해도 계정 존재 여부를 구분할 수 없다. 회원가입의 아이디 중복 확인(`POST /auth/check-id`)은 가입 여부를 바로 알려 주는 기능이라 이 범위 밖이다. 남은 한계로, 가입 계정의 횟수는 로그인 성공·비밀번호 재설정·관리자 잠금 해제 때 0이 되지만 이 테이블의 횟수는 만료(마지막 실패 후 30일) 전까지 줄지 않는다. 그래서 시간을 두고 시도하면 횟수가 줄어드는지로 가입 계정임을 알 수 있다. 계정 주인이 그 사이에 로그인해야 드러나는 차이라 공격 비용이 높아 막지 않고 기록만 한다. 아이디는 소문자로 정규화한 뒤 전용 키의 HMAC-SHA256 hex로만 저장하고 원문은 두지 않는다. 실패마다 `INSERT … ON DUPLICATE KEY UPDATE` 한 문장으로 1을 더하고, 만료된 행이면 1부터 다시 센다. 잠긴 뒤의 시도는 만료 시각을 늘리지 않는다. 행 삭제는 매일 04:40에 도는 `UNREGISTERED_LOGIN_FAILURE_CLEANUP` 배치가 맡는다. 같은 DB를 쓰는 새벽 작업인 RDS 자동 백업(03:00~03:30, `terraform/main/modules/data/main.tf` `backup_window`), 정보계 ETL(03:30, [PH-76](phase2/ph76_etl_first_load.md)), RDS 점검 창(월 04:00~04:30, `maintenance_window`)이 모두 끝난 뒤로 잡았다. 백업은 창을 넘겨도 끝날 때까지 계속되므로([AWS RDS 백업 창](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_ManagingAutomatedBackups.html#USER_WorkingWithAutomatedBackups.BackupWindow)) 바로 뒤에 붙이지 않았다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `user_id_hash` | `CHAR(64)` | **PK** | X |  | 소문자 정규화한 로그인 아이디의 HMAC-SHA256 hex |
| `failure_count` | `INT` |  | X |  | 만료 전 누적 실패 횟수 |
| `last_failed_at` | `DATETIME(6)` |  | X |  | 마지막 실패 시각 |
| `expires_at` | `DATETIME(6)` |  | X |  | 마지막 실패 후 30일 |

**인덱스**

| 종류 | 이름 | 컬럼 |
| --- | --- | --- |
| INDEX | `ix_unregistered_login_failure_expires` | `expires_at` |

---

## `EVENT_PUBLICATION`

> 이벤트 발행 기록 — 아웃박스 (PH-96, #437). Spring Modulith Event Publication Registry가 소유한다

업무 트랜잭션 안에서 발행된 이벤트를 받을 AFTER_COMMIT 리스너마다 1행을 같은 커밋으로 남긴다. 리스너가 성공하면 행을 지운다(`spring.modulith.events.completion-mode=delete`). 그래서 **남아 있는 행이 곧 아직 전달되지 않은 이벤트**다. 커밋 직후 리스너가 실패하거나 프로세스가 죽어도 행이 남아 다시 전달할 수 있다.

DDL은 `spring-modulith-events-jdbc` 2.0.8의 `schemas/v2/schema-mysql.sql`을 그대로 옮겼다. **테이블·컬럼명이 대문자**인 것은 라이브러리가 이 이름으로 조회하기 때문이고, **시각이 `TIMESTAMP(6)`**(2038-01-19까지)인 것은 원본 DDL을 바꾸지 않았기 때문이다([ADR-0010](adr/0010-outbox-spring-modulith-event-publication-registry.md)). 행은 애플리케이션이 직접 쓰지 않고 라이브러리만 쓴다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `ID` | `VARCHAR(36)` | **PK** | X |  | 발행 기록 식별자 (UUID) |
| `LISTENER_ID` | `VARCHAR(512)` |  | X |  | 이벤트를 받을 리스너 메서드 식별자 |
| `EVENT_TYPE` | `VARCHAR(512)` |  | X |  | 이벤트 클래스 전체 이름 |
| `SERIALIZED_EVENT` | `VARCHAR(4000)` |  | X |  | Jackson으로 직렬화한 이벤트 JSON |
| `PUBLICATION_DATE` | `TIMESTAMP(6)` |  | X |  | 발행 시각 |
| `COMPLETION_DATE` | `TIMESTAMP(6)` |  | O | `NULL` | 완료 시각. `delete` 모드에서는 완료 시 행이 지워져 쓰이지 않는다 |
| `STATUS` | `VARCHAR(20)` |  | O |  | `PUBLISHED` / `PROCESSING` / `COMPLETED` / `FAILED` / `RESUBMITTED` |
| `COMPLETION_ATTEMPTS` | `INT` |  | O |  | 전달 시도 횟수 |
| `LAST_RESUBMISSION_DATE` | `TIMESTAMP(6)` |  | O | `NULL` | 마지막 재제출 시각 |

**인덱스**

| 종류 | 이름 | 컬럼 |
| --- | --- | --- |
| INDEX | `EVENT_PUBLICATION_BY_COMPLETION_DATE_IDX` | `COMPLETION_DATE` |

---

# 9. 회계 원장 — P3

2차에 신설(PH-20, #451). 이자 지급(P2)과 타행 이체(P4)가 들어오면서 예수금 안의 이동만 있던 전제가 깨져 회계 원장이 필요해졌다.

Apache Fineract 의 `acc_gl_account`·`acc_gl_journal_entry` 를 대조 기준으로 삼되 두 가지는 다르다. **금액을 `BIGINT` 원 단위 정수로 둔다**(Fineract 는 `DECIMAL(19,6)`, 절대규칙 5). **전표를 별도 테이블로 뺀다**(Fineract 는 `transaction_id` 를 공유하는 분개 묶음이 전표 역할) — 전표 단위로 차대변을 검증하고 채번 규칙을 둘 자리가 필요하다. 차대변 검증 자체는 Fineract 와 같이 애플리케이션에서만 한다([gl_journal_patterns.md](phase2/gl_journal_patterns.md) §2).

## `gl_account`

> 계정과목

코드 체계는 **대분류1 + 중분류2 + 세분류2 = 5자리**이고 첫 자리가 분류와 1:1 로 대응한다(1 자산 / 2 부채 / 3 자본 / 4 수익 / 5 비용). 시드 15개는 `R__seed_gl_account.sql` 이 넣는다 — 마스터성 데이터라 `V__` 가 아니라 `R__` 다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `account_code` | `CHAR(5)` | **PK** | X |  | 계정과목 코드. 대1+중2+세2. 첫 자리가 분류 |
| `account_name` | `VARCHAR(50)` |  | X |  | 계정과목명. 시산표 화면에 그대로 표시된다 |
| `account_class` | `VARCHAR(12)` |  | X |  | `ASSET` / `LIABILITY` / `EQUITY` / `REVENUE` / `EXPENSE` |
| `normal_balance` | `VARCHAR(6)` |  | X |  | 정상잔액 방향. `DEBIT` / `CREDIT`. 자산·비용은 차변, 부채·자본·수익은 대변 |
| `created_at` | `DATETIME(6)` |  | X | `CURRENT_TIMESTAMP(6)` |  |
| `updated_at` | `DATETIME(6)` |  | X | `CURRENT_TIMESTAMP(6)` |  |

**제약**

| 이름 | 내용 | 이유 |
| --- | --- | --- |
| `ck_gl_account_code` | `account_code REGEXP '^[1-5][0-9]{4}$'` | 코드 체계가 문서에만 있으면 깨진다. `ck_account_number` 와 같은 방식 |
| `ck_gl_account_class_code` | 코드 첫 자리 · `account_class` · `normal_balance` 다섯 조합 중 하나 | 열마다 따로 제한하면 `10100`+`LIABILITY` 나 `ASSET`+`CREDIT` 이 통과한다. 이 CHECK 가 두 열의 허용값 검사까지 포함한다. contra 계정(자산인데 정상잔액 대변)은 2차에 없고, 필요해지면 새 V 파일에서 넓힌다 |

---

## `gl_voucher`

> 전표 — 분개를 담는 단위

거래 1건이 장부에 남긴 기표 단위다. 분개 여러 줄을 담고 **전표 하나 안에서 차변 합계와 대변 합계가 같아야 한다.** 이 검증은 도메인(`gl.domain.Voucher`)에서만 하고 DB 제약은 걸지 않는다 — 근거는 [gl_journal_patterns.md](phase2/gl_journal_patterns.md) §2. 전표번호 `yyyyMMdd-TTT-NNNNNN` 은 `gl_voucher_sequence` 가 채번한다.

`updated_at` 이 없다. 전표는 수정하지 않는다 — 틀리면 지우거나 고치지 않고 정정 전표를 새로 세운다(용어표 "정정 체인"). 수정 시각 칸이 있으면 고쳐도 되는 것처럼 읽힌다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `voucher_no` | `VARCHAR(20)` | **PK** | X |  | 전표번호. 영업일 + 유형 + 일련 |
| `trade_date` | `DATE` |  | X |  | 이 전표가 귀속되는 영업일 |
| `tx_type` | `VARCHAR(24)` |  | X |  | `OPENING` / `TRANSFER` / `PRODUCT_SUBSCRIPTION` / `INTEREST` / `REVERSAL` / `INTERBANK_TRANSFER`(타행 송신 요청) / `INTERBANK_SETTLEMENT`(타행 확정 해소) / `MATURITY`(만기해지 원리금 이체) / `TERMINATION`(중도해지 원리금 이체) |
| `reference_key` | `VARCHAR(40)` |  | X |  | 원 거래번호. 원장 `transaction_number` 와 같은 값이고 개시 전표는 `OPENING-{yyyyMMdd}`. 원장과 전표를 1:1 로 잇는다(PH-24). 타행이체만 한 거래번호에 `INTERBANK_TRANSFER`·`INTERBANK_SETTLEMENT` 두 전표가 붙는다(`gl_journal_patterns.md` §3-5) |
| `description` | `VARCHAR(200)` |  | O |  | 적요 |
| `created_at` | `DATETIME(6)` |  | X | `CURRENT_TIMESTAMP(6)` |  |

**제약**

| 이름 | 내용 | 이유 |
| --- | --- | --- |
| `ck_gl_voucher_tx_type` | 9개 유형 중 하나 | `REVERSAL` 은 정정 체인 취소정정(PH-24). `INTERBANK_TRANSFER`·`INTERBANK_SETTLEMENT` 는 타행 미결제 2패턴([분개 패턴표](phase2/gl_journal_patterns.md) §3-5, PH-33-②). 두 전표는 같은 원 거래번호를 참조 키로 쓴다. `MATURITY`·`TERMINATION` 은 예적금 원리금 이체(분개 패턴표 §3-7, #713) |

**인덱스**

| 종류 | 이름 | 컬럼 |
| --- | --- | --- |
| UNIQUE | `uk_gl_voucher_no_trade_date` | `voucher_no, trade_date` |
| UNIQUE | `uk_gl_voucher_tx_type_reference_key` | `tx_type, reference_key` |

`voucher_no` 가 이미 PK 라 `uk_gl_voucher_no_trade_date` 는 행을 더 제한하지 않는다. 분개가 복제해 가는 `trade_date` 를 복합 FK 로 묶기 위한 참조 대상이다.

`uk_gl_voucher_tx_type_reference_key` 는 같은 거래를 두 번 기표하는 것을 막는다. 두 번째 기표는 예외가 되어 원장까지 롤백된다 — 조용히 넘기면 원장만 두 번 기표된 상태가 가려진다.

---

## `gl_journal_entry`

> 분개 — 전표 안의 한 줄

계정과목 하나와 **차변 또는 대변 한쪽의 금액**을 가진다. Fineract `acc_gl_journal_entry.type_enum` + `amount` 와 같은 형태다.

`trade_date` 를 전표에서 한 번 더 들고 간다. 600만 줄을 기간으로 거를 때 전표 조인을 없애기 위해서다 — Fineract 도 `entry_date` 를 같은 자리에 둔다. 분개는 전표와 함께 한 번만 쓰이고 수정되지 않으므로 두 값이 어긋날 경로가 없다.

**시산표 집계용 인덱스는 거래일이 먼저다(GL-IDX).** `ix_gl_journal_entry_trade_date (trade_date, account_code, dr_cr, amount)` 는 조회 기간만 범위로 읽고, 집계 SQL 이 쓰는 컬럼을 모두 담아 본 테이블을 읽지 않는다. 인덱스가 없을 때는 기간과 무관하게 분개 전체를 `fk_gl_journal_entry_account` 순서로 읽었다(운영 647만 행 전 구간 평균 26.8초, [ph28_baseline_results.md](phase2/ph28_baseline_results.md)). `account_code` 로 시작하는 인덱스는 넣지 않는다. `(account_code, trade_date)` 는 기간으로 좁히지 못해 오히려 느렸고, `fk_gl_journal_entry_account` 는 FK 가 자동으로 만든 인덱스라 같은 FK 를 받칠 수 있는 인덱스가 생기면 MySQL 이 조용히 지운다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `journal_entry_id` | `BIGINT` | **PK** | X |  | AUTO_INCREMENT |
| `voucher_no` | `VARCHAR(20)` | **FK** → `gl_voucher` | X |  | 이 줄이 속한 전표 |
| `line_no` | `SMALLINT` |  | X |  | 전표 안의 줄 번호. 1부터 |
| `account_code` | `CHAR(5)` | **FK** → `gl_account` | X |  | 계정과목 |
| `dr_cr` | `VARCHAR(6)` |  | X |  | `DEBIT` / `CREDIT`. 금액의 방향을 이 칸이 말한다 |
| `amount` | `BIGINT` |  | X |  | 원 단위 정수. 한 줄은 한쪽 금액만 가지므로 항상 양수 |
| `trade_date` | `DATE` |  | X |  | 전표 `trade_date` 복제본. 시산표 집계 기준일 |
| `created_at` | `DATETIME(6)` |  | X | `CURRENT_TIMESTAMP(6)` |  |

**인덱스**

| 종류 | 이름 | 컬럼 |
| --- | --- | --- |
| UNIQUE | `uk_gl_journal_entry_line` | `voucher_no, line_no` |
| FK | `fk_gl_journal_entry_voucher` | `voucher_no, trade_date` → `gl_voucher` |
| FK | `fk_gl_journal_entry_account` | `account_code` → `gl_account` |
| INDEX | `ix_gl_journal_entry_trade_date` | `trade_date, account_code, dr_cr, amount` |

**제약**

| 이름 | 내용 | 이유 |
| --- | --- | --- |
| `ck_gl_journal_entry_dr_cr` | `DEBIT` 또는 `CREDIT` | |
| `ck_gl_journal_entry_amount` | `amount > 0` | 0원 분개와 음수 금액을 막는다. 방향은 `dr_cr` 이 말한다 |
| `ck_gl_journal_entry_line_no` | `line_no > 0` | 줄 번호는 1부터다. `NOT NULL`·UNIQUE 만으로는 0 과 음수가 통과한다 |

**전표 FK 가 복합인 이유.** `voucher_no` 만 참조하면 전표와 다른 `trade_date` 를 가진 분개가 저장되고, 그대로 날짜별 시산표 집계가 틀어진다. 복제본이 원본과 같도록 DB 가 강제한다 — 특히 P6 시드 생성기(PH-60b)는 애플리케이션을 거치지 않고 600만 건을 직접 INSERT 하므로 애플리케이션 규약으로는 막을 수 없다.

---

## `gl_voucher_sequence`

> 전표번호 일련번호 채번 (PH-21)

전표번호 끝 6자리를 (거래일, 유형)마다 1부터 채번한다. 구조는 `transaction_sequence` 와 같다. 카운터는 기표 트랜잭션과 **별도로 커밋**한다 — 같은 트랜잭션이면 이체가 커밋될 때까지 행 락을 잡아 같은 날 같은 유형의 기표가 직렬화된다. 대가로 기표가 롤백되면 결번이 생긴다.

그날 행이 없으면 `gl_voucher` 에 이미 있는 가장 큰 번호 다음부터 시작한다. 시드(PH-60b)는 이 카운터를 거치지 않고 전표를 직접 넣기 때문이다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `trade_date` | `DATE` | **PK** | X |  | 전표의 거래일. 전표번호 앞 8자리와 같다 |
| `tx_type` | `VARCHAR(24)` | **PK** | X |  | `gl_voucher.tx_type` 과 같은 값 |
| `last_seq` | `INT` |  | X |  | 해당 거래일·유형에서 마지막으로 나간 일련번호 |
| `updated_at` | `DATETIME(6)` |  | X |  | 마지막 채번 시각 |

**CHECK 제약**

| 이름 | 조건 |
| --- | --- |
| `ck_gl_voucher_sequence_range` | `last_seq BETWEEN 1 AND 999999` |

---

# 10. 영업일 — P5

2차에 신설(PH-40, #471). 영업일은 시스템 시각과 분리된 **논리 업무일자**이고 `business.api.BusinessDateProvider` 가 유일한 출처다([glossary](phase2/glossary.md) 6번).

Apache Fineract 의 `m_business_date` 처럼 **시계가 아니라 DB 에 저장된 값**을 영업일로 쓴다. 테이블은 둘로 나눈다 — 매일 바뀌는 값 1개(`business_date`)와 한 번 넣고 거의 안 바뀌는 목록(`holiday`)은 성격이 다르다. Fineract 와 달리 영업일을 넘길 때 휴일을 건너뛴다(Fineract 는 `plusDays(1)` 이고 휴일은 별도 개념).

## `business_date`

> 현재 영업일

`date_type` 별로 한 행이다. 2차에는 `BUSINESS_DATE` 한 행뿐이고, 마감 대상일(`COB_DATE`) 같은 종류가 필요해지면 테이블을 바꾸지 않고 행을 더한다(Fineract `BusinessDateType` 과 같은 구조).

초기값 `2026-01-01` 은 의미 없는 과거 날짜다 — 서버가 뜰 때 오늘 달력에 맞춘다(오늘이 영업일이면 오늘, 아니면 다음 영업일. 저장값이 그보다 이전일 때만 바꾼다). V 파일이 실행되는 날은 환경마다(팀원 로컬·CI·배포) 달라서 특정 날짜를 박으면 미래나 과거로 어긋난다. `NOW()`·`CURRENT_DATE` 는 RDS 가 UTC 라 쓰지 않는다. 이후 영업일은 매일 자정(KST) 같은 방식으로 맞추고, PH-43 이후에는 COB 마지막 스텝이 다음 영업일로 넘긴다. 기동 시 맞추기는 PH-43 이후에도 남는다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `date_type` | `VARCHAR(20)` | **PK** | X |  | 영업일 종류. 2차는 `BUSINESS_DATE` 1행 |
| `business_date` | `DATE` |  | X |  | 현재 영업일 |
| `created_at` | `DATETIME(6)` |  | X | `CURRENT_TIMESTAMP(6)` |  |
| `updated_at` | `DATETIME(6)` |  | X | `CURRENT_TIMESTAMP(6)` | 마지막 영업일 전환 시각 |

---

## `business_date_cutoff`

> 영업일 마감 cutoff (#556)

COB의 영업일 전환(`advanceFrom`)과 달력 따라잡기(`catchUpTo`)가 `BUSINESS_DATE`를 다음 영업일로 넘길 때 떠나는 영업일 D의 행을 같은 트랜잭션으로 남긴다. 전환 UPDATE가 `BUSINESS_DATE` 행에 배타 락을 잡으므로, D를 읽은 거래가 모두 commit/rollback된 뒤에만 이 행이 커밋된다. 레플리카에서 `status = 'CLOSED'`인 D가 보이면 D 거래일 원장이 더는 추가되지 않는다(`info_mart.md` §3-0). `batch_execution_history`는 이 신호로 쓰지 않는다. 기준일마다 CLOSED 행은 하나이고, 재실행은 멱등 INSERT로 처리한다. 다른 테이블과 FK는 없다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `business_date_cutoff_id` | `BIGINT` | **PK** | X |  | 내부 식별자 (AUTO_INCREMENT) |
| `base_date` | `DATE` | **UK** | X |  | 마감된 영업일 |
| `status` | `VARCHAR(10)` | **UK** | X |  | `CLOSED` |
| `closed_at` | `DATETIME(6)` |  | X |  | 마감 시각(KST, 애플리케이션이 채움) |

| 구분 | 인덱스 | 컬럼 |
| --- | --- | --- |
| UNIQUE | `uk_business_date_cutoff_date_status` | `base_date, status` |

---

## `cob_checkpoint`

> COB 재시작 체크포인트 (#663)

`CobRunnerService`가 새 COB를 시작할 때, 영업일을 넘기기 전에 마감 대상 영업일(`COB_DATE`)의 영업일 전환 행과 등록된 스텝 행을 모두 `PENDING`으로 남기고, 전환과 스텝이 끝날 때마다 `SUCCESS`로 바꾼다. `PENDING`이 남은 `cob_date`가 있으면 다음 `run()`이 오래된 날짜부터 그 날짜의 `PENDING` 스텝만 이어서 실행한다. 행이 없는 스텝(초기화 실패 등)은 재개 대상이 아니다. `batch_execution_history`는 관측용이라 재시작 근거로 쓰지 않고, `business_date_cutoff`는 달력 따라잡기도 기록하므로 `COB_DATE`의 근거가 아니다. 다른 테이블과 FK는 없다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `cob_checkpoint_id` | `BIGINT` | **PK** | X |  | 내부 식별자 (AUTO_INCREMENT) |
| `cob_date` | `DATE` | **UK** | X |  | 마감 대상 영업일(ADR-0004 `COB_DATE`) |
| `step_name` | `VARCHAR(100)` | **UK** | X |  | `CobStep.name()`. 영업일 전환은 `business-date-advance` |
| `status` | `VARCHAR(10)` |  | X |  | `PENDING` / `SUCCESS` |
| `updated_at` | `DATETIME(6)` |  | X |  | 마지막 상태 변경 시각(KST, 애플리케이션이 채움) |

| 구분 | 인덱스 | 컬럼 |
| --- | --- | --- |
| UNIQUE | `uk_cob_checkpoint_date_step` | `cob_date, step_name` |

---

## `holiday`

> 휴일 달력 — 주말을 제외한 공휴일

**주말은 넣지 않는다** — 토·일은 요일로 판정한다. 영업일 = 주말도 아니고 이 테이블에도 없는 날. 시드는 `R__seed_holiday.sql` 이 넣는다 — 운영에도 있어야 하고 대체공휴일·임시공휴일이 추가될 수 있는 마스터성 데이터라 `V__` 가 아니라 `R__` 다. 등록 API 는 2차 범위 밖이다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `holiday_date` | `DATE` | **PK** | X |  | 휴일 날짜 |
| `holiday_name` | `VARCHAR(50)` |  | X |  | 예: `개천절`, `대체공휴일(추석)` |
| `created_at` | `DATETIME(6)` |  | X | `CURRENT_TIMESTAMP(6)` |  |
| `updated_at` | `DATETIME(6)` |  | X | `CURRENT_TIMESTAMP(6)` |  |
