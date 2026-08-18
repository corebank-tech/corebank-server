# 📐 CoreBank 미니 코어뱅킹 — 테이블 스키마 레퍼런스

**DBMS**: MySQL 8.4 · InnoDB · `utf8mb4_0900_ai_ci`
**대상**: 25개 비즈니스 테이블 + 1개 PK 채번 전용 테이블 (`ledger_entry_id_sequence`) · 259개 컬럼
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

금액은 전부 `BIGINT`(원 단위 정수), 금리는 `DECIMAL(5,2)`(연 %), 시각은 `DATETIME(6)`(UTC 저장·KST 표시), 계좌번호는 `CHAR(12)`, 거래번호는 `CHAR(20)`입니다.

---

## 테이블 목록

| # | 테이블 | 설명 | 담당 | 컬럼 |
| --- | --- | --- | --- | --- |
| 1 | `customer` | 고객 | P6 | 17 |
| 2 | `terms` | 약관 | P6 | 10 |
| 3 | `customer_terms_agreement` | 회원가입 약관 동의 | P6 | 4 |
| 4 | `verification_request` | 인증 요청 | P6 | 13 |
| 5 | `notification` | 알림 | P6 | 9 |
| 6 | `product` | 상품 | P3 | 22 |
| 7 | `product_rate_tier` | 상품 기간별 금리 | P3 | 3 |
| 8 | `product_preferential_rate` | 상품 우대금리 | P3 | 4 |
| 9 | `product_terms` | 상품-약관 연결 | P3 | 2 |
| 10 | `account` | 계좌 | P2 | 21 |
| 11 | `transaction_sequence` | 거래번호 일련번호 채번 | P4 | 4 |
| 12 | `transfer` | 이체 거래 | P4 | 20 |
| 13 | `ledger_entry` | 원장 | P4 | 13 |
| 14 | `ledger_entry_id_sequence` | 원장 PK 전용 채번 | P4 | 1 |
| 15 | `favorite_account` | 자주 쓰는 계좌 | P4 | 6 |
| 16 | `transfer_limit` | 이체한도 | P1 | 6 |
| 17 | `transfer_limit_daily_usage` | 일별 한도 사용액 | P1 | 5 |
| 18 | `transfer_limit_history` | 이체한도 변경 이력 | P1 | 8 |
| 19 | `product_subscription` | 상품가입 | P3 | 18 |
| 20 | `subscription_terms_agreement` | 상품 약관 동의 | P3 | 5 |
| 21 | `scheduled_transfer` | 예약이체 | P3 | 17 |
| 22 | `auto_transfer` | 자동이체 등록 | P5 | 18 |
| 23 | `auto_transfer_execution` | 자동이체 회차 실행결과 | P5 | 8 |
| 24 | `idempotency_key` | 멱등키 | P5 | 9 |
| 25 | `audit_log` | 감사 로그 | P5 | 8 |
| 26 | `common_code` | 공통코드 | P5 | 8 |

---

# 1. 고객 · 인증 — P6

## `customer`

> 고객

