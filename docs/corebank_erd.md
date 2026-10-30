# CoreBank 미니 코어뱅킹 — DB ERD v3.0

> **DBMS**: MySQL 8.4 / 49개 테이블(40개 비즈니스 테이블 + 채번·배치·cutoff·체크포인트·감사 적재 상태 6개 + Spring Session 2개 + Spring Modulith 발행 기록 `EVENT_PUBLICATION` 1개), [스키마 목록](schema_reference.md#테이블-목록) 기준 413개 컬럼 / 금액 BIGINT · 업무 시각 DATETIME(6)(시간대 없는 벽시각, KST는 애플리케이션 저장·표시 계약). Spring Session 시각은 프레임워크 표준인 UTC epoch 밀리초 BIGINT, `EVENT_PUBLICATION` 시각은 라이브러리 DDL 그대로 TIMESTAMP(6).
> **스키마 권한**: Flyway 단독 (`spring.jpa.hibernate.ddl-auto: validate`)

---

```mermaid
erDiagram
    SPRING_SESSION {
        char PRIMARY_ID PK "CHAR(36)"
        char SESSION_ID UK "CHAR(36)"
        bigint CREATION_TIME "epoch ms"
        bigint LAST_ACCESS_TIME "epoch ms"
        int MAX_INACTIVE_INTERVAL "seconds"
        bigint EXPIRY_TIME "epoch ms, INDEX"
        varchar PRINCIPAL_NAME "VARCHAR(100), 고객 PK 문자열, INDEX"
    }
    SPRING_SESSION_ATTRIBUTES {
        char SESSION_PRIMARY_ID PK,FK "CHAR(36)"
        varchar ATTRIBUTE_NAME PK "VARCHAR(200)"
        blob ATTRIBUTE_BYTES "직렬화 속성"
    }
    SPRING_SESSION ||--o{ SPRING_SESSION_ATTRIBUTES : "ON DELETE CASCADE"
    %% =================================================================
    %% CoreBank 미니 코어뱅킹 - DB ERD v1.0
    %% MySQL 8.4 / 49개 테이블 / 금액 BIGINT · 시각 DATETIME(6)(시간대 없는 벽시각, KST는 애플리케이션 계약)
    %% =================================================================

    %% ---------- P6 ----------
    customer {
        bigint customer_id PK
        varchar user_id UK "로그인 아이디"
        varchar existing_bank_customer_id UK "원장 고객 식별자, NULL 허용 (ATH0303)"
        char password_hash "BCrypt"
        varchar user_name "VARCHAR(50)"
        date birth_date
        varchar email UK "VARCHAR(100)"
        varchar phone_number "하이픈 없음"
        tinyint login_failure_count "5회 시 잠금 (ATH0102)"
        boolean account_locked
        varchar status "ACTIVE / SUSPENDED"
        varchar role "CUSTOMER / ADMIN"
        datetime last_login_at "대시보드 currentLoginAt"
        varchar last_login_ip "대시보드 currentLoginIp"
        datetime previous_login_at "대시보드 previousLoginAt"
        datetime password_changed_at "DATETIME(6)"
        datetime joined_at "DATETIME(6)"
        varchar email_ciphertext "AES-256-GCM, NULL 허용"
        varchar phone_number_ciphertext "AES-256-GCM, NULL 허용"
        char email_search_hash UK "CHAR(64), HMAC-SHA256, NULL 허용"
    }

    terms {
        bigint terms_id PK
        varchar terms_code UK "VARCHAR(30)"
        varchar version UK "VARCHAR(10)"
        varchar terms_type "SIGNUP / PRODUCT"
        varchar title "VARCHAR(200)"
        text content
        boolean is_required
        boolean view_required "전문 열람 강제 (PRD0005)"
    }
    customer_terms_agreement {
        bigint agreement_id PK
        bigint customer_id FK
        bigint terms_id FK
        datetime agreed_at "DATETIME(6)"
    }
    verification_request {
        varchar verification_request_id PK "EVF_ / PRR_ / OTP_REQ_ 접두어 + CSPRNG 난수"
        varchar purpose "SIGN_UP / EMAIL_CHANGE / PASSWORD_RESET / OTP_TRANSACTION"
        bigint customer_id FK "회원가입 전에는 NULL"
        varchar target "이메일 주소 등 인증 대상"
        char code_hash "인증번호·OTP 단방향 해시 (REQ-NFR-009)"
        varchar transaction_type "OTP 전용. IMMEDIATE_TRANSFER 등"
        json transaction_data "OTP 전용. 거래 내용 변조 검증"
        tinyint error_count "5회 오입력 시 OTP0103"
        boolean locked
        boolean used
        datetime verified_at "DATETIME(6)"
        datetime expires_at "발급 + 180초"
    }

    %% ---------- P2 ----------
    account {
        bigint account_id PK
        char account_number UK "하이픈 없는 숫자 12자리"
        bigint customer_id FK
        bigint product_id FK "입출금계좌는 NULL"
        varchar account_type "DEMAND_DEPOSIT / TIME_DEPOSIT / INSTALLMENT_SAVINGS"
        bigint balance "원장 대사 대상"
        bigint hold_amount "지급정지 보류액"
        varchar status "ACTIVE / SUSPENDED / MATURED / CLOSED / DORMANT"
        char password_hash "계좌비밀번호 BCrypt"
        tinyint password_failure_count
        boolean password_locked "APW0101"
        varchar alias "계좌별명. 한글 12자·영숫자 24자 (ACC0001)"
        int display_order "사용자 지정 표시순서"
        boolean withdrawal_registered "응답 withdrawalAccountRegistered"
        datetime withdrawal_registered_at "DATETIME(6)"
        datetime opened_date
        date maturity_date
        datetime closed_date
        date closed_trade_date "해지 논리 영업일 (정보계 CLOSE 기준)"
        datetime last_transaction_at "DATETIME(6)"
        bigint version "낙관적 락"
    }

    account_status_history {
          bigint history_id PK "AUTO_INCREMENT"
          bigint account_id "FK 없음. 계좌 정리 후에도 이력 보존"
          varchar previous_status "VARCHAR(12)"
          varchar new_status "VARCHAR(12)"
          date maturity_date "NULL 허용. 계약 만기일"
          date effective_business_date "NULL 허용. 상태 전이 기준 영업일"
          datetime changed_at "DATETIME(6)"
          varchar trigger_type "VARCHAR(20). COB"
    }

    interest_payment {
        bigint interest_payment_id PK "AUTO_INCREMENT"
        bigint account_id UK "계좌당 이자 지급 최대 1회, 물리적 FK 없음"
        varchar payment_reason "MATURITY / TERMINATION"
        bigint gross_amount "세전이자"
        bigint tax_amount "원천징수 세금"
        bigint net_amount "세후이자"
        char transaction_number UK "CHAR(20), NULL 허용"
        date trade_date "원장 거래일, NULL 허용"
        datetime paid_at "DATETIME(6), KST"
    }

    account_number_sequence {
        bigint sequence_id PK
        char bank_code "계좌번호 앞 3자리 은행코드"
        varchar account_type "DEMAND_DEPOSIT / TIME_DEPOSIT / INSTALLMENT_SAVINGS"
        bigint product_id FK "입출금계좌는 NULL"
        char product_prefix "상품별 Prefix 2자리"
        bigint last_sequence "마지막 발급 7자리 일련번호"
        datetime created_at "DATETIME(6)"
        datetime updated_at "DATETIME(6)"
    }

    %% ---------- P4 ----------
    transaction_sequence {
        date seq_date PK "KST 기준 영업일"
        char channel PK "WB / BT"
        bigint last_seq "해당 일자·채널의 마지막 일련번호"
    }
    transfer {
        bigint transfer_id PK
        char transaction_number UK "YYYYMMDD + 채널2 + 일련10"
        bigint withdrawal_account_id FK
        bigint deposit_account_id FK "원장 입금행의 근거. 타행 거래는 NULL"
        varchar deposit_account_number "VARCHAR(16). 당행 12자리, 타행 1~16자리"
        char payee_bank_code "타행 은행코드. NULL이면 당행"
        varchar payee_name "거래 시점 스냅샷. 타행 ERROR·TIMEOUT은 NULL"
        bigint amount
        bigint fee "당행 0 고정 (POL-028)"
        varchar transfer_type "IMMEDIATE / SCHEDULED / AUTO"
        char channel "WB / BT"
        varchar status "SUCCESS / ERROR / TIMEOUT (PROCESSING은 커밋되지 않음, #377)"
        varchar source_type "SCHEDULED / AUTO"
        bigint source_id
        date execution_date "source_id와 함께 멱등키. SCHEDULED/AUTO 전용"
        varchar my_passbook_memo "TRF0005 최대 10자"
        varchar recipient_passbook_memo "VARCHAR(10)"
        bigint withdrawal_balance_after
        varchar error_code "VARCHAR(10)"
        varchar error_message "VARCHAR(200)"
        datetime transferred_at "DATETIME(6)"
        date trade_date "거래일(귀속 영업일). NULL 허용"
        datetime invalidated_at "정정 체인 무효화 시각. NULL이면 유효"
        bigint ref_transfer_id FK "정정 체인 원거래"
        varchar correction_type "REVERSAL / REPOST"
    }
    ledger_entry {
        bigint ledger_entry_id PK
        bigint account_id
        bigint transfer_id "이체 외 기표(상품가입 초입금 등)는 NULL"
        char transaction_number "짝이 되는 2행이 동일"
        varchar direction "DEPOSIT / WITHDRAWAL"
        bigint amount "양수만. 방향은 direction 이 표현"
        bigint balance_after "기표 직후 잔액"
        varchar transaction_type "OPENING / IMMEDIATE_TRANSFER / SCHEDULED_TRANSFER / AUTO_TRANSFER / PRODUCT_SUBSCRIPTION / INTEREST / MATURITY / TERMINATION / REVERSAL / INTERBANK_TRANSFER"
        varchar transaction_content "통장 표시내용"
        char channel "WB / BT"
        boolean reversed
        bigint reversal_id "반대기표가 가리키는 원거래"
        datetime occurred_at PK "RANGE PARTITION KEY"
        date trade_date "거래일(귀속 영업일). NULL 허용"
    }
    favorite_account {
        bigint favorite_account_id PK
        bigint customer_id FK
        char deposit_account_number UK "CHAR(12)"
        varchar payee_name "등록 시점 예금주 스냅샷"
        varchar alias "미지정 시 예금주명 (FAV0001)"
        datetime registered_at "DATETIME(6)"
    }
    ledger_entry_id_sequence {
        bigint sequence_id PK "AUTO_INCREMENT (ledger_entry.ledger_entry_id 전용 채번 카운터)"
    }
    ledger_reconciliation_mismatch {
        bigint ledger_reconciliation_mismatch_id PK
        date business_date UK "COB_DATE (마감 대상 영업일)"
        bigint account_id UK "FK 없음 (감사용 기록)"
        bigint ledger_balance "원장 누적 합계"
        bigint account_balance "대사 시점 account.balance"
        datetime detected_at "DATETIME(6)"
    }

    %% ---------- P1 ----------
    admin_permission {
        bigint customer_id PK,FK "관리자"
        varchar permission PK "GL_READ / GL_WRITE / CUSTOMER_READ / CUSTOMER_WRITE / AUDIT_READ"
        datetime granted_at "권한 부여 시각"
    }
    notification {
        bigint notification_id PK
        bigint customer_id FK
        varchar type UK "TRANSFER / SCHEDULED_TRANSFER / AUTO_TRANSFER / PRODUCT_SUBSCRIPTION"
        bigint ref_id UK "uk_noti_type_ref(type, ref_id). 다형 참조: TRANSFER->transfer.transfer_id / SCHEDULED_TRANSFER->scheduled_transfer_id / AUTO_TRANSFER->auto_transfer_execution.execution_id / PRODUCT_SUBSCRIPTION->subscription_id"
        varchar title "VARCHAR(100)"
        varchar content "VARCHAR(500)"
        boolean is_read
        datetime read_at "DATETIME(6)"
        datetime occurred_at "DATETIME(6)"
    }
    transfer_limit {
        bigint customer_id PK
        bigint one_time_limit
        bigint daily_limit
    }
    transfer_limit_daily_usage {
        bigint customer_id PK
        date usage_date PK "KST 기준 영업일"
        bigint used_amount "SUCCESS 확정 건만 합산"
    }
    transfer_limit_history {
        bigint history_id PK "AUTO_INCREMENT"
        bigint customer_id FK
        bigint before_one_time_limit "변경 직전 1회 한도"
        bigint before_daily_limit "변경 직전 1일 한도"
    }

    %% ---------- P3 ----------
    product {
        bigint product_id PK
        varchar product_code UK "VARCHAR(20)"
        varchar product_name "VARCHAR(100)"
        varchar product_group "SAVINGS(정기적금) / DEPOSIT(정기예금)"
        varchar deposit_type "거치식 / 적립식"
        varchar summary "VARCHAR(200)"
        text description
        decimal base_rate "DECIMAL(5,2)"
        decimal max_rate "DECIMAL(5,2)"
        bigint min_amount
        bigint max_amount
        bigint amount_unit "배수 검증 (PRD0004)"
        smallint min_term_months
        smallint max_term_months
        varchar interest_pay_type "단리 / 복리"
        varchar sale_status "ON_SALE / SUSPENDED"
        date sale_start_date
        date sale_end_date
        boolean new_flag
        boolean single_account_limit "1인 1계좌 제한 상품 (PRD0301)"
    }
    product_rate_tier {
        bigint product_id PK
        smallint term_months PK
        decimal rate "DECIMAL(5,2)"
    }
    product_preferential_rate {
        bigint product_id PK
        varchar condition_code PK "VARCHAR(30)"
        varchar condition_name "VARCHAR(100)"
        decimal rate "DECIMAL(5,2)"
    }
    product_terms {
        bigint product_id PK
        bigint terms_id PK
    }
    product_subscription {
        bigint subscription_id PK
        bigint customer_id FK
        bigint product_id FK
        bigint account_id FK "가입으로 개설된 계좌"
        bigint withdrawal_account_id FK "초입금 출금계좌"
        bigint subscription_amount
        smallint term_months
        tinyint payment_day "1~28, 적립식만"
        decimal base_rate "DECIMAL(5,2)"
        decimal preferential_rate "DECIMAL(5,2)"
        decimal applied_rate "DECIMAL(5,2)"
        varchar maturity_handling "TRANSFER / RENEW"
        bigint expected_maturity_amount
        varchar status "SUCCESS / ERROR / PROCESSING"
        char transaction_number "초입금 거래번호"
        date opened_date
        date maturity_date
        datetime subscribed_at "DATETIME(6)"
        date subscription_trade_date "가입 논리 영업일 (정보계 SUBSCRIBE 기준)"
    }
    subscription_terms_agreement {
        bigint subscription_id PK
        bigint terms_id PK
        varchar terms_version "동의 시점 버전 (PRD0006)"
        datetime read_at "전문 열람 시각 (PRD0005)"
        datetime agreed_at "DATETIME(6)"
    }
    scheduled_transfer {
        bigint scheduled_transfer_id PK
        bigint customer_id FK
        bigint withdrawal_account_id FK
        char payee_bank_code "당행 상수. bank 테이블 없음"
        char payee_account_number "CHAR(12)"
        varchar payee_name "VARCHAR(50)"
        bigint amount
        date scheduled_date "익일~1년 이내 (SCD0001)"
        varchar my_passbook_memo "VARCHAR(10)"
        varchar recipient_passbook_memo "VARCHAR(10)"
        varchar status "WAITING / PROCESSING / SUCCESS / FAILED / CANCELED"
        char transaction_number "CHAR(20)"
        datetime registered_at "DATETIME(6)"
        datetime executed_at "DATETIME(6)"
        datetime canceled_at "DATETIME(6)"
        varchar failure_reason "VARCHAR(200)"
        bigint version "낙관적 락"
        varchar active_dup_key UK "VARCHAR(80)"
    }

    %% ---------- P5 ----------
    auto_transfer {
        bigint auto_transfer_id PK
        bigint customer_id FK
        bigint withdrawal_account_id FK
        char deposit_account_number "CHAR(12)"
        varchar payee_name "VARCHAR(50)"
        bigint amount
        tinyint cycle_months "1 / 3 / 6"
        tinyint transfer_day "1~31 (AUT0001)"
        date start_date
        date end_date "60개월 이내 (AUT0002)"
        date next_execution_date
        varchar my_passbook_memo "VARCHAR(10)"
        varchar recipient_passbook_memo "VARCHAR(10)"
        varchar status "NORMAL / EXPIRED / TERMINATED (AutoTransferStatus)"
        datetime registered_at "DATETIME(6)"
        datetime terminated_at "DATETIME(6)"
        varchar active_dup_key UK "VARCHAR(64)"
    }
    auto_transfer_execution {
        bigint execution_id PK
        bigint auto_transfer_id FK
        date execution_date UK
        bigint amount
        varchar status "SUCCESS / ERROR / PROCESSING (ProcessResultStatus)"
        char transaction_number "CHAR(20)"
        varchar failure_reason "VARCHAR(200)"
        datetime executed_at "DATETIME(6)"
    }
    idempotency_key {
        char idempotency_key PK "UUID v4"
        bigint customer_id FK "회원가입 완료 사전 예약은 NULL"
        varchar endpoint "VARCHAR(120)"
        char request_hash "SHA-256. *AuthToken 필드 제외 후 계산"
        varchar state "PROCESSING / COMPLETED"
        smallint http_status
        json response_snapshot
        datetime expires_at "24시간 후 배치 삭제"
    }
    audit_log {
        bigint audit_log_id PK
        bigint customer_id
        char transaction_number "CHAR(20)"
        varchar event_type "VARCHAR(40)"
        varchar request_ip "VARCHAR(45)"
        varchar result "SUCCESS / FAILURE"
        json detail "민감정보 마스킹 후 저장"
        varchar access_reason "관리자 조회·변경 사유(PH-89). NULL 허용"
        char row_hash "무결성 해시 SHA-256(PH-82). 도입 전 NULL"
        datetime requested_at "DATETIME(6)"
    }
    audit_archive_execution {
        bigint audit_archive_execution_id PK
        date archive_date UK "적재 대상 날짜. (archive_date, archive_type) UNIQUE"
        varchar archive_type UK "VARCHAR(10). LOG / ACCESS"
        varchar status "VARCHAR(10). PENDING / SUCCESS / FAILURE"
        varchar object_key "VARCHAR(100). 저장소 객체 키"
        int row_count "성공 때 적재 행 수. NULL 허용"
        varchar failure_reason "VARCHAR(500). 마지막 실패 사유"
        datetime completed_at "DATETIME(6). 성공 시각(KST)"
        datetime updated_at "DATETIME(6). 마지막 상태 변경(KST)"
    }
    audit_integrity_mismatch {
        bigint audit_integrity_mismatch_id PK
        date archive_date UK "검증한 사본의 날짜"
        bigint audit_log_id UK "FK 없음 (지워진 행도 기록)"
        varchar mismatch_type UK "TAMPERED / MISSING_IN_DB / NOT_ARCHIVED"
        datetime detected_at "DATETIME(6)"
    }
    common_code {
        varchar code_group PK "예: ACCOUNT_STATUS"
        varchar code PK "서버 Enum 값과 일치. 예: ACTIVE"
        varchar code_name "화면 표시 한글명"
        int sort_order
        char use_yn "Y / N"
        varchar description "VARCHAR(200)"
        datetime created_at "DATETIME(6)"
        datetime updated_at "DATETIME(6)"
    }
    batch_execution_lock {
        varchar job_name PK "VARCHAR(50). 예: DAILY_TRANSFER_BATCH"
        boolean currently_running "배치 중복 트리거 방지 락 (#189)"
        datetime updated_at "DATETIME(6). stale 판단 기준"
    }
    batch_execution_history {
        bigint batch_execution_history_id PK
        varchar job_name "예: COB"
        varchar step_name "CobStep.name()"
        date business_date "COB_DATE"
        datetime started_at "DATETIME(6)"
        datetime ended_at "DATETIME(6)"
        varchar status "SUCCESS / FAILURE / HALTED (#600, #603)"
        text failure_reason "실패·중단 시만. SUCCESS면 NULL"
    }
    EVENT_PUBLICATION {
        varchar ID PK "VARCHAR(36). Spring Modulith 이벤트 발행 기록 (#437)"
        varchar LISTENER_ID "VARCHAR(512)"
        varchar EVENT_TYPE "VARCHAR(512)"
        varchar SERIALIZED_EVENT "VARCHAR(4000). JSON"
        timestamp PUBLICATION_DATE "TIMESTAMP(6). 라이브러리 DDL 그대로"
        timestamp COMPLETION_DATE "delete 모드라 완료 시 행 삭제"
        varchar STATUS "PUBLISHED / PROCESSING / COMPLETED / FAILED / RESUBMITTED"
        int COMPLETION_ATTEMPTS
        timestamp LAST_RESUBMISSION_DATE
    }
    auth_token {
        bigint auth_token_id PK
        varchar purpose UK "TERMS_AUTH / ... / ACCOUNT_PASSWORD_AUTH (#580)"
        char token_hash UK "SHA-256. 원문 미저장"
        bigint customer_id "로그인 고객 토큰만. FK 없음"
        text payload "JSON 문자열. utf8mb4_bin"
        char claim_id "임시가입 토큰 선점 ID"
        datetime created_at "DATETIME(6)"
        datetime expires_at "유효성 판정 기준"
        datetime consumed_at "NULL 이면 미소비"
    }
    otp_issue_lock {
        bigint customer_id PK "FK 없음 (#580)"
        char owner_id "UUID. 주인만 해제"
        datetime expires_at "지나면 재획득 가능"
    }
    terms_view_history {
        bigint customer_id PK "FK 없음 (#580)"
        bigint terms_id PK
        datetime viewed_at "DATETIME(6)"
        datetime expires_at "열람 후 30분. 지나면 정리 배치가 삭제"
    }

    %% ---------- P3 (회계 원장, PH-20) ----------
    gl_account {
        char account_code PK "CHAR(5). 대1+중2+세2. 첫 자리가 분류"
        varchar account_name "VARCHAR(50)"
        varchar account_class "ASSET / LIABILITY / EQUITY / REVENUE / EXPENSE"
        varchar normal_balance "DEBIT / CREDIT. 정상잔액 방향"
        datetime created_at "DATETIME(6)"
        datetime updated_at "DATETIME(6)"
    }
    gl_voucher {
        varchar voucher_no PK "VARCHAR(20). yyyyMMdd-TTT-NNNNNN. gl_voucher_sequence 가 채번"
        date trade_date "귀속 영업일"
        varchar tx_type "OPENING / TRANSFER / PRODUCT_SUBSCRIPTION / INTEREST / REVERSAL / INTERBANK_TRANSFER / INTERBANK_SETTLEMENT / MATURITY / TERMINATION"
        varchar reference_key UK "VARCHAR(40). 원 거래번호. (tx_type, reference_key) 유일"
        varchar description "VARCHAR(200). 적요"
        datetime created_at "DATETIME(6). 전표는 수정하지 않아 updated_at 없음"
    }
    gl_journal_entry {
        bigint journal_entry_id PK "AUTO_INCREMENT"
        varchar voucher_no FK "VARCHAR(20)"
        smallint line_no "전표 안의 줄 번호. 1부터"
        char account_code FK "CHAR(5)"
        varchar dr_cr "DEBIT / CREDIT. Fineract type_enum 대응"
        bigint amount "원 단위 정수. 항상 양수"
        date trade_date "전표에서 복제. 시산표 집계 기준일. INDEX(trade_date, account_code, dr_cr, amount)"
        datetime created_at "DATETIME(6)"
    }
    gl_voucher_sequence {
        date trade_date PK "전표의 거래일"
        varchar tx_type PK "gl_voucher.tx_type 과 같은 값"
        int last_seq "마지막 일련번호. 1~999999"
        datetime updated_at "DATETIME(6)"
    }

    %% ---------- P5 (영업일, PH-40) ----------
    %% 다른 테이블과 FK 없음. BusinessDateProvider 가 읽는다.
    business_date {
        varchar date_type PK "VARCHAR(20). 2차는 BUSINESS_DATE 1행"
        date business_date "현재 영업일. 기동 시 오늘로 맞춤"
        datetime created_at "DATETIME(6)"
        datetime updated_at "DATETIME(6). 마지막 전환 시각"
    }
    business_date_cutoff {
        bigint business_date_cutoff_id PK
        date base_date UK "마감된 영업일. (base_date, status) UNIQUE"
        varchar status UK "VARCHAR(10). CLOSED"
        datetime closed_at "DATETIME(6). 마감 시각(KST)"
    }
    cob_checkpoint {
        bigint cob_checkpoint_id PK
        date cob_date UK "마감 대상 영업일(COB_DATE). (cob_date, step_name) UNIQUE"
        varchar step_name UK "CobStep.name(). 영업일 전환은 business-date-advance"
        varchar status "VARCHAR(10). PENDING / SUCCESS"
        datetime updated_at "DATETIME(6). 마지막 상태 변경(KST)"
    }
    holiday {
        date holiday_date PK "주말 제외 공휴일만. 주말은 요일로 판정"
        varchar holiday_name "VARCHAR(50)"
        datetime created_at "DATETIME(6)"
        datetime updated_at "DATETIME(6)"
    }

    %% ---------- 관계 ----------
    %% 회계 원장 (PH-20). 이체·상품가입·이자와의 연결은 기표 훅(PH-24)이 생긴 뒤 표기한다.
    %% 이 블록을 관계 목록 끝이 아니라 앞에 둔 것은 #463(원장-잔액 대사)이 파일 끝을
    %% 건드려서다 — 같은 자리를 고치면 머지 순서에 따라 충돌한다.
    gl_voucher ||--o{ gl_journal_entry : "전표-분개 (전표 단위 차대변 일치)"
    gl_account ||--o{ gl_journal_entry : "계정별 분개"

    customer ||--o{ admin_permission : "권한보유"
    customer ||--o{ customer_terms_agreement : "동의"
    terms ||--o{ customer_terms_agreement : "대상"
    customer ||--o{ verification_request : "인증요청"
    customer ||--o{ notification : "수신"
    product ||--o{ product_rate_tier : "기간별금리"
    product ||--o{ product_preferential_rate : "우대금리"
    product ||--o{ product_terms : "약관연결"
    terms ||--o{ product_terms : "약관연결"
    customer ||--o{ account : "보유"
    product ||--o{ account : "상품계좌"
    account_number_sequence ||..o{ account : "계좌번호 채번"
    product ||--o{ account_number_sequence : "예·적금 채번 규칙"
    account ||--o{ transfer : "출금"
    customer ||--o{ favorite_account : "등록"
    customer ||--o| transfer_limit : "한도보유"
    customer ||--o{ transfer_limit_daily_usage : "일별사용"
    customer ||--o{ transfer_limit_history : "한도변경이력"
    customer ||--o{ product_subscription : "가입"
    product ||--o{ product_subscription : "가입대상"
    account ||--o| product_subscription : "개설계좌"
    account ||..o| interest_payment : "이자 지급 이력 (논리 관계, FK 없음)"
    product_subscription ||--o{ subscription_terms_agreement : "약관동의"
    terms ||--o{ subscription_terms_agreement : "약관동의"
    customer ||--o{ scheduled_transfer : "예약"
    account ||--o{ scheduled_transfer : "출금"
    customer ||--o{ auto_transfer : "등록"
    account ||--o{ auto_transfer : "출금"
    auto_transfer ||--o{ auto_transfer_execution : "회차"
    customer o|--o{ idempotency_key : "발급"
    %% 일회성 저장소 (#580). 수명이 짧아 FK 없음
    customer ||..o{ auth_token : "인증 토큰"
    customer ||..o| otp_issue_lock : "OTP 발급 잠금"
    customer ||..o{ terms_view_history : "약관 열람"
    terms ||..o{ terms_view_history : "열람 대상"

    %% 논리 관계 (ledger_entry 는 파티션 테이블이라 FK 선언 불가)
    %% 채번 자원 (FK 없음. 거래번호/원장ID 생성)
    transaction_sequence ||..o{ transfer : "거래번호 채번"
    transaction_sequence ||..o{ ledger_entry : "거래번호 채번"
    ledger_entry_id_sequence ||..o{ ledger_entry : "ledger_entry_id 전용 채번"
    account ||..o{ ledger_reconciliation_mismatch : "대사 불일치 (FK 없음)"

    %% 같은 두 엔티티를 두 번 참조하는 관계 (중복 제거 대상이라 명시)
    account |o--o{ transfer : "입금 계좌 (당행 거래만)"
    account ||--o{ product_subscription : "초입금 출금계좌"

    account  ||--o{ ledger_entry : "계좌별 기표"
    transfer ||--o{ ledger_entry : "성공 시 출금1행+입금1행 / 실패 시 0행"
    transfer |o--o{ transfer : "정정 체인 — 취소정정·정상거래가 원거래를 가리킨다"
    customer ||--o{ audit_log : "행위"
    audit_log ||..o{ audit_integrity_mismatch : "무결성 불일치 (FK 없음)"
    scheduled_transfer      |o--o| transfer : "실행결과 (WAITING 은 없음)"
    auto_transfer_execution |o--o| transfer : "실행결과 (ERROR 는 없음)"
```

## 원장-잔액 대사 (Reconciliation, #378)

`account.balance`는 조회 성능용 캐시이고, 진실의 원천은 `ledger_entry`다. 계좌별로 원장 기표를
`SUM(DEPOSIT) - SUM(WITHDRAWAL)`로 더한 값이 항상 `account.balance`와 같아야 한다.

- **탐지 (#604)**: COB 체인의 대사 스텝(`LedgerReconciliationCobStep`, `order=2`)이 영업일 23:30 COB에서
  돈다. 독립 스케줄(매일 02:00 증분 · 일요일 03:00 전수)은 없앴다. 스텝은 아래 **전수 대사**를 돌린다 —
  증분 대사는 기표 시각 기준 달력 하루만 보므로, 영업일에만 도는 COB 안에서는 23:30~24:00 거래와
  주말·연휴 기표 계좌가 어느 날 대사에도 잡히지 않기 때문이다. 주말·연휴 불일치는 다음 영업일 COB에서
  잡힌다(누락이 아니라 발견 지연). 불일치는 `LEDGER_RECONCILIATION_MISMATCH` 마커로 ERROR 로그에
  남는다(경보 채널 연동은 미정 — 현재는 로그 기반).
- **soft / hard**: 불일치 건수가 임계(`app.ledger-reconciliation.cob-hard-threshold`, 기본 10) 이하면
  soft(`LEDGER_RECONCILIATION_SOFT` WARN), 초과면 hard(`LEDGER_RECONCILIATION_HARD` ERROR)다. hard는
  `CobStepHaltException`으로 마감을 멈춘다. 영업일 전환 뒤에 멈춰도 `cob_checkpoint`가 `COB_DATE`와 남은
  스텝을 기억해서, 다음 COB가 같은 날짜로 대사부터 다시 한다(#600, #663).
  soft/hard는 **마감을 계속할지**의 판단이지 경보 심각도가 아니다 — 원장 불일치는 1건이어도 사고라
  건별 `LEDGER_RECONCILIATION_MISMATCH`는 soft/hard와 무관하게 항상 ERROR다.
- **전수 대사 (#468)**: 원장을 거치지 않고 잔액만 틀어진 휴면 계좌까지 잡도록 **전 계좌**를
  `account_id` 순 청크(기본 1,000개, `app.ledger-reconciliation.full-scan-chunk-size`)로 훑는다.
  원장이 한 줄도 없는 계좌는 원장 합계를 0으로 본다. 청크마다 짧은 읽기 전용 트랜잭션을 열어 같은
  스냅샷에서 잔액과 원장 합계를 읽으므로 COB 중 들어오는 거래나 다른 배치가 돌아도 오탐이 없다.
  COB 대사는 증분 경로(`run(date)`)·`runFull()`과 같은 락(`LEDGER_RECONCILIATION_BATCH`)을 잡는다. 못 잡으면
  `LEDGER_RECONCILIATION_SKIPPED` ERROR를 남기고 그날 대사를 건너뛰므로 수동 재확인이 필요하다. 불일치는 같은 마커에 `scope=FULL`을 붙여 남기고, 끝나면 계좌 수·
  불일치 수·`elapsedMs`를 INFO로 남긴다.
- **증분 대사 (#378, 현재 트리거 없음)**: 전일 기표가 있었던 계좌만 대조하는 경로
  (`LedgerReconciliationBatchUseCase.run(date)`)는 코드에 남아 있지만 부르는 곳이 없다. 거래일
  (`trade_date`, PH-41) 기준으로 바꾸면 COB 안에서도 날짜 구멍 없이 쓸 수 있다.
- **자동 정정 없음**: `ledger_entry`는 파티션 테이블이라 FK로도 강제되지 않는 APPEND-ONLY
  원장이다. 배치는 탐지까지만 하고 잔액을 임의로 덮어쓰지 않는다.
- **불일치 목록 (#613)**: COB 대사가 찾은 불일치 계좌를 `ledger_reconciliation_mismatch`에
  영업일(`COB_DATE`)별로 남기고, 관리자 목록 API가 읽는다. 같은 영업일을 다시 대사하면 그날 행을
  지우고 새 결과로 바꾼다. 재실행 이력은 `batch_execution_history`(#603)가 맡는다.
- **대응 절차**: 불일치가 나오면 원장이 맞고 `account.balance`가 틀린 것으로 간주한다.
  담당자가 원인(동시성 버그, 배포 중 유실 등)을 먼저 파악하고, 필요하면 반대기표(원거래와
  반대 방향의 신규 `ledger_entry` 행)로만 정정한다. 기존 행을 UPDATE/DELETE하지 않는다.
