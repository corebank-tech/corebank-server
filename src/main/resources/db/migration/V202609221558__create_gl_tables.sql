-- ====================================================================
-- V202609221558__create_gl_tables.sql
-- 회계 원장 — 계정과목 · 전표 · 분개 (P3, PH-20 / #451)

-- 주의: Flyway 적용 후에는 이 파일을 수정하지 마십시오 (체크섬 불변).
--       변경은 새 V 파일에 ALTER 로 작성합니다.
--
-- 계정과목 시드는 여기 넣지 않는다. 마스터성 데이터라 R__seed_gl_account.sql 이
-- 맡는다 (flyway_file_role_guide.md — V__ 에 시드 INSERT 금지).
--
-- Apache Fineract 의 acc_gl_account · acc_gl_journal_entry 를 대조 기준으로 삼되
-- 두 가지는 의도적으로 다르게 간다.
--   1. 금액을 BIGINT 원 단위 정수로 둔다. Fineract 는 DECIMAL(19,6) 이지만
--      AGENTS.md 절대규칙 5 가 "금액은 원 단위 정수, BigDecimal 로 바꾸지 마라"
--      로 못박는다. 원화는 소수 자리가 없는 통화다.
--   2. 전표를 별도 테이블로 뺀다. Fineract 는 transaction_id 를 공유하는 분개
--      줄 묶음이 전표 역할을 하고 차대변 검증을 애플리케이션에서만 한다.
--      우리는 전표 단위 차대변 일치를 DB 제약으로 걸어야 하고(PH-21), 전표번호
--      채번 규칙(영업일+유형+일련)을 둘 자리가 필요하다.
--
-- 인덱스는 FK 가 만드는 것 외에 두지 않는다. 시산표 집계용 복합 인덱스
-- (account_code, trade_date) 는 PH-28 베이스라인(10/8)을 측정한 뒤 S3 에서 붙인다
-- — 지금 넣으면 "개선 전" 수치가 사라진다.
-- ====================================================================

-- --------------------------------------------------------------------
-- 1. 계정과목
-- --------------------------------------------------------------------
CREATE TABLE gl_account (
    account_code   CHAR(5)     NOT NULL COMMENT '대분류1 + 중분류2 + 세분류2. 첫 자리가 분류',
    account_name   VARCHAR(50) NOT NULL,
    account_class  VARCHAR(12) NOT NULL COMMENT 'ASSET / LIABILITY / EQUITY / REVENUE / EXPENSE',
    normal_balance VARCHAR(6)  NOT NULL COMMENT '정상잔액 방향. DEBIT / CREDIT',
    created_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (account_code),
    -- 코드 체계(1~5 로 시작하는 5자리)를 강제한다 — account.ck_account_number 와 같은 방식.
    CONSTRAINT ck_gl_account_code   CHECK (account_code REGEXP '^[1-5][0-9]{4}$'),
    -- 코드 첫 자리 · 분류 · 정상잔액 셋의 **조합**을 강제한다. 열마다 따로 제한하면
    -- 10100(자산 코드) + LIABILITY 나 ASSET + CREDIT 같은 조합이 통과한다.
    -- 이 CHECK 가 위 두 열의 허용값 검사까지 포함하므로 단일 열 CHECK 는 두지 않는다.
    -- 대손충당금 같은 contra 계정(자산인데 정상잔액 대변)은 2차에 없다. 필요해지면
    -- tx_type 과 마찬가지로 새 V 파일에서 넓힌다.
    CONSTRAINT ck_gl_account_class_code CHECK (
        (account_class = 'ASSET'     AND account_code LIKE '1%' AND normal_balance = 'DEBIT')
     OR (account_class = 'LIABILITY' AND account_code LIKE '2%' AND normal_balance = 'CREDIT')
     OR (account_class = 'EQUITY'    AND account_code LIKE '3%' AND normal_balance = 'CREDIT')
     OR (account_class = 'REVENUE'   AND account_code LIKE '4%' AND normal_balance = 'CREDIT')
     OR (account_class = 'EXPENSE'   AND account_code LIKE '5%' AND normal_balance = 'DEBIT')
    )
) ENGINE=InnoDB COMMENT='계정과목 (PH-20)';

-- --------------------------------------------------------------------
-- 2. 전표 — 분개를 담는 단위
-- --------------------------------------------------------------------
-- updated_at 을 두지 않는다. 전표는 수정하지 않는다 — 틀리면 지우거나 고치지 않고
-- 정정 전표를 새로 세운다(용어표 "정정 체인"). 수정 시각 칸이 있으면 고쳐도 되는
-- 것처럼 읽힌다.
CREATE TABLE gl_voucher (
    voucher_no  VARCHAR(20)  NOT NULL COMMENT '영업일 + 유형 + 일련. 채번 규칙은 PH-21',
    trade_date  DATE         NOT NULL COMMENT '이 전표가 귀속되는 영업일',
    tx_type     VARCHAR(24)  NOT NULL COMMENT 'OPENING / TRANSFER / PRODUCT_SUBSCRIPTION / INTEREST',
    description VARCHAR(200) NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (voucher_no),
    -- 분개가 복제해 가는 trade_date 를 복합 FK 로 묶기 위한 참조 대상이다.
    -- voucher_no 가 이미 PK 라 이 UNIQUE 는 행을 더 제한하지 않는다 — FK 가 참조할
    -- 키를 만들어 주는 것이 목적이다.
    UNIQUE KEY uk_gl_voucher_no_trade_date (voucher_no, trade_date),
    -- 타행 미결제 유형은 패턴이 확정되면(P4 PH-33) 새 V 파일에서 넓힌다.
    CONSTRAINT ck_gl_voucher_tx_type CHECK (tx_type IN ('OPENING', 'TRANSFER', 'PRODUCT_SUBSCRIPTION', 'INTEREST'))
) ENGINE=InnoDB COMMENT='전표 — 분개를 담는 단위 (PH-20)';

-- --------------------------------------------------------------------
-- 3. 분개 — 전표 안의 한 줄
-- --------------------------------------------------------------------
CREATE TABLE gl_journal_entry (
    journal_entry_id BIGINT      NOT NULL AUTO_INCREMENT,
    voucher_no       VARCHAR(20) NOT NULL,
    line_no          SMALLINT    NOT NULL COMMENT '전표 안의 줄 번호. 1부터',
    account_code     CHAR(5)     NOT NULL,
    dr_cr            VARCHAR(6)  NOT NULL COMMENT '차변/대변. Fineract acc_gl_journal_entry.type_enum 대응',
    amount           BIGINT      NOT NULL COMMENT '원 단위 정수. 한 줄은 한쪽 금액만 가지므로 항상 양수',
    -- 전표에서 복제한다. 600만 줄을 기간으로 거를 때 전표 조인을 없앤다.
    -- Fineract 도 acc_gl_journal_entry.entry_date 를 같은 자리에 둔다.
    trade_date       DATE        NOT NULL COMMENT '전표의 trade_date 복제본. 시산표 집계 기준일',
    created_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (journal_entry_id),
    UNIQUE KEY uk_gl_journal_entry_line (voucher_no, line_no),
    -- **복합 FK 다.** voucher_no 만 참조하면 전표와 다른 trade_date 를 가진 분개가
    -- 저장되고, 그대로 날짜별 시산표 집계가 틀어진다. 복제본이 원본과 같도록 DB 가
    -- 강제한다 — 특히 P6 시드 생성기(PH-60b)는 애플리케이션을 거치지 않고 600만 건을
    -- 직접 INSERT 하므로, 애플리케이션 규약으로는 막을 수 없다.
    CONSTRAINT fk_gl_journal_entry_voucher FOREIGN KEY (voucher_no, trade_date)
        REFERENCES gl_voucher (voucher_no, trade_date),
    CONSTRAINT fk_gl_journal_entry_account FOREIGN KEY (account_code) REFERENCES gl_account (account_code),
    CONSTRAINT ck_gl_journal_entry_dr_cr   CHECK (dr_cr IN ('DEBIT', 'CREDIT')),
    -- 0 원 분개와 음수 금액을 막는다. 금액의 방향은 dr_cr 이 말한다.
    CONSTRAINT ck_gl_journal_entry_amount  CHECK (amount > 0),
    -- 줄 번호는 1부터다. NOT NULL·UNIQUE 만으로는 0 과 음수가 통과한다.
    CONSTRAINT ck_gl_journal_entry_line_no CHECK (line_no > 0)
) ENGINE=InnoDB COMMENT='분개 — 전표 안의 한 줄 (PH-20)';