접속 이력(구 `login_history`)을 흡수했다. 대시보드가 쓰는 값이 직전·현재 2건뿐이라 별도 테이블을 두지 않는다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `customer_id` | `BIGINT` | **PK** | X |  | 고객 내부 식별자. 명세의 `customerId`. 클라이언트에 노출되는 유일한 고객 키 |
| `user_id` | `VARCHAR(20)` | **UK** | X |  | 고객이 직접 정한 로그인 아이디. 내부 PK인 `customer_id`와 다른 화면 입력값이다 |
| `password_hash` | `CHAR(60)` |  | X |  | 로그인 비밀번호의 BCrypt 해시. 평문·복호화 가능 암호화 금지 |
| `user_name` | `VARCHAR(50)` |  | X |  | 고객 실명. 응답 시 이름 길이별 마스킹 적용 (2자 `이*` / 3자 `홍*동` / 4자 이상 `남**수`) |
| `birth_date` | `DATE` |  | X |  | 생년월일. 실명계좌 인증·아이디 찾기의 대조값. 로그 평문 기록 금지 |
| `email` | `VARCHAR(100)` | **UK** | X |  | 이메일 주소. 연락처 겸 인증 수단. 중복 가입 시도 시 ATH0302 |
| `phone_number` | `VARCHAR(11)` |  | X |  | 휴대폰 번호. 하이픈 없이 숫자만 저장하고 응답 시 중간 4자리를 마스킹한다 |
| `login_failure_count` | `TINYINT` |  | X | `0` | 로그인 비밀번호 연속 오류 횟수. 5회 도달 시 `account_locked`가 TRUE로 바뀐다 (ATH0102) |
| `account_locked` | `BOOLEAN` |  | X | `FALSE` | 계정 잠금 여부. `login_failure_count` 5회 도달 시 TRUE, 관리자 잠금 해제로 FALSE 복귀 |
| `display_order_type` | `VARCHAR(20)` |  | X | `'OPENED_DATE_ASC'` | 계좌 목록 정렬 방식. `CUSTOM`(사용자 지정 순서) / `OPENED_DATE_ASC`(개설일 오름차순) |
| `last_login_at` | `DATETIME(6)` |  | O |  | 가장 최근 로그인 시각. 대시보드의 `currentLoginAt` |
| `last_login_ip` | `VARCHAR(45)` |  | O |  | 가장 최근 로그인 IP. 대시보드의 `currentLoginIp` |
| `previous_login_at` | `DATETIME(6)` |  | O |  | 직전 로그인 시각. 대시보드의 `previousLoginAt`. 부정 접속을 고객이 알아채는 단서 |
| `password_changed_at` | `DATETIME(6)` |  | O |  | 로그인 비밀번호 최종 변경 일시. 직전 비밀번호 재사용 제한(ATH0003) 판정 보조 |
| `joined_at` | `DATETIME(6)` |  | X |  | 회원가입 완료 일시. 고객정보 조회의 `joinedAt` |
| `created_at` | `DATETIME(6)` |  | X |  | 행 생성 일시 |
| `updated_at` | `DATETIME(6)` |  | X |  | 행 최종 수정 일시 |

**인덱스**

| 종류 | 이름 | 컬럼 |
| --- | --- | --- |
| UNIQUE | `uk_customer_user_id` | `user_id` |
| UNIQUE | `uk_customer_email` | `email` |

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

> 인증 요청 (이메일·비밀번호재설정·OTP 통합). *AuthToken 은 Redis

이메일 인증·비밀번호 재설정·OTP를 하나로 합쳤다. 셋 다 `코드 발급 → 검증 → 만료 → 1회용` 구조가 같고 식별자가 모두 String이라 통합이 가능했다. 검증 결과로 발급되는 `*AuthToken`은 이 테이블이 아니라 Redis에 있다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `verification_request_id` | `VARCHAR(64)` | **PK** | X |  | 인증 요청 식별자. 용도별 접두어(`EVF_` 이메일 / `PRR_` 비밀번호 재설정 / `OTP_REQ_` OTP) + CSPRNG 난수. 명세의 `emailVerificationId`·`passwordResetRequestId`·`otpRequestId`가 모두 이 컬럼이다 |
| `purpose` | `VARCHAR(24)` |  | X |  | 인증 요청의 용도. `SIGN_UP` / `EMAIL_CHANGE` / `PASSWORD_RESET` / `OTP_TRANSACTION`. 세 요청을 한 테이블에 합친 구분 축 |
| `customer_id` | `BIGINT` | **FK** | O |  | 요청 고객. 회원가입 전 이메일 인증은 고객이 아직 없어 NULL → `customer.customer_id` |
| `target` | `VARCHAR(100)` |  | O |  | 인증 대상 값. 이메일 인증은 수신 이메일 주소가 들어가고 OTP는 비어 있다 |
| `code_hash` | `CHAR(60)` |  | X |  | 인증번호·OTP 6자리의 단방향 해시. 평문 저장·로그 기록 금지 |
| `transaction_type` | `VARCHAR(32)` |  | O |  | **OTP 전용.** 어떤 거래를 승인하려는 OTP인지 (`IMMEDIATE_TRANSFER` 등) |
| `transaction_data` | `JSON` |  | O |  | **OTP 전용.** 출금계좌·입금계좌·금액 등 거래 핵심 정보. 최종 거래 API가 요청 본문과 대조해 변조를 잡는다 (OTP0102) |
| `error_count` | `TINYINT` |  | X | `0` | 인증번호 연속 오류 횟수. 5회를 넘기면 `locked`가 TRUE가 된다 (OTP0103) |
| `locked` | `BOOLEAN` |  | X | `FALSE` | 잠금 여부. `error_count`가 5를 넘으면 TRUE. 재발급 전까지 검증 불가 (OTP0103) |
| `used` | `BOOLEAN` |  | X | `FALSE` | 사용 완료 여부. 검증 성공 시 TRUE로 바꿔 재사용을 막는다 |
| `verified_at` | `DATETIME(6)` |  | O |  | 검증 성공 시각. 미검증이면 NULL |
| `expires_at` | `DATETIME(6)` |  | X |  | 유효시간 만료 시각. 발급 시각 + 180초 |
| `created_at` | `DATETIME(6)` |  | X |  | 발급 요청 시각 |

