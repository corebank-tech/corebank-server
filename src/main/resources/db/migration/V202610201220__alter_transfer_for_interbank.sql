-- ====================================================================
-- V202610201220__alter_transfer_for_interbank.sql
-- P4 (PH-33-② / #654)
--
-- transfer에 타행 거래를 담는다(fixed_length_message_spec.md §6).
--   - 타행 수취 계좌는 당행 account에 없다 → deposit_account_id NULL 허용
--   - 은행마다 자릿수가 다르다 → deposit_account_number를 전문 계좌번호 칸(16바이트)과 맞춘다
--   - 예금주는 상대 은행 응답(SUCCESS)으로만 받는다 → ERROR·TIMEOUT 타행 건은 payee_name이 없다
--   - payee_bank_code: NULL이면 당행 거래다
--
-- 당행 행의 규칙은 CHECK로 그대로 지킨다. 은행코드가 없으면 입금계좌 ID와 예금주가 있어야 한다.
--
-- CHAR → VARCHAR 변경은 테이블을 다시 쓴다(ALGORITHM=COPY). 그동안 transfer 쓰기가 막히므로
-- 재작성이 한 번만 일어나도록 변경을 ALTER 하나로 묶는다.
-- ====================================================================

ALTER TABLE transfer
    MODIFY COLUMN deposit_account_id     BIGINT      NULL COMMENT '당행 입금계좌. 타행 거래는 NULL',
    MODIFY COLUMN deposit_account_number VARCHAR(16) NOT NULL COMMENT '숫자만. 당행 12자리, 타행 1~16자리',
    MODIFY COLUMN payee_name             VARCHAR(50) NULL COMMENT '거래 시점 스냅샷. 타행은 SUCCESS 응답의 예금주, ERROR·TIMEOUT이면 NULL',
    ADD COLUMN payee_bank_code CHAR(3) NULL COMMENT '타행 입금 은행코드(common_code BANK_CODE). NULL이면 당행' AFTER deposit_account_number,
    ADD CONSTRAINT ck_transfer_payee_account CHECK ((payee_bank_code IS NULL) = (deposit_account_id IS NOT NULL)),
    ADD CONSTRAINT ck_transfer_payee_name CHECK (payee_bank_code IS NOT NULL OR payee_name IS NOT NULL);