**인덱스**

| 종류 | 이름 | 컬럼 |
| --- | --- | --- |
| INDEX | `ix_vr_customer` | `customer_id, purpose, created_at DESC` |
| INDEX | `ix_vr_target` | `target, purpose, created_at DESC` |

---

## `notification`

> 알림

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `notification_id` | `BIGINT` | **PK** | X |  | 알림 내부 식별자. 읽음 처리 API의 경로 변수 |
| `customer_id` | `BIGINT` | **FK** | X |  | 수신 고객 → `customer.customer_id` |
| `type` | `VARCHAR(24)` |  | X |  | 알림을 발생시킨 업무 구분. `TRANSFER`(즉시이체) / `SCHEDULED_TRANSFER`(예약이체) / `AUTO_TRANSFER`(자동이체) / `PRODUCT_SUBSCRIPTION`(상품가입) |
| `ref_id` | `BIGINT` |  | O |  | 다형 참조: TRANSFER->transfer.transfer_id / SCHEDULED_TRANSFER->scheduled_transfer_id / AUTO_TRANSFER->auto_transfer_execution.execution_id / PRODUCT_SUBSCRIPTION->subscription_id |
| `title` | `VARCHAR(100)` |  | X |  | 알림 제목. 목록 화면의 한 줄 요약 |
| `content` | `VARCHAR(500)` |  | X |  | 알림 본문 문구 |
| `is_read` | `BOOLEAN` |  | X | `FALSE` | 읽음 여부. 미읽음 건수 조회의 집계 대상 |
| `read_at` | `DATETIME(6)` |  | O |  | 읽음 처리 시각. 미읽음이면 NULL |
| `occurred_at` | `DATETIME(6)` |  | X |  | 알림 발생 시각. 목록 정렬 기준 |

**인덱스**

| 종류 | 이름 | 컬럼 |
| --- | --- | --- |
| INDEX | `ix_noti_customer` | `customer_id, is_read, occurred_at DESC` |

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
| `status`                   | `VARCHAR(12)` |        | X    | `ACTIVE` | 계좌 상태. `ACTIVE`(정상) / `SUSPENDED`(거래정지) / `CLOSED`(해지)                            |
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
| `last_transaction_at`      | `DATETIME(6)` |        | O    |          | 최근 거래 일시. 전체 계좌 조회의 `lastTransactionAt`                                           |
| `version`                  | `BIGINT`      |        | X    | `0`      | 낙관적 락 버전. 배치·조회 경로의 갱신 충돌을 잡는다 (이체 실행 경로는 비관적 락)                                  |
| `created_at`               | `DATETIME(6)` |        | X    |          | 행 생성 일시. JPA Auditing이 UTC 기준으로 자동 설정                                             |
| `updated_at`               | `DATETIME(6)` |        | X    |          | 행 최종 수정 일시. JPA Auditing이 UTC 기준으로 자동 설정                                          |

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
| `ck_account_number`                   | `account_number REGEXP '^[0-9]{12}$'`                                                                                                                | 계좌번호는 하이픈 없는 숫자 12자리여야 한다.                         |
| `ck_account_type`                     | `account_type IN ('DEMAND_DEPOSIT', 'TIME_DEPOSIT', 'INSTALLMENT_SAVINGS')`                                                                          | 계좌 유형은 입출금·정기예금·정기적금 중 하나여야 한다.                    |
| `ck_account_status`                   | `status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')`                                                                                                        | 계좌 상태는 정상·거래정지·해지 중 하나여야 한다.                       |
| `ck_account_password_lock_state`      | 오류 횟수가 `0~4`이면 `password_locked = FALSE`, 오류 횟수가 `5`이면 `password_locked = TRUE`                                                                      | 계좌비밀번호 연속 오류 횟수와 잠금 상태가 항상 일치해야 한다.                |
| `ck_account_withdrawal_registered`    | `withdrawal_registered IN (FALSE, TRUE)`                                                                                                             | 출금계좌 등록 여부는 Boolean 값이어야 한다.                       |
| `ck_account_product`                  | `DEMAND_DEPOSIT`이면 `product_id IS NULL`<br>`TIME_DEPOSIT` 또는 `INSTALLMENT_SAVINGS`이면 `product_id IS NOT NULL`                                        | 입출금계좌에는 상품이 연결되지 않고, 예·적금계좌에는 상품이 반드시 연결되어야 한다.    |
| `ck_account_maturity`                 | `DEMAND_DEPOSIT`이면 `maturity_date IS NULL`<br>`TIME_DEPOSIT` 또는 `INSTALLMENT_SAVINGS`이면 `maturity_date IS NOT NULL AND maturity_date >= opened_date` | 입출금계좌에는 만기일이 없고, 예·적금계좌의 만기일은 개설일 이후여야 한다.         |
| `ck_account_withdrawal_type`          | `withdrawal_registered = FALSE OR account_type = 'DEMAND_DEPOSIT'`                                                                                   | 출금계좌 등록은 입출금계좌에만 허용한다.                             |
| `ck_account_withdrawal_registered_at` | 미등록이면 `withdrawal_registered_at IS NULL`<br>등록이면 `withdrawal_registered_at IS NOT NULL`                                                              | 출금계좌 등록 여부와 등록 일시의 존재 여부가 일치해야 한다.                 |
| `ck_account_closed_date`              | `CLOSED`이면 `closed_date IS NOT NULL AND closed_date >= opened_date`<br>`CLOSED`가 아니면 `closed_date IS NULL`                                           | 해지 계좌에는 개설일 이후의 해지일이 있어야 하며, 미해지 계좌에는 해지일이 없어야 한다. |

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

거래번호 일련 10자리를 채번한다. 일자·채널별로 매일 0부터 다시 시작하므로 `AUTO_INCREMENT`로 만들 수 없다. 전 고객이 같은 행을 두고 경합하는 유일한 지점이라 락 보유 시간을 최소로 유지해야 한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `seq_date` | `DATE` | **PK** | X |  | 채번 기준 영업일. KST 기준이며 거래번호 앞 8자리와 같다 |
| `channel` | `CHAR(2)` | **PK** | X |  | 채널 코드. `WB`(인터넷뱅킹) / `BT`(배치·예약이체). 거래번호 9~10번째 자리 |
| `last_seq` | `BIGINT` |  | X | `0` | 해당 일자·채널에서 마지막으로 나간 일련번호. 다음 거래는 이 값 + 1을 받는다 |
| `updated_at` | `DATETIME(6)` |  | X |  | 마지막 채번 시각 |

**CHECK 제약**

| 이름 | 조건 |
| --- | --- |
| `ck_txseq_range` | `last_seq BETWEEN 0 AND 9999999999` |

---

## `transfer`

> 이체 거래 (즉시·예약·자동 3종의 단일 수렴점)

즉시·예약·자동이체 3종이 모두 이 테이블 1행으로 수렴한다. 예약·자동은 `source_type`·`source_id`로 원본을 역추적한다. `status = ERROR`인 행은 원장 기표가 0행이다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `transfer_id` | `BIGINT` | **PK** | X |  | 이체 거래 내부 식별자. 대외 식별자는 `transaction_number` |
| `transaction_number` | `CHAR(20)` | **UK** | X |  | 대외 거래 식별자. `YYYYMMDD`(8) + 채널(2) + 일련(10) = 20자. 이체 상세 조회의 경로 변수이자 원장 2행을 잇는 키 |
| `withdrawal_account_id` | `BIGINT` | **FK** | X |  | 출금계좌 → `account.account_id` |
| `deposit_account_id` | `BIGINT` | **FK** | X |  | 입금계좌. 원장 입금 행의 `account_id` 근거. 1차는 당행 전용이라 항상 값이 있다 → `account.account_id` |
| `deposit_account_number` | `CHAR(12)` |  | X |  | 입금 계좌번호. 타행 확장을 대비해 번호도 함께 보관한다 |
| `payee_name` | `VARCHAR(50)` |  | X |  | 입금계좌 예금주명. **거래 시점 스냅샷**이라 이후 상대 정보가 바뀌어도 남는다 |
| `amount` | `BIGINT` |  | X |  | 이체금액 |
| `fee` | `BIGINT` |  | X | `0` | 수수료. 당행 이체는 0으로 고정 |
| `transfer_type` | `VARCHAR(12)` |  | X |  | 이체 종류. `IMMEDIATE`(즉시) / `SCHEDULED`(예약) / `AUTO`(자동) |
| `channel` | `CHAR(2)` |  | X |  | 거래 채널. `WB`(인터넷뱅킹) / `BT`(배치). 거래번호의 채널 2자리와 같은 값 |
| `status` | `VARCHAR(12)` |  | X |  | 처리 결과. `SUCCESS`(정상) / `ERROR`(오류) / `PROCESSING`(응답 유실·타임아웃 시에만) |
| `source_type` | `VARCHAR(12)` |  | O |  | 이 이체를 만든 원본 구분. `SCHEDULED`(예약이체) / `AUTO`(자동이체). 즉시이체는 NULL |
| `source_id` | `BIGINT` |  | O |  | 예약·자동이체 원본 PK. `source_type`과 함께 역추적에 쓴다 |
| `my_passbook_memo` | `VARCHAR(10)` |  | O |  | 내 통장에 찍히는 문구. 최대 10자 |
| `recipient_passbook_memo` | `VARCHAR(10)` |  | O |  | 받는 분 통장에 찍히는 문구 |
| `withdrawal_balance_after` | `BIGINT` |  | O |  | 출금 직후 출금계좌 잔액. 성공 시에만 값이 있다 |
| `error_code` | `VARCHAR(10)` |  | O |  | 실패 시 오류코드 |
| `error_message` | `VARCHAR(200)` |  | O |  | 실패 시 사유 문구 |
| `transferred_at` | `DATETIME(6)` |  | X |  | 이체 처리 일시 |
| `created_at` | `DATETIME(6)` |  | X |  | 행 생성 일시 |

**인덱스**

| 종류 | 이름 | 컬럼 |
| --- | --- | --- |
| UNIQUE | `uk_transfer_txno` | `transaction_number` |
| INDEX | `ix_transfer_wacc` | `withdrawal_account_id, transferred_at DESC` |
| INDEX | `ix_transfer_source` | `source_type, source_id` |

**CHECK 제약**

| 이름 | 조건 |
| --- | --- |
| `ck_transfer_amount` | `amount > 0` |
| `ck_transfer_fee` | `fee >= 0` |
| `ck_transfer_selfsend` | `withdrawal_account_id <> deposit_account_id` |

---

## `ledger_entry`

> 원장 (APPEND-ONLY. UPDATE/DELETE 금지)

**APPEND-ONLY.** `UPDATE`·`DELETE`를 금지한다. 취소는 반대 방향 기표를 새로 쌓는다. `occurred_at` 기준 RANGE 파티션이라 FK를 선언할 수 없고, 파티션 키가 PK에 포함돼야 해서 PK가 복합키다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `ledger_entry_id` | `BIGINT` | **PK** | X |  | 원장 행 식별자. 거래내역 조회 응답의 `ledgerEntryId` |
| `account_id` | `BIGINT` |  | X |  | 기표 대상 계좌 |
| `transfer_id` | `BIGINT` |  | O |  | 원본 이체 거래. 상품가입 초입금·이자 지급처럼 이체가 아닌 기표는 NULL |
| `transaction_number` | `CHAR(20)` |  | X |  | 이 기표가 속한 거래번호. **출금 행과 입금 행이 같은 값**을 갖는다 |
| `direction` | `VARCHAR(10)` |  | X |  | 기표 방향. `DEPOSIT`(입금) / `WITHDRAWAL`(출금). 금액은 늘 양수라 부호 역할을 이 컬럼이 한다 |
| `amount` | `BIGINT` |  | X |  | 기표 금액. 항상 양수 |
| `balance_after` | `BIGINT` |  | X |  | 이 기표 직후의 계좌 잔액 스냅샷. 통장 형태로 보여줄 때 쓴다 |
| `transaction_type` | `VARCHAR(32)` |  | X |  | 기표를 일으킨 업무. `IMMEDIATE_TRANSFER` / `SCHEDULED_TRANSFER` / `AUTO_TRANSFER` / `PRODUCT_SUBSCRIPTION`(가입 초입금) / `INTEREST`(이자) / `REVERSAL`(반대기표) |
| `transaction_content` | `VARCHAR(10)` |  | O |  | 통장에 찍히는 적요. 최대 10자 |
| `channel` | `CHAR(2)` |  | X |  | 거래 채널. `WB` / `BT` |
| `reversed` | `BOOLEAN` |  | X | `FALSE` | 반대기표로 취소된 원거래인지 여부. 원본을 지우지 않고 이 값만 세운다 |
| `reversal_id` | `BIGINT` |  | O |  | 반대기표 행이 가리키는 원거래의 `ledger_entry_id` |
| `occurred_at` | `DATETIME(6)` | **PK** | X |  | 기표 발생 일시. RANGE 파티션 키이며 거래내역 조회의 기간 조건이 이 컬럼에 걸린다 |

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

# 5. 이체한도 — P1

## `transfer_limit`

> 이체한도 (P1 소유 경계 유지)

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `customer_id` | `BIGINT` | **PK** **FK** | X |  | 대상 고객. 고객당 1행 → `customer.customer_id` |
| `one_time_limit` | `BIGINT` |  | X |  | 1회 이체한도. 초과하면 LMT0002 |
| `daily_limit` | `BIGINT` |  | X |  | 1일 이체한도. 초과하면 LMT0003 |
| `version` | `BIGINT` |  | X | `0` | 낙관적 락 버전 |
| `created_at` | `DATETIME(6)` |  | X |  | 한도 최초 부여 일시. REQ-TRSF-029로 가입 시 생성된다 |
| `updated_at` | `DATETIME(6)` |  | X |  | 행 최종 수정 일시 |

**CHECK 제약**

| 이름 | 조건 |
| --- | --- |
| `ck_tl_order` | `one_time_limit <= daily_limit` |
| `ck_tl_positive` | `one_time_limit > 0 AND daily_limit > 0` |

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

REQ-TRSF-025의 "변경 이력을 저장한다"를 담는다. 한도 변경 1건당 1행이 쌓이는 append-only 테이블이다. 변경자·요청 IP 같은 감사 정보는 `audit_log`의 책임이므로 여기에는 값의 전후 변화만 남긴다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 담기는 정보 |
| --- | --- | --- | --- | --- | --- |
| `history_id` | `BIGINT` | **PK** | X | `AUTO_INCREMENT` | 이력 식별자 |
| `customer_id` | `BIGINT` | **FK** | X |  | 대상 고객 → `customer.customer_id` |
| `before_one_time_limit` | `BIGINT` |  | X |  | 변경 전 1회 이체한도 |
| `after_one_time_limit` | `BIGINT` |  | X |  | 변경 후 1회 이체한도 |
| `before_daily_limit` | `BIGINT` |  | X |  | 변경 전 1일 이체한도 |
| `after_daily_limit` | `BIGINT` |  | X |  | 변경 후 1일 이체한도 |
| `created_at` | `DATETIME(6)` |  | X |  | 변경이 일어난 시각 |
| `updated_at` | `DATETIME(6)` |  | X |  | 행 최종 수정 일시. append-only라 `created_at`과 같은 값이 유지된다 |

**제약·인덱스를 두지 않은 이유**

`CHECK` 제약은 두지 않는다 — 적재되는 값은 `transfer_limit`의 `ck_tl_positive`·`ck_tl_order`를 이미 통과한 데이터의 복사본이다. 별도 인덱스도 두지 않는다 — 이력 조회 요구사항이 아직 없고, FK가 `customer_id` 인덱스를 자동 생성한다. 조회 API가 생기면 그때 추가한다.

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
| `active_dup_key` | `VARCHAR(80)` *GEN* | **UK** | O |  | **생성 컬럼.** `WAITING`일 때만 값이 생겨 중복 예약을 막는다 (SCD0301) |

**인덱스**

| 종류 | 이름 | 컬럼 |
| --- | --- | --- |
| UNIQUE | `uk_sched_active_dup` | `active_dup_key` |
| INDEX | `ix_sched_batch` | `status, scheduled_date` |

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
| `customer_id` | `BIGINT` | **FK** | X |  | 요청 고객 → `customer.customer_id` |
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
| `requested_at` | `DATETIME(6)` |  | X |  | 요청 일시 |

**인덱스**

| 종류 | 이름 | 컬럼 |
| --- | --- | --- |
| INDEX | `ix_audit_customer` | `customer_id, requested_at DESC` |
| INDEX | `ix_audit_txno` | `transaction_number` |

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
